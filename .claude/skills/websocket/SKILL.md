---
name: websocket
description: Context and architecture of the Android realtime stack — WebSocketChatRealtimeClient, RealtimeConnectionManager, the app-scoped stores (presence, unread), and the chat list/conversation controllers. Read this before touching any of those, before changing reconnect/join/catch-up/read behaviour, and when debugging missed messages, a stuck typing indicator or a room that goes deaf after backgrounding.
---

# Android realtime architecture

Binding implementation requirements live in `.claude/rules/websocket.md`. This
document is the **why**: what the pieces are, which lifecycle constraints shaped
them, and the failure modes each guard was written against. Most of what looks
redundant here is load-bearing — the regressions in this area came from removing
it.

## What it is

One OkHttp `WebSocket` to `${API_BASE_URL}/v1/ws/chat` with an
`Authorization: Bearer` upgrade header, held **app-wide** for as long as the app
is foregrounded and logged in. It serves chat, presence and notifications. Every
frame both directions is `{"event": "...", "data": {...}}`.

The contract is `docs/api/asyncapi.yaml` + `docs/api/messages/websocket.md` —
**byte-identical mirrors** of the backend's copies. They are part of the
contract, not convenience copies: mirror drift was the root cause of an earlier
cross-client split.

### File map

| File                                                          | Owns                                                                                                          |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| `data/ChatRealtimeClient.kt`                                  | the socket singleton: connect/disconnect, backoff, auth breaker, `JoinAckTracker`, frame dispatch, event flow |
| `data/RealtimeConnectionManager.kt`                           | when the connection is held (foreground ∧ authenticated) and the network-return fast path                     |
| `data/PresenceStore.kt`                                       | app-wide online/offline cache, seeded from `GET /chats/presence` per connection                               |
| `data/UnreadMessagesStore.kt` / `UnreadNotificationsStore.kt` | app-wide badges, optimistic + reconciled                                                                      |
| `ui/main/messages/ChatConversationController.kt`              | the open thread: paging, catch-up, optimistic send, mark-read, typing state                                   |
| `ui/main/messages/ChatListController.kt`                      | list ordering, previews, per-row unread                                                                       |
| `ui/main/messages/ChatConversationScreen.kt`                  | join/leave, the typing send state machine, the socket-down REST poll                                          |
| `ui/main/messages/MessagesScreen.kt`                          | `ChatListRealtimeEffect`, the realtime-error policy                                                           |
| `ui/main/MainScreen.kt`                                       | the persistent app-wide give-up banner                                                                        |
| `SkipperClubApplication.kt`                                   | starts the app-scoped owner and the three stores                                                              |

## Decision 1 — the socket is app-scoped, the UI is not

`RealtimeConnectionManager` (a `DefaultLifecycleObserver` on
`ProcessLifecycleOwner`) owns the connection: connect when foregrounded **and**
authenticated, disconnect otherwise. It is started once from
`SkipperClubApplication`, long before any Activity.

That is why the badges and the presence cache are **app-scoped singletons**
started in the same place. The Messages tab's `ChatListController` only exists
while that tab is composed, so it structurally cannot be the badge source — a
message arriving while the user is on the Feed has to count.

Every authenticated connection auto-joins its personal room server-side, which is
what makes `message:received` and `notification:new` work with no client
subscription.

## Decision 2 — `disconnect()` clears `joinedChatIds`, so the screen re-joins

A deliberate `disconnect()` (backgrounding, logout) clears the joined-room set so
a signed-out session can never replay another user's rooms. The consequence is
that the client's own `onOpen` replay **cannot** restore the open conversation's
room after a background→foreground cycle.

That gap is closed by the conversation screen itself: on every `Connected` it
calls `rejoinChat`, guarded by `isRoomJoined` (C-AN-1 / D-AN-1). Without it the
conversation never re-joined, never received `ChatJoined`, and therefore never
caught up on messages sent while the app was in the background.

