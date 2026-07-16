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

Avoid logging URLs containing query tokens. Native/mobile clients that can set
an upgrade header should prefer `Authorization`.

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

Redis transports room fan-out and presence between API instances.

## Keepalive and limits

- The server sends a protocol-level Ping every 30 seconds and closes
  connections that do not answer with a Pong within 10 seconds. Browsers and
  standard WebSocket libraries answer pings automatically — no client code is
  needed, but the client must keep reading from the socket.
- Inbound frames are limited to 32 KiB; larger frames close the connection
  with 1009 (`message too big`).
- Inbound events are rate limited per connection: 10 events/second with a
  burst of 20. Excess frames are dropped, and each violation streak produces
  a single `error` event (`Rate limit exceeded`).
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

| Event          | `data`                | Result                                            |
| -------------- | --------------------- | ------------------------------------------------- |
| `chat:join`    | `{chatId}`            | joins room, replies `chat:joined`                 |
| `chat:leave`   | `{chatId}`            | leaves room, replies `chat:left`                  |
| `message:send` | `{chatId, text}`      | persists message, replies `message:sent`          |
| `message:read` | `{chatId, messageId}` | records receipt, replies `message:read:confirmed` |
| `chat:typing`  | `{chatId, isTyping}`  | replies `chat:typing:sent`                        |

IDs must be UUIDv7 — the WebSocket handlers reject any other UUID version with
an `error` event (the REST API parses generic UUIDs, so this check is stricter
on the WS path). Message text is 1 to 1000 Unicode characters (runes).

```javascript
ws.addEventListener("open", () => send("chat:join", { chatId }));
send("message:send", { chatId, text: "Ahoy!" });
```

After a successful `message:send`:

1. the sender receives `message:sent` with `{}`;
2. every connection currently joined to the chat, including the sender's
   joined connection, receives `message:new`;
3. every other participant's personal room receives `message:received`, even
   when that participant has not joined the chat room.

Both message events carry the same message object. Its `read` field is always
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
| bulk mark-read      | —              | `POST /chats/actions` (`mark-read`)                   | none (no per-message receipts)                                                     |
| hide chat           | —              | `DELETE /chats/{chatId}` / `POST /chats/actions`      | none                                                                               |
| typing indicator    | `chat:typing`  | —                                                     | `chat:typing` → chat room, excluding the sender's connection                       |

Only the acknowledgement differs: WS callers get the reply events
(`message:sent`, `message:read:confirmed`, `chat:typing:sent`), REST callers
get the HTTP response (`201` with the message object, `204`) instead.

`message:received` is never delivered to the sender's own personal room —
not even to the sender's other devices. A sender's second device sees the new
message only if it has joined the chat room (`message:new`) or on its next
REST fetch.

## Server to client events

| Event                    | `data`                       | Scope                                             |
| ------------------------ | ---------------------------- | ------------------------------------------------- |
| `chat:joined`            | `{chatId}`                   | requesting connection                             |
| `chat:left`              | `{chatId}`                   | requesting connection                             |
| `message:sent`           | `{}`                         | requesting connection                             |
| `message:new`            | message object               | joined chat room                                  |
| `message:received`       | message object               | other participants' personal rooms                |
| `message:read:confirmed` | `{}`                         | requesting connection                             |
| `message:read`           | `{messageId,userId,readAt}`  | joined chat room                                  |
| `chat:typing:sent`       | `{}`                         | requesting connection                             |
| `chat:typing`            | `{chatId,userId,isTyping}`   | joined chat room except originating connection    |
| `presence:update`        | `{userId,isOnline,lastSeen}` | personal rooms of the user's chat co-participants |
| `notification:new`       | notification object          | recipient's personal room                         |
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

## Errors

Access failures deliberately collapse existence and authorization details:

- chat operations: `Chat not found or access denied`;
- read operation: `Message not found or access denied`;
- malformed UUID, invalid text length, or unexpected failure:
  `Internal server error`.

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

## Reconnection

Native `WebSocket` does not reconnect automatically. Implement bounded
exponential backoff with jitter, refresh an expired access token before
reconnecting (always after a 1008 or 4401 close), and replay `chat:join` for
currently visible chats only after the new connection is open. After a
reconnect, catch up missed messages over REST (`GET /chats/{id}/messages`).

## Related

- [AsyncAPI specification](../../api/asyncapi.yaml)
- [Socket.IO to WebSocket migration guide](./socketio-to-websocket-migration.md)
- [Chats REST API](./chats.md)
- [Notifications](../notifications/index.md)
