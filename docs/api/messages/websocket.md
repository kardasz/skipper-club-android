# WebSocket Events

SkipperClub uses a plain RFC 6455 WebSocket for chat, presence, and real-time
notifications. It does not use Socket.IO and has no long-polling fallback.

## Connection

Connect to:

```text
wss://api.skipperclub.app/v1/ws/chat
```

Authenticate with either:

- `Authorization: Bearer <access-token>` during the HTTP upgrade; or
- `?token=<access-token>` as a query parameter (needed by browser-native
  `WebSocket`, which cannot set arbitrary upgrade headers).

An invalid or missing token causes the accepted connection to close with
WebSocket status 1008 (`unauthorized`). There is no Socket.IO auth object.
When the short-lived access token expires while the connection is open, the
server closes it with application code 4401 (`token expired`) — refresh the
token, then reconnect.

```javascript
const ws = new WebSocket(
  `wss://api.skipperclub.app/v1/ws/chat?token=${encodeURIComponent(accessToken)}`,
);
```

The server logs the request path only, and redacts the parameter
(`token=[Filtered]`) from error reports before they leave the process. Clients
should still avoid logging URLs containing query tokens. Native/mobile clients
that can set an upgrade header should prefer `Authorization`.

## Frame format

Every client and server message is a JSON text frame with the same envelope:

```json
{
  "event": "chat:join",
  "data": { "chatId": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e" }
}
```

```javascript
function send(event, data) {
  ws.send(JSON.stringify({ event, data }));
}

ws.addEventListener("message", ({ data }) => {
  const frame = JSON.parse(data);
  if (frame.event === "notification:new") showNotification(frame.data);
});
```

Unknown event names are ignored. Malformed payloads produce an `error` event;
the current protocol intentionally exposes only generic text, not REST problem
details.

## Rooms and delivery

Every authenticated connection automatically joins `user:{userId}`. This
personal room delivers `message:received` and `notification:new` without any
client subscription message.

`chat:join` adds one connection to a chat room after checking access.
`chat:leave` removes it. Room membership ends when the connection closes. After
reconnecting, the client must join each active chat again.

Hiding a chat also ends membership server-side: both `DELETE /chats/{chatId}`
and the bulk delete action (`POST /chats/actions` with `action: delete`) evict
the hider's connections from that chat's room across every API instance, so a
chat the client was just told is gone stops pushing `message:new` and
`chat:typing` at it. No `chat:leave` is needed; an explicit one still works
(leave is deliberately not access-checked) but is redundant after the
eviction. A new message in the chat un-hides it, after which `chat:join`
succeeds again.

Losing participation ends membership the same way: when a user is removed from
a chat's participants — today the cruise group-chat membership sync, including
its `cli cruises sync-chats` reconciliation (see
[Cruise Chats](../cruises/chats.md)) — their connections are evicted from that
chat's room across every API instance. Otherwise they would keep receiving
`message:new` and `chat:typing` for a chat they no longer belong to, for the
whole lifetime of the connection. No event announces the removal: the client
learns it lost access on its next REST fetch, so a chat opened at that moment
simply goes quiet until then. A later legitimate re-add is unaffected —
`chat:join` re-checks access and succeeds again.

Redis transports room fan-out and presence between API instances.

### Fan-out is post-commit and outlives the triggering request

Every real-time event is emitted **after** the write that caused it has
committed, and deliberately does not depend on the lifetime of the request (or
the WebSocket connection) that triggered it. A client that disconnects the
moment after its `201`, a proxy that times out, or a sender that closes its
socket right after `message:send` therefore cannot cost the _other_ clients
their events: the fan-out runs on a context detached from the caller's, bounded
to 5 seconds so it can never hang on a slow Redis. This matters only across
instances — delivery to connections on the same instance never depended on the
caller's context, which is exactly why the failure mode was invisible in
single-instance setups. Request-scoped values (request id, negotiated language)
are preserved, so a fan-out still correlates with the request that produced it.

An event whose payload is the same for every recipient — `message:received` to
a chat's participants, `presence:update` to a user's co-participants — is
encoded once and crossed between instances once, however many recipients it
has: a 30-person chat costs one Redis publish per message, not 30. This is
invisible in the protocol (the same frames reach the same clients, and a client
that is a member of two target rooms still receives exactly one copy);
`notification:new` is not batched, because each recipient's notification is a
different row and therefore a different payload.

## Keepalive and limits

- The server sends a protocol-level Ping approximately every 30 seconds and
  closes connections that do not answer with a Pong within 10 seconds. Browsers
  and standard WebSocket libraries answer pings automatically — no client code
  is needed, but the client must keep reading from the socket. The interval is
  a floor, not a promise: the ping, the presence refresh (which also repairs
  and re-announces presence lost to a Redis failure — see
  [Presence semantics](#presence-semantics)) and the heartbeat
  frame all run in the tick's own loop body, so a slow Redis or a
  ping-timeout wait pushes the next tick later (never earlier). Clients must
  therefore size their stale-connection threshold with headroom — see below.
- In addition to the protocol Ping (which it does not replace), the server
  emits a `heartbeat` event as a normal envelope frame on every one of those
  ticks, to every authenticated connection. It exists because browsers cannot
  observe protocol pings — the network stack answers them invisibly to
  JavaScript, so on an idle chat a browser has no signal to detect a half-open
  connection. A `heartbeat` is an ordinary text frame the browser _can_ see.
  Clients should derive liveness from any inbound frame (heartbeat included)
  and treat "no frame of any kind for a stale threshold" as a dead connection
  to reconnect. Use a threshold of at least 2.5× the interval (≥ 75 s); 90 s is
  recommended. Native clients that already watchdog protocol pings (Android
  OkHttp `pingInterval`, iOS `sendPing`) can ignore the event.
- Inbound frames are limited to 32 KiB; larger frames close the connection
  with 1009 (`message too big`).
- Inbound events are rate limited per connection: 10 events/second with a
  burst of 20. Excess frames are dropped, and each violation streak produces
  a single `error` event (`Rate limit exceeded`).
- Independently of the rate limit, inbound frames queue per connection (up to
  the same burst allowance of 20) while earlier frames are still being
  handled. Frames arriving beyond that backlog are dropped, again with a
  single `error` event per drop streak — deliberately reusing the
  `Rate limit exceeded` message, so clients see one backpressure signal
  rather than a new error kind. A streak ends when a frame is accepted again.
- The HTTP upgrade itself counts against the same global per-IP rate limit as
  the REST API (429 with problem+json when exceeded).
- Outbound delivery is buffered per connection; a client that stops reading
  long enough for 64 frames to queue up is disconnected as a slow consumer.

### Close codes

| Code | Reason          | Client action                            |
| ---- | --------------- | ---------------------------------------- |
| 1008 | `unauthorized`  | obtain a fresh token, then reconnect     |
| 4401 | `token expired` | refresh the access token, then reconnect |
| 1009 | message too big | fix the payload; do not retry it         |

## Client to server events

| Event          | `data`                             | Result                                                                       |
| -------------- | ---------------------------------- | ---------------------------------------------------------------------------- |
| `chat:join`    | `{chatId}`                         | joins room, replies `chat:joined`                                            |
| `chat:leave`   | `{chatId}`                         | leaves room, replies `chat:left`                                             |
| `message:send` | `{chatId, text, clientMessageId?}` | persists message, replies `message:sent` carrying the created message object |
| `message:read` | `{chatId, messageId}`              | records receipt, replies `message:read:confirmed`                            |
| `chat:typing`  | `{chatId, isTyping}`               | replies `chat:typing:sent` (`isTyping` is required; omitting it is an error) |

Chat and message IDs must be UUIDv7 — the WebSocket handlers reject any other
UUID version with an `error` event (`Invalid payload`; the REST API parses
generic UUIDs, so this check is stricter on the WS path). Message text is 1 to
1000 Unicode characters (runes).

The optional `clientMessageId` is a client-generated idempotency key, unique
per (chat, sender). **Any UUID version is accepted** — it is a client
identifier, not a server entity id, so the UUIDv7 restriction above
deliberately does not apply. Re-sending with the same `clientMessageId`
replies `message:sent` carrying the already-created message and does **not**
broadcast `message:new` / `message:received` again — safe retries after a
dropped connection without duplicating the message. The same key works on the
REST send (`POST /chats/{chatId}/messages`), and both transports share one
key space per (chat, sender).

```javascript
ws.addEventListener("open", () => send("chat:join", { chatId }));
send("message:send", { chatId, text: "Ahoy!" });
```

After a successful `message:send`:

1. the sender receives `message:sent` carrying the created message object —
   the same wire shape as `message:new` — so it can reconcile a pending
   optimistic send with the server-assigned id and timestamps;
2. every connection currently joined to the chat, including the sender's
   joined connection, receives `message:new`;
3. every other participant's personal room receives `message:received`, even
   when that participant has not joined the chat room.

All three events echo the send's `clientMessageId` (omitted when the send
carried none). **Reconcile the optimistic entry on that key.** Ordering alone
is not enough: per-connection replies are ordered, but a client that falls back
to REST for one send and uses the socket for the next has no single ordered
stream to match against, and matching on message text collides the moment the
same text is sent twice. The key also identifies the message on the sender's
_other_ connections, which see it via `message:new` with no reply ordering to
lean on.

All three events carry the same message object. Its `read` field is always
`false`: the server builds one snapshot when the message is created and sends
the identical bytes to every recipient, so the flag cannot carry per-recipient
read state. Only the REST message responses expose per-viewer read state;
clients derive read/unread state from their own read receipts.

## Transport parity (REST + WebSocket)

Chat writes have two equivalent entrypoints; **both produce identical
server-emitted events**. The fan-out lives in one shared backend component
(`internal/messages/realtime.go`), so the two paths cannot drift apart. A
client may send over REST (like Android does) or over WS (like web and iOS
do) — every other connected client receives the same real-time events either
way.

| Write               | WS entrypoint  | REST entrypoint                                       | Events emitted to others                                                           |
| ------------------- | -------------- | ----------------------------------------------------- | ---------------------------------------------------------------------------------- |
| create message      | `message:send` | `POST /chats/{chatId}/messages`                       | `message:new` → chat room; `message:received` → other participants' personal rooms |
| mark message read   | `message:read` | `PATCH /chats/{chatId}/messages/{messageId}` (`true`) | `message:read` receipt → chat room                                                 |
| mark message unread | —              | `PATCH …/messages/{messageId}` (`read: false`)        | none                                                                               |
| bulk mark-read      | —              | `POST /chats/actions` (`mark-read`)                   | one `message:read` receipt → each chat room where something was newly marked       |
| hide chat           | —              | `DELETE /chats/{chatId}` / `POST /chats/actions`      | none — but the hider's own connections are evicted from the chat room (both paths) |
| remove participant  | —              | cruise membership sync / `cli cruises sync-chats`     | none — but the removed user's connections are evicted from the chat room           |
| typing indicator    | `chat:typing`  | —                                                     | `chat:typing` → chat room, excluding the sender's connection                       |

An idempotent replay (a send repeating an earlier `clientMessageId`) emits
**no** events to others on either transport — only the acknowledgement
(`message:sent` / `201`) carrying the already-created message.

Only the acknowledgement differs: WS callers get the reply events
(`message:sent` carrying the created message object, the empty
`message:read:confirmed` and `chat:typing:sent`), REST callers get the HTTP
response (`201` with the message object, `204`) instead.

`message:received` is never delivered to the sender's own personal room —
not even to the sender's other devices. A sender's second device sees the new
message only if it has joined the chat room (`message:new`) or on its next
REST fetch.

## Read receipts are cascading

A `message:read` receipt (`{messageId, userId, readAt}`) means `userId` has
read `messageId` **and every earlier message in that chat** — clients should
treat everything up to and including the referenced message as read by that
user, not just the single message.

This is why bulk mark-read (`POST /chats/actions` with `mark-read`) emits
exactly one receipt per chat: the receipt references the newest message that
actually transitioned from unread to read in that chat, and the cascade
covers the rest. A chat where nothing was newly marked (everything already
read, or no messages at all) emits no receipt. The single-message paths (WS
`message:read`, REST `PATCH` with `read: true`) emit one receipt per call
with the same cascading meaning.

## Server to client events

| Event                    | `data`                       | Scope                                             |
| ------------------------ | ---------------------------- | ------------------------------------------------- |
| `chat:joined`            | `{chatId}`                   | requesting connection                             |
| `chat:left`              | `{chatId}`                   | requesting connection                             |
| `message:sent`           | message object               | requesting connection                             |
| `message:new`            | message object               | joined chat room                                  |
| `message:received`       | message object               | other participants' personal rooms                |
| `message:read:confirmed` | `{}`                         | requesting connection                             |
| `message:read`           | `{messageId,userId,readAt}`  | joined chat room                                  |
| `chat:typing:sent`       | `{}`                         | requesting connection                             |
| `chat:typing`            | `{chatId,userId,isTyping}`   | joined chat room except originating connection    |
| `presence:update`        | `{userId,isOnline,lastSeen}` | personal rooms of the user's chat co-participants |
| `notification:new`       | notification object          | recipient's personal room                         |
| `heartbeat`              | `{ts}`                       | every connection, every ~30s                      |
| `error`                  | `{type,message,timestamp}`   | requesting connection                             |

Message payload:

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b30",
  "chatId": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "text": "Ahoy!",
  "read": false,
  "createdAt": "2026-07-10T12:00:00Z",
  "updatedAt": "2026-07-10T12:00:00Z",
  "user": {
    "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99",
    "name": "Jan Kowalski",
    "avatarUrl": null
  }
}
```

## Presence semantics

Presence is connection-scoped: a user becomes online when their first
WebSocket connection (across all devices and API instances) is established,
and offline when their last connection closes. `presence:update` is delivered
only to users sharing at least one chat with the affected user (the legacy
Socket.IO gateway broadcast it to every connected client; that leak is fixed
here).

Both transitions fire exactly once, and both survive a transient Redis failure:

- **Offline** is decided by the user's presence going empty, not by the
  disconnecting connection being the one removed from it. A connection whose
  presence entry had already aged out — its server-side refreshes were failing
  for longer than the staleness horizon — still takes the user offline (and
  still persists `lastSeen`) when its close is what leaves the user with no
  presence at all. Concurrent disconnects of the same user still produce one
  event, never two.
- **Online** is retried by the server's own keepalive. If the connect-time
  presence write fails, the connection stays fully usable and the next
  keepalive tick (~30 s) puts the user back into presence and broadcasts the
  `presence:update{isOnline:true}` the connect could not send. Clients need no
  special handling: they may see the online event up to one tick late, never
  twice.

### Seeding presence after (re)connect

`presence:update` is **push-only** — it fires on the transitions above, not on
connect — so a client that just connected knows nothing about co-participants
who were already online (their transition happened before the client was
listening). Seed the initial state from the REST snapshot instead:

- **`GET /chats/presence`** returns the current online state of the caller's
  chat co-participants — the same audience `presence:update` targets, never
  arbitrary users. Each entry is `{userId, isOnline, lastSeen}`. `lastSeen` is
  the persisted moment the user's last connection closed (`null` if they have
  never cleanly disconnected since this field shipped, and ignored by clients
  while `isOnline` is true). See [chats.md](./chats.md) for the full schema.