The `isRoomJoined` guard exists because `Connected` is emitted **before** the
`onOpen` replay finishes on the OkHttp thread: membership in the set already
guarantees a `chat:join` was or will be sent on this very connection, so skipping
cannot lose the trigger — it only closes the window in which a collector
observing `Connected` mid-replay fired a duplicate join and a duplicate catch-up.

## Decision 3 — catch-up keys off `ChatJoined`, never `Connected`

`Connected` only means the socket is up; the join replay runs _after_ it. A
message created between a REST snapshot taken on `Connected` and the server
processing our `chat:join` would reach neither path.

So `ChatRealtimeEvent.ChatJoined` — minted from the server's `chat:joined` ack —
is the catch-up trigger. It is the one ack this client correlates, because the
server's `error` frame carries no correlation id and a positive ack is the only
reliable signal a join landed.

## Decision 4 — OkHttp's ping is the liveness watchdog; `heartbeat` is ignored

`pingInterval(30s)` makes OkHttp send its own pings and fail the connection when
a pong does not come back, surfacing as `onFailure` into the normal backoff path.
A peer that vanishes without a FIN (dropped NAT binding, sleeping radio) would
otherwise leave the client reading a socket that never delivers again:
`isConnected` stays true, messages silently stop, nothing reconnects.

The server's application-level `heartbeat` frame exists for browsers, which
cannot observe protocol pings. Android has a real watchdog, so `heartbeat` is in
`SILENT_EVENTS`: logging it — as an unhandled frame or as an ack — would put a
line in the debug log twice a minute and bury genuinely unknown events.

## Decision 5 — sends go over REST, receives over the socket

Android creates messages with `POST /chats/{id}/messages` carrying a
`clientMessageId`; the backend's fan-out is identical either way (one shared
component), so every other client sees the same events. `message:sent` is
therefore an uncorrelated ack here and lives in `SERVER_ACK_EVENTS`.

The socket carries **typing** and, in exactly one place, a **read receipt**.
Everywhere else the REST bulk mark-read is the receipt source: per the transport
parity table it already broadcasts `message:read` to the room, so sending both
delivered two identical receipts and spent two of the server's 10 events/second
slots. The single exception is `flushPendingMarkRead` at dispose, where the frame
must reach the server **before** `chat:leave` — receipts for a room already left
are dropped.

## Decision 6 — the backoff is interruptible, and 403 gets its own tier

`ReconnectBackoffGate` lets a returning network cut a pending backoff short
(`ConnectivityManager` default-network callback → `onNetworkAvailable`), so a
drop near the 30s cap does not leave realtime dead for half a minute after
connectivity is back. Only the delay is cut: the retry re-enters the normal
attempt path, so the token provider runs and superseded attempts are still
refused.

`pendingSkip` is guarded by the instance monitor rather than merely `@Volatile`:
a wait's teardown running late could null out the field a _successor_ wait had
installed, silently making that successor un-skippable for its full duration.
The teardown therefore clears it only while it still holds its own deferred,
compared by identity.

Failure tiers:

- **401 on the upgrade** — the token was rejected server-side while still locally
  valid; force a refresh, or the client loops on the same rejected token.
- **403** — the token is valid but access is denied; a refresh cannot fix it and
  refreshing would hammer the refresh endpoint in a `refresh → reconnect → 403`
  loop. It parks on a dedicated 5-minute tier instead of retrying every 30s.
- **everything else** (including `1009`) — plain bounded backoff. `1009` used to
  be terminal, but nothing re-sends the offending frame after a reconnect, so
  refusing to retry only meant one anomalous frame killed realtime for the rest
  of the process. It is logged loudly instead.

## Decision 7 — the auth breaker is state, not just an event

After 3 consecutive auth rejections the client stops auto-reconnecting; hammering
the refresh and upgrade endpoints cannot fix credentials.

