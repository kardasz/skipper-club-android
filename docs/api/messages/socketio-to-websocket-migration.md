# Socket.IO to WebSocket Migration Guide

Audience: the SkipperClub client teams (web, Android, iOS). This guide
describes how to migrate from the legacy Socket.IO gateways to the plain
RFC 6455 WebSocket endpoint of the current API.

The Go backend has no Socket.IO server (no maintained Socket.IO server exists
for Go — see ADR-0001 §2.3). The event catalogue is unchanged; the transport,
connection model, and authentication handshake are not.

## What changed

| Aspect             | Before (Socket.IO)                                       | After (plain WebSocket)                                                     |
| ------------------ | -------------------------------------------------------- | --------------------------------------------------------------------------- |
| Endpoint           | `https://api…` + namespaces `/chat` and `/notifications` | single `wss://api.skipperclub.app/v1/ws/chat`                               |
| Notifications      | separate `/notifications` namespace connection           | same connection; `notification:new` arrives automatically                   |
| Wire format        | Socket.IO packets (`socket.emit(event, data)`)           | JSON text frame: `{"event": "...", "data": {...}}`                          |
| Auth               | `auth: { token }` handshake object (plus header/query)   | `Authorization: Bearer <token>` header **or** `?token=` query param         |
| Transport fallback | long-polling fallback                                    | none — WebSocket only                                                       |
| Reconnection       | built into the client library                            | client-implemented backoff + rejoin                                         |
| Acks               | event-style replies (`message:sent`, `chat:joined`, …)   | unchanged — same reply events                                               |
| Keepalive          | Socket.IO ping/pong (25 s / 20 s)                        | protocol-level Ping every 30 s, Pong deadline 10 s (automatic)              |
| Presence           | online on first `chat:join`, broadcast to every client   | online on connect, offline on last close, sent only to chat co-participants |
| Token expiry       | connection outlived the token                            | server closes with code 4401 (`token expired`)                              |
| Rate limiting      | none on WS                                               | 10 events/s per connection (burst 20); upgrade counts to per-IP limit       |
| Max frame size     | 1 MB (Socket.IO default)                                 | 32 KiB                                                                      |

**Unchanged:** every event name and payload documented in
[asyncapi.yaml](../../api/asyncapi.yaml) and [websocket.md](./websocket.md) —
`chat:join`, `chat:leave`, `message:send`, `message:read`, `chat:typing`,
`chat:joined`, `chat:left`, `message:sent`, `message:new`,
`message:received`, `message:read:confirmed`, `chat:typing:sent`,
`presence:update`, `notification:new`, `error`. The personal room
(`user:{userId}`) is still joined automatically on connect, so
`message:received` and `notification:new` need no subscription message.

## Target connection model

All three clients should converge on the same architecture:

1. **One WebSocket connection per running app.** Chat events, message
   notifications, and `notification:new` all share it. Do not open one
   connection per screen or per namespace.
2. **Open after login, close on logout.** On mobile, additionally close when
   the app enters the background and reopen on foreground (the iOS app
   already does this; keep that behavior).
3. **Join only visible chats.** Send `chat:join` when a conversation opens
   and `chat:leave` when it closes. Messages for _all_ chats still arrive via
   `message:received` on the personal room; the room join only adds
   `message:new`, `chat:typing`, and `message:read` receipts for the open
   conversation. Send `chat:typing` only for the currently open chat.
4. **Reconnect yourself.** Bounded exponential backoff with jitter
   (e.g. 1 s → 2 s → 4 s → … capped at 30 s). After every reconnect:
   re-send `chat:join` for the chats currently on screen, then catch up
   missed messages over REST (`GET /chats/{chatId}/messages`,
   `GET /chats/unread-count`, `GET /notifications/unread-count`).
5. **Handle close codes.** On 1008 (`unauthorized`) or 4401
   (`token expired`): refresh the access token first, then reconnect. Do not
   retry in a tight loop with the same token.
6. **Keep the REST fallback.** Sending messages over REST
   (`POST /chats/{chatId}/messages`) while disconnected remains valid; the
   server emits the same WS events for REST-created messages.

## Protocol essentials

Connect:

```text
wss://api.skipperclub.app/v1/ws/chat
Authorization: Bearer <access-token>        (native clients)
wss://…/v1/ws/chat?token=<access-token>     (browsers)
```

Every frame, both directions:

```json
{ "event": "message:send", "data": { "chatId": "<uuid-v7>", "text": "Ahoy!" } }
```

Rules that differ from Socket.IO habits:

- There is no `connect` event; the connection is usable as soon as the
  upgrade completes. Auth failure surfaces as a close (1008), not
  `connect_error`.