The recommended, uniform client lifecycle is **clear on disconnect → seed on
(re)connect → live events win afterwards**:

1. On WS disconnect, clear in-memory presence (avoids showing stale "online").
2. After every successful WS (re)connect, call `GET /chats/presence` and seed.
3. Apply live `presence:update` events on top of the seed.

**Snapshot-vs-live race rule (mandatory, identical on every client):** track
the set of `userId`s that received a live `presence:update` since the current
connection opened; when the snapshot response arrives, apply it **only to
`userId`s not in that set**. Live events always overwrite unconditionally. This
makes seeding correct regardless of whether the snapshot response lands before
or after concurrent live events — a snapshot applied blindly after a live event
would regress to exactly the staleness this endpoint removes.

`lastSeen` becomes meaningful for offline users because the last-disconnect
moment is persisted server-side (`users.last_seen_at`). A crash-killed instance
writes no `last_seen_at` and emits no offline event; such a user may read as
online until presence self-heals via TTL, and their `lastSeen` stays null or
stale until their next clean disconnect.

## Errors

Failed operations produce an `error` event with generic text — never REST
problem details:

- input validation (malformed payload JSON, bad or wrong-version UUID,
  wrong text length, missing required field such as `isTyping`):
  `Invalid payload`. A frame that is not a well-formed envelope at all is
  reported once per streak, not once per frame: a client looping on bad JSON
  gets a single `Invalid payload`, and the next well-formed frame arms the
  next one;