The give-up is exposed as `connectionGaveUp: StateFlow<Boolean>`, not only as an
event. `_events` is a replay-0 `SharedFlow` whose sole collector lives under the
Messages tab, so on any other tab a give-up was invisible — and a consumer
attaching later could never learn about it. `MainScreen` renders a persistent
app-wide banner off that flow (parity with web's banner, iOS's alert).

The breaker resets on every `connect()` **and** on every socket that actually
opens: without the per-session reset, a session that ended in a give-up poisoned
every later one — its first rejection landed on an already-exhausted count and
gave up before the refresh handler ever ran (C-AN-2).

## Decision 8 — publish and state flips are one atomic step

`connect`, `disconnect`, `openSocketIfCurrent`, `publishOpenIfCurrent` and
`markDisconnectedIfCurrent` share the object monitor because the guard and the
mutation must not interleave:

- an unlocked `scope !== ownerScope` check can pass, lose the thread to a full
  `disconnect()` (scope cancelled, socket closed, `Disconnected` emitted), and
  only then set `isConnected = true` — for a session that no longer exists. The
  socket's own later close callback bails on its scope guard, so `isConnected`
  would stay true after logout: the REST-poll fallback stays disabled and
  consumers see a spurious `Connected`;
- the same race on the _open_ side publishes a live socket that nothing will ever
  close, so the user reads as online to everyone after logging out.

Holding the monitor is safe there: `tryEmit` and `MutableStateFlow` writes never
suspend, and no other lock is taken inside.

## Decision 9 — the event flow drops the oldest, and everything lost is recoverable

Socket callbacks run off-main and must never block, so emission is always
`tryEmit` into a `MutableSharedFlow` with 256 extra buffer and
`DROP_OLDEST`. Collectors run on the main thread, so a burst can outrun them.

Dropping the _oldest_ keeps the live tail of the conversation intact. Anything
lost is recoverable: `ChatJoined` triggers a REST catch-up, `Connected` refreshes
the list, and the list refetches on open.

## Decision 10 — work that must outlive the screen gets its own scope

Two detached `CoroutineScope`s (same dispatcher, own `SupervisorJob`), each
bounded by a grace timer in `close()`:

- **`sendDispatchScope`** — a back-tap right after sending cancels the screen
  scope, and with it a POST launched there: the OkHttp call aborted, the bubble
  gone with the screen, no retry state left, and the message silently never left
  the device (AN-1). The send watchdog lives here too, for the same reason.
- **`markReadFlushScope`** — the dispose-time flush must survive the screen scope
  being torn down at that very moment. `close()` cancels it after a grace period
  rather than immediately, because a synchronous cancel would kill exactly the
  REST mark-read it exists to protect.

## Decision 11 — history has a generation, catch-up has a queued re-run

`historyGeneration` is bumped whenever the window and its cursor are replaced
wholesale (`retry`, `reloadFromScratch`). A `loadMore` page resolving against a
stale generation belongs to a discarded window: merging it — or worse, writing
its deep cursor — would splice an old page into the fresh page 0 and leave an
invisible, never-closed hole (D-AN-3). Such a page re-issues itself, or queues
behind an in-flight reload, rather than being silently dropped.

`catchUpRerunRequested` does the same for catch-up: a trigger arriving mid-loop
runs one more pass afterwards instead of being lost (AN-4). Catch-up is
deliberately **not** guarded on `isSending` — skipping it because a send happened
to be in flight dropped that reconnect's reconciliation entirely.

Realtime arrivals that land before the first page are **buffered**
(`pendingRealtimeMessages`, bounded, drop-oldest) rather than dropped: `chat:join`
is sent before and acked independently of the initial REST load, so there is a
real window in which the room is live but the list is empty, and appending there
would put the message above history it precedes.

## Decision 12 — text and time are compared carefully

