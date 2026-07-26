# WebSocket Rules (Android)

Binding rules for `data/ChatRealtimeClient.kt`, `data/RealtimeConnectionManager.kt`,
the app-scoped stores (`PresenceStore`, `UnreadMessagesStore`,
`UnreadNotificationsStore`) and the messages UI controllers/screens.
Architecture and rationale are in the `websocket` skill
(`.claude/skills/websocket/`) — read it before changing this area.

These rules exist because this stack has regressed repeatedly, in a small set of
recurring shapes. The checklist at the end is not optional.

## Contract first

- `docs/api/asyncapi.yaml`, `docs/api/messages/websocket.md` and
  `docs/api/messages/chats.md` are **byte-identical mirrors** of the backend's
  copies in `skipper-club-api-go`. Never hand-edit them to match a client change;
  sync them from the backend and verify with `diff`. Mirror drift was the root
  cause of an earlier cross-client contract split.
- The client implements the contract; it does not extend it.
- Cross-client timings and budgets must stay equal to web's
  (`lib/messages/constants.ts`) and iOS's. Changing one is a three-repo change
  plus a docs update.
- Backend-first deploys: the API ships before the clients for any contract
  change. Clients treat a missing `nextCursor` as the end of pagination.

## Never reintroduce

- **Keying catch-up off `Connected`.** The join replay runs after it; use
  `ChatJoined`, the ack for our own `chat:join`.
- **Dropping the conversation's `Connected` rejoin.** `disconnect()` clears
  `joinedChatIds`, so the `onOpen` replay cannot restore the open room after a
  background cycle — the screen's `rejoinChat` is the only thing that does.
- **Arming the join-ack tracker for a frame that was not sent.** `sendFrame`
  no-ops while disconnected; arming there fires a spurious `join_failed` for a
  join that never went out (AN-5). The reconnect replay arms it correctly.
- **An unguarded `scope !== ownerScope` check outside the monitor.** Guard and
  state flip are one atomic step, or a logout race publishes a live socket
  nothing will ever close.
- **Offset paging.** History, catch-up and the chat list page by keyset cursor
  (`before` / `cursor`). Local-prepend compensation is structurally unnecessary
  with a cursor and must not come back.
- **`hasMore` derived from a post-dedupe count.** It comes from `nextCursor`; a
  fully-overlapping page would otherwise stop paging permanently.
- **Comparing ISO timestamps as strings, or ordering by timestamp alone.** Parse
  them and break ties on the UUIDv7 id (`sortedByCreationOrder`,
  `isNewerTimestamp`).
- **`String.take` on composer text.** Use `truncateToCodePoints` — the server
  counts runes and a split surrogate pair is rejected.
- **Sending both a WS `message:read` and the REST bulk mark-read** for the same
  read. REST already broadcasts the receipt; the socket frame is only for the
  dispose-time flush that must precede `chat:leave`.
- **Showing `Rate limit exceeded` to the user.** It is backpressure; keep it in
  the log (`shouldSurfaceRealtimeError`).
- **Blocking in a socket callback.** They run off-main; emission is always
  `tryEmit`.

## Client invariants

- One app-scoped socket, owned by `RealtimeConnectionManager` (foreground ∧
  authenticated). UI never connects or disconnects it.
- Anything that must update outside the Messages tab (badges, presence) is an
  app-scoped singleton started in `SkipperClubApplication` — a tab-scoped
  controller cannot be the source.
- `connect()` is idempotent (guards on an existing scope) and resets the auth
  breaker and the give-up state; a socket that actually opens resets them too.
- The auth breaker cap is 3, and the give-up is **state** (`connectionGaveUp`) so
  it survives tab switches and late consumers, not only an event on a replay-0
  flow.
- Backoff is bounded exponential with jitter, interruptible via
  `onNetworkAvailable`; a persistent 403 uses the long tier, a 401 forces a token
  refresh, everything else stays on the fast tier.
- `JoinAckTracker` retries a join 3 times, 10s apart, then emits `join_failed`;
  the chat stays in `joinedChatIds` so the next reconnect replay is the backstop.
- `markDisconnected(force = false)` on a failed reconnect **attempt** — only a
  real connected→disconnected transition may emit `Disconnected` (AN-9).
- The event flow stays `DROP_OLDEST`: never switch to `SUSPEND` (it would block
  an OkHttp callback) or to dropping the newest.

## Controller invariants

- The optimistic bubble's `clientMessageId` is minted once per logical message
  and reused for every retry; the backend dedupes on it.