- chat operations whose access check fails:
  `Chat not found or access denied`;
- read operation whose access check fails:
  `Message not found or access denied` —
  both collapses deliberately hide whether the resource exists at all;
- inbound rate limit exceeded, or frames dropped because the connection's
  handler backlog is full (see "Keepalive and limits"): `Rate limit exceeded`;
- genuine server-side failures (service/DB): `Internal server error`.

```json
{
  "event": "error",
  "data": {
    "type": "websocket_error",
    "message": "Chat not found or access denied",
    "timestamp": "2026-07-10T12:00:00Z"
  }
}
```

### Failure isolation

An unexpected server-side fault while handling one frame costs that frame
only. The connection stays open, every frame after it is handled normally, and
no other connection is affected. Such a frame is the one case that produces
**no** answer at all — not even an `error` event — because the failure happens
before any reply is decided, so a client waiting for `message:sent`,
`chat:joined` or another reply must treat "no answer" the same way it treats a
dropped connection: retry. `message:send` retries are safe with a
`clientMessageId` (see above); `chat:join` and `message:read` are idempotent.

The same isolation applies to server-initiated delivery: a fault while
delivering one broadcast costs that single event on the affected instance, not
the connection and not the other events. If a fault instead hits the machinery
that owns the socket itself (the connection's writer or its keepalive), that
one connection is closed abruptly — no close handshake, so browsers report
1006 — while every other connection keeps running. Reconnect as below and
catch up over REST.

## Reconnection

Native `WebSocket` does not reconnect automatically. Implement bounded
exponential backoff with jitter, refresh an expired access token before
reconnecting (always after a 1008 or 4401 close), and replay `chat:join` for
currently visible chats only after the new connection is open. After a
reconnect, catch up missed messages over REST (`GET /chats/{id}/messages`)
using the `before` keyset cursor, not `offset`: page backwards following each
response's `nextCursor` until a message the client already holds appears. The
chat keeps growing during the catch-up, and offset paging silently skips one
older message per message that arrives between two page fetches — see
[Chats REST API → Paging modes](./chats.md#paging-modes).

## Related

- [AsyncAPI specification](../../api/asyncapi.yaml)
- [Socket.IO to WebSocket migration guide](./socketio-to-websocket-migration.md)
- [Chats REST API](./chats.md)
- [Notifications](../notifications/index.md)