- `sortedByCreationOrder` parses timestamps instead of comparing strings: the API
  omits fractional seconds when zero, and `"…:00.500Z"` sorts _before_ `"…:00Z"`
  lexicographically. Ties break on the UUIDv7 id. An unparseable timestamp sorts
  to the far past, so a malformed row can never masquerade as the catch-up anchor.
- `truncateToCodePoints` caps the composer by Unicode code points, not UTF-16
  units: `String.take` counts an emoji as two toward a limit the server counts as
  one and, worse, a cut landing between a surrogate pair leaves a lone surrogate
  the user sees as `�` and the server rejects.

## Cross-client contract

Identical on web, iOS and Android by agreement:

| Concern            | Value                                      |
| ------------------ | ------------------------------------------ |
| typing keepalive   | 2s while typing                            |
| typing idle-stop   | 3s of no keystrokes → one `isTyping:false` |
| typing expiry (rx) | 5s without a fresh `true`                  |
| send confirmation  | 12s → `Failed` + retry affordance          |
| history page       | 30                                         |
| catch-up page/cap  | 50 × 5 pages, then a full page-0 reload    |
| join ack           | 3 sends total, 10s apart                   |
| auth breaker       | 3 consecutive failures                     |

Read acks are the one budget Android does not have: it does not track
`message:read:confirmed` at all, because the REST bulk mark-read the backend
broadcasts from is its receipt source (web and iOS do track them, with a cap of
3). iOS's join-ack budget counts _re-sends_, so it gives up after 4 sends where
web and Android give up after 3 — check before "aligning" either.

Also contractual: `Rate limit exceeded` is backpressure and must never be shown
(`shouldSurfaceRealtimeError`); read receipts cascade; `read` in a realtime
payload is always `false`; `presence:update` is push-only, seeded from
`GET /chats/presence` under the snapshot-vs-live race rule (`PresenceStore`'s
`liveUpdatedSinceOpen` + `connectionEpoch`, invalidated on connect **and**
disconnect).

Deploy order matters: the API must ship before the clients for the keyset cursor
contract — clients treat a missing `nextCursor` as the end of pagination.

## Tests

| Layer            | Where                                                                                        |
| ---------------- | -------------------------------------------------------------------------------------------- |
| socket mechanics | `data/ChatRealtimeClientTest.kt`                                                             |
| connection owner | `data/RealtimeConnectionManagerTest.kt`                                                      |
| stores           | `data/PresenceStoreTest.kt`, `UnreadMessagesStoreTest.kt`, `UnreadNotificationsStoreTest.kt` |
| thread + list    | `ui/main/messages/ChatConversationControllerTest.kt`, `ChatListControllerTest.kt`            |
| event dispatch   | `ui/main/messages/ConversationRealtimeDispatchTest.kt`, `RealtimeErrorPolicyTest.kt`         |

The client exposes deliberate seams so behaviour is testable without a live
socket: `handleFrame`, `markConnectedForTesting`, `isJoinPending`, `isRoomJoined`,
`publishAuthGaveUp`, and injectable `JoinAckTracker` / `ReconnectBackoffGate` /
`AuthFailureBreaker`. Controllers take injectable timings and a fake
`ChatsGateway`. Pure functions (`presenceAfter`, `seededPresence`,
`unreadCountAfter`, `reconnectBackoffMillis`, `shouldSurfaceRealtimeError`,
`shouldHoldConnection`) are extracted precisely so the rules can be tested without
coroutine machinery.

## History

This stack has been through several cross-client audits (`ws_audit_2026-07-21`,
`ws_audit_2026-07-24` + follow-up) and a closing fix round
(`ws_fix_report_2026-07-25`) in the workspace root. Findings are referenced in
code comments by their audit id (`AN-…`, `D-AN-…`, `C-AN-…`). Regressions kept
recurring in the same handful of shapes — that catalogue is the checklist in
`.claude/rules/websocket.md`. When a comment explains why something is _not_
simpler, it is a scar; read it before deleting it.