- There are no callback acks. Confirmations are ordinary events
  (`message:sent`, `chat:joined`, …) on the same connection.
- Unknown inbound event names are silently ignored by the server.
- IDs must be UUIDv7; `message:send` text is 1–1000 Unicode characters.
- Timestamps are RFC 3339 / ISO 8601 in UTC. Fractional seconds may be
  present or absent — parsers must accept both (`2026-07-10T12:00:00Z` and
  `2026-07-10T12:00:00.123456789Z`).
- Ping/pong is handled by the WebSocket protocol layer; every standard client
  answers automatically as long as the app keeps reading from the socket.

## Web (Next.js)

Current state: `socket.io-client` with **two** namespace connections built in
`lib/websocket/chat-socket.ts` and `lib/websocket/notification-socket.ts`,
providers mounted per page, token fetched from the BFF route
`/api/auth/ws-token` (which transparently refreshes the cookie-held JWT and
returns `{ token, websocketUrl }`).

Migration steps:

1. Remove the `socket.io-client` dependency. Replace both factories with one
   native `WebSocket` wrapper:

   ```ts
   const { token, websocketUrl } = await fetchWsCredentials(); // keep the BFF route
   const ws = new WebSocket(
     `${websocketUrl}/v1/ws/chat?token=${encodeURIComponent(token)}`,
   );
   ```

   Browsers cannot set upgrade headers, so the query parameter is the
   supported browser mechanism. Keep delivering the URL and token via
   `/api/auth/ws-token` — it already refreshes the access token server-side,
   which is exactly what a reconnect needs. Update `WEBSOCKET_URL` handling:
   the value must now be the API origin (the client appends `/v1/ws/chat`,
   not `/chat`).

2. **Merge the two providers into one** app-level provider (mounted once in
   the authenticated layout, not per page — today every navigation tears down
   and re-opens both sockets). `notification:new` arrives on the same
   connection; expose the same `useChatSocketEvents` /
   `useNotificationSocketEvents` hook APIs on top of it so consuming
   components (`chat-view`, `chat-list`, `notifications-list`, both
   unread-count hooks) do not change.
3. Replace `socket.emit(event, data)` with
   `ws.send(JSON.stringify({ event, data }))` and the per-event `socket.on`
   listeners with one `message` listener that dispatches on `frame.event`.
4. Implement reconnection (the library no longer does it): exponential
   backoff with jitter, re-fetch `/api/auth/ws-token` before every attempt
   (replaces the `connect_error`-string-sniffing JWT refresh), and re-send
   `chat:join` for the rooms tracked in `joinedRoomsRef`.
5. Handle close codes: `ws.onclose` with `event.code === 4401 || 1008` →
   refresh credentials, then reconnect immediately; otherwise back off.
6. Drop the polling-fallback expectations from tests; there is no
   long-polling transport anymore.
7. Optional but recommended: pause reconnection attempts while
   `document.visibilityState === "hidden"` and reconnect on `visibilitychange`.

## Android

Current state: `io.socket:socket.io-client` 2.1.2 in
`data/ChatRealtimeClient.kt`, socket scoped to the Messages tab
(`MessagesScreen`), namespace `/chat`, auth via the Socket.IO `auth` object,
only `chat:join`/`chat:leave` emitted; sends and read receipts go over REST;
listens to `message:new`/`message:received` with a REST poll fallback.

Migration steps:

1. Replace the Socket.IO dependency with **OkHttp's WebSocket** (OkHttp is
   already on the classpath): `OkHttpClient.newWebSocket(request, listener)`
   with `Request.Builder().url("$API_BASE_URL/v1/ws/chat".toWs())
.addHeader("Authorization", "Bearer $accessToken")`. Note the endpoint is
   `/v1/ws/chat`, not the `/chat` namespace; the scheme must be `wss://`
   (`https://` base URL swapped to `wss://`).
2. Keep the `ChatRealtimeClient` interface and `ChatRealtimeEvent` flow —
   only the implementation changes. Serialize frames with the existing
   lenient `Json` instance:
   `{"event":"chat:join","data":{"chatId":"…"}}`; parse inbound frames by
   `event` and reuse `parseRealtimeChatMessage` for the message payloads.
3. Implement reconnection with exponential backoff (Socket.IO's
   `setReconnection(true)` is gone). On every (re)open: re-emit `chat:join`
   for `joinedChatIds` — the existing rejoin-on-connect logic carries over
   as-is. The existing REST poll while disconnected remains the right
   fallback.
4. Fetch a fresh token per connection attempt: call
   `SessionStore.validSession()` (which refreshes when near expiry) inside
   the connect/reconnect path instead of capturing the token once. On close
   code 4401 or 1008, force a session refresh before reconnecting.