- A send in flight and the dispose-time mark-read run on the **detached** scopes,
  each bounded by `close()`'s grace. Never move them back onto the screen scope.
- `historyGeneration` guards every `loadMore` result against a window replaced
  mid-flight; a stale page re-issues or queues, it is never silently dropped.
- Every guard that can skip a pass queues a re-run (`catchUpRerunRequested`,
  `loadMoreRerunRequested`). Catch-up is not guarded on `isSending`.
- Realtime arrivals before the first page are buffered, deduped and bounded —
  not dropped, not appended to an empty list.
- Unread counting: the app-wide badge consumes `message:received` only. The chat
  list row consumes both twins and must be idempotent by `lastMessage.id` **and**
  skip the user's own messages (`isOwnMessage`) — `isChatOpen` does not cover it,
  because closing the conversation clears the open-chat id before the dispose
  leaves the room.
- Typing: `isTyping:true` once per burst plus a 2s keepalive, `isTyping:false`
  once on idle/send/dispose — and before `chat:leave`, or the peer's indicator
  strands on its 5s expiry. Clear received indicators on `Disconnected`.
- Presence: bump the epoch on connect **and** disconnect, clear the race set at
  both, apply the snapshot only to users with no live event since the connection
  opened, and discard a seed whose epoch is stale.
- The socket-down REST poll is gated on `repeatOnLifecycle(STARTED)` — a
  `LaunchedEffect` keeps running while the Activity is stopped, and a background
  poll marks messages the user never saw as seen (AN-2).

## Compose hazards

- Never launch a coroutine, start a timer or fire a request inside a
  `MutableStateFlow.update { }` lambda: `update` retries the lambda on CAS
  failure, so the side effect runs more than once.
- Read the latest callback through `rememberUpdatedState` in long-lived
  `LaunchedEffect`s instead of keying the effect on it and restarting the
  collector.
- Controllers created with `remember(scope, chatId)` do not see a later change of
  another parameter — pass volatile values (like `currentUserId`) at the call
  site instead of capturing them at construction.
- `DisposableEffect`'s `onDispose` ordering is load-bearing: flush mark-read and
  the typing stop **before** `chat:leave`, and `close()` last.

## Anti-regression self-review (mandatory before every commit here)

Previous regressions repeated six shapes. Re-read your own diff against all six,
every time:

1. **A guard that swallows a trigger.** Every `if (inProgress) return` must queue
   a re-run. A dropped trigger leaves a permanent gap in the thread.
2. **State cleared on disconnect that a later path depends on.** `joinedChatIds`
   is the canonical example — clearing it is correct, and the screen's rejoin is
   what makes it safe.
3. **Counters or epochs not reset per session.** A breaker inherited across
   sessions gives up before the refresh handler runs; one reset in the wrong
   place re-arms a breaker that must stay tripped.
4. **A fix placed in a layer that recomposition or dispose destroys.** If it must
   survive the screen, it belongs in the client singleton or a detached scope.
5. **Removing an accidental backstop.** If the change deletes a mechanism that
   was masking something, find out what it was masking first.
6. **An unbounded retry, or a bound that turns "eventually succeeds" into
   "definitively lost".** Both are bugs; pick the bound deliberately.

Then ask: does this still match `docs/api/messages/websocket.md` **and** what web
and iOS do? A behaviour change that only lands on Android is a contract split.

## Tests

- **Behavioural change lands with its test, in the same commit.**
- Match the layer: pure rules as pure functions (`presenceAfter`,
  `unreadCountAfter`, `shouldSurfaceRealtimeError`, `shouldHoldConnection`,
  `reconnectBackoffMillis`), client mechanics through the seams (`handleFrame`,
  `markConnectedForTesting`, `isJoinPending`, `isRoomJoined`,
  `publishAuthGaveUp`), controllers against a fake `ChatsGateway` with injectable
  timings.
- Do not add a real 10s wait to a test — inject a short timeout instead. Where a
  debounce genuinely has to elapse, poll for the effect rather than sleeping past
  it.
- Cover the transition, not the happy path: background→foreground→rejoin→catch-up,
  the `message:new`/`message:received` twin pair, a repeated `clientMessageId`, a
  stale-generation `loadMore`, the give-up path of every bounded budget.
- New realtime UI state gets a Compose test for the state transition, not only a
  render.

## Quality gate

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

(or `make assemble-debug`, `make lint`, `make test` — CI calls the Makefile).
Instrumented tests: `./gradlew :app:connectedDebugAndroidTest` (needs a
device/emulator; CI runs it per PR).