5. Move the connection scope up: from the Messages-tab composition to an
   app-scoped, foreground-aware owner (e.g. a singleton observing
   `ProcessLifecycleOwner`): connect when logged-in-and-foregrounded,
   disconnect in background. This is what makes `notification:new` and
   chat-list badge updates work outside the Messages tab. If the team wants a
   minimal first step, keeping the current tab scope also works — the
   endpoint does not force the larger refactor.
6. `message:send`/`message:read` may stay on REST (current behavior); the
   server emits the same events either way. Adopting WS sends is optional
   and can come later.

## iOS

Current state: `Socket.IO-Client-Swift` in `Core/Network/WebSocketManager.swift`,
namespace `/chat`, socket opened lazily per conversation and torn down on
background via `AppLifecycleObserver`, token via `connectParams` + header,
full event usage (join/leave/send/read/typing, presence), `ChatPollingService`
REST fallback, notifications entirely on APNs.

Migration steps:

1. Replace `SocketManager`/`SocketIOClient` with **`URLSessionWebSocketTask`**
   (no third-party dependency needed; Starscream goes away with Socket.IO):

   ```swift
   var request = URLRequest(url: URL(string: "wss://api.skipperclub.app/v1/ws/chat")!)
   request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
   let task = session.webSocketTask(with: request)
   task.resume()
   ```

   Configuration: `WEBSOCKET_BASE_URL` must switch to a `wss://` URL and
   `WEBSOCKET_NAMESPACE` disappears — the path is `/v1/ws/chat`.

2. Keep `WebSocketManagerProtocol`, the handler-token registry, and
   `activeChats` — only the transport inside `WebSocketManager` changes.
   Replace `socket.emit(event, dict)` with an encoded
   `{"event":…,"data":…}` text frame, and the per-event `socket.on`
   registrations with a receive loop (`task.receive`) that decodes the
   envelope and dispatches on `event`. The existing `Codable` outgoing
   structs in `WebSocketEvent.swift` (currently dead code) become useful:
   encode them as the `data` member instead of `[String: Any]` dictionaries.
3. Date decoding: keep the fractional-seconds ISO 8601 formatter but accept
   timestamps **without** fractional seconds too (Go omits them when zero) —
   e.g. try `.iso8601withFractionalSeconds` then plain `.iso8601`.
4. There is no `.connect` client event: the connection is usable once the
   task reports open (`urlSession(_:webSocketTask:didOpenWithProtocol:)`).
   Rejoin `activeChats` there — same logic as today's `.connect` handler.
   Map `didCloseWith closeCode:` to the reconnect policy: 4401/1008
   (`.policyViolation`) → refresh token via the existing auth manager, then
   reconnect; anything else → exponential backoff (replaces
   `.reconnects(true)`).
5. Keep `AppLifecycleObserver` exactly as is (disconnect on background,
   reconnect on foreground) — it already matches the target model. Keep
   `ChatPollingService` as the disconnected fallback.
6. Recommended follow-up: hold the connection app-wide while foregrounded
   (not per conversation) so `notification:new` can drive in-app badge
   updates without REST polling; APNs remains the background channel.
7. Pings: `URLSessionWebSocketTask` answers server pings automatically while
   a `receive` call is pending. The client-side `sendPing` timer is optional
   and may be dropped.

## Rollout checklist (per client)

- [ ] Connects with `Authorization` header (native) or `?token=` (web) and
      receives `notification:new` without any subscription message.
- [ ] `chat:join` → `chat:joined` for an accessible chat; `error`
      (`Chat not found or access denied`) for a foreign chat id.
- [ ] `message:send` → `message:sent` ack, `message:new` in the open room,
      `message:received` on other participants' connections.
- [ ] Typing indicator round-trip; sender never receives its own
      `chat:typing` back.
- [ ] `presence:update` arrives for chat partners going online/offline, and
      never for users the account shares no chat with.
- [ ] Reconnect after airplane mode: backoff, token refresh, rejoin, REST
      catch-up, no duplicate messages in the UI.
- [ ] Access-token expiry mid-connection: connection closes with 4401, client
      refreshes and reconnects without user-visible errors.
- [ ] Burst protection: a runaway loop emitting >10 events/s sees dropped
      frames and one `error` event — the app must not treat that as fatal.
- [ ] No token in any log line (URLs with `?token=` are never logged).

## Related

- [WebSocket Events](./websocket.md) — full protocol reference
- [AsyncAPI specification](../../api/asyncapi.yaml)
- [ADR-0001 §2.3](../adr/0001-architecture.md) — why Socket.IO was dropped
