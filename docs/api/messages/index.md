# Messages

The messaging API provides real-time communication between users through chats. It supports private conversations, group chats, and cruise-related communication channels.

## Overview

SkipperClub's messaging system enables users to communicate through various chat types, each designed for specific use cases. The system supports both REST API for standard operations and WebSocket for real-time messaging.

**Key features**:

- Private 1:1 conversations between users
- Group chats for multiple participants
- Cruise-specific channels for Q&A and group discussions
- Real-time messaging via WebSocket
- Typing indicators and read receipts
- User presence (online/offline status)

## Documentation

- [Chats REST API](./chats.md) — Chat management, messages, read status
- [WebSocket Events](./websocket.md) — Real-time messaging and presence
- [Socket.IO to WebSocket Migration](./socketio-to-websocket-migration.md) — Client migration guide (web, Android, iOS)

## Chat Types

| Type         | Code           | Description                                        |
| ------------ | -------------- | -------------------------------------------------- |
| Private      | `ONE_TO_ONE`   | Direct conversation between two users              |
| Group        | `GROUP`        | Multi-user chat with custom name                   |
| Cruise Q&A   | `CRUISE_QNA`   | Questions from potential participants to organizer |
| Cruise Group | `CRUISE_GROUP` | Group chat for accepted cruise participants        |

### Private Chats (ONE_TO_ONE)

Direct messaging between two users. Friendship between participants is not required. The system ensures only one private chat exists between any two users — creating a chat with an existing participant returns the existing chat.

### Group Chats (GROUP)

Multi-user conversations with a custom name. Friendship between participants is not required. Unlike private chats, each group chat creation produces a new chat, even with the same participants.

### Cruise Q&A (CRUISE_QNA)

A dedicated channel where users can ask questions about a cruise before joining. Only the organizer and the user asking questions can see the conversation — other participants cannot read these private Q&A threads.

### Cruise Group (CRUISE_GROUP)

A shared chat for all accepted cruise participants. Created automatically when the cruise is set up. All participants with `accepted` status can read and send messages.

## Endpoints Summary

| Method | Endpoint                               | Description                   |
| ------ | -------------------------------------- | ----------------------------- |
| GET    | `/chats`                               | List user's chats             |
| POST   | `/chats`                               | Create new chat               |
| GET    | `/chats/{chatId}`                      | Get chat metadata             |
| DELETE | `/chats/{chatId}`                      | Hide chat from list           |
| GET    | `/chats/{chatId}/messages`             | List chat messages            |
| POST   | `/chats/{chatId}/messages`             | Send message                  |
| PATCH  | `/chats/{chatId}/messages/{messageId}` | Update message (mark as read) |
| GET    | `/chats/unread-count`                  | Get total unread count        |
| POST   | `/chats/actions`                       | Perform bulk actions on chats |

## WebSocket Events Summary

| Event              | Direction       | Description                                                |
| ------------------ | --------------- | ---------------------------------------------------------- |
| `chat:join`        | Client → Server | Subscribe to a chat room's updates                         |
| `chat:leave`       | Client → Server | Unsubscribe from a chat room                               |
| `message:send`     | Client → Server | Send message in real-time                                  |
| `message:read`     | Client → Server | Mark message as read                                       |
| `chat:typing`      | Client → Server | Send typing indicator                                      |
| `message:new`      | Server → Client | New message — delivered to the joined chat room only       |
| `message:received` | Server → Client | New-message notification — personal room, all user's chats |
| `message:read`     | Server → Client | Read receipt — delivered to the joined chat room           |
| `chat:typing`      | Server → Client | Receive typing indicator                                   |
| `presence:update`  | Server → Client | User online/offline status                                 |
| `notification:new` | Server → Client | New notification — personal room                           |

Server-to-client message events are transport-independent: a message created
over REST (`POST /chats/{chatId}/messages`) triggers exactly the same
`message:new` / `message:received` fan-out as one sent over WS
`message:send`, and `PATCH …/messages/{messageId}` with `read: true`
broadcasts the same `message:read` receipt as WS `message:read`. Bulk
mark-read (`POST /chats/actions`) broadcasts one `message:read` receipt per
chat where anything was newly marked. See
[Transport parity](./websocket.md#transport-parity-rest--websocket).

## Key Concepts

### Chat Hiding

When a user "deletes" a chat via `DELETE /chats/{chatId}`, the chat is hidden from their list — not permanently deleted. This Messenger-like behavior means:

1. The chat disappears from the user's chat list
2. Old messages become invisible to that user
3. When someone sends a new message, the chat reappears
4. Only new messages (after hiding) are visible

See [Chat Hiding Behavior](./chats.md#chat-hiding-behavior) for details.

### Read Status

Each message tracks read status per user. Messages can be marked as read individually or in bulk:

- `PATCH /chats/{chatId}/messages/{messageId}` with `{"read": true}` — single message
- `POST /chats/actions` with `{"action": "mark-read", "chatIds": [...]}` — all messages in one or more chats

Both paths broadcast `message:read` receipts with cascading semantics: a
receipt for message M means the reader has read M and every earlier message
in that chat. See
[Read receipts are cascading](./websocket.md#read-receipts-are-cascading).

### Unread Count

The system maintains unread message counts:

- Per-chat count available in chat list response (`unreadCount` field)
- Total unread across all chats via `GET /chats/unread-count`

### Real-time Updates

For live messaging experience, connect via WebSocket to receive:

- New messages as they arrive
- Typing indicators from other participants
- Read receipts when others view messages
- Online/offline presence updates

See [WebSocket Events](./websocket.md) for connection setup and event handling.

## Data Model

### Chat

```typescript
interface Chat {
  id: string; // UUID v7
  type: ChatType; // ONE_TO_ONE, GROUP, CRUISE_QNA, CRUISE_GROUP
  name: string | null; // Chat name (required for GROUP)
  participants: User[]; // List of participants
  lastMessage: Message | null; // Most recent message
  lastReadMessageId: string | null; // Last read message for current user
  relatedCruiseId: string | null; // Linked cruise (for CRUISE_* types)
  unreadCount: number; // Unread messages for current user
  updatedAt: string; // Last activity timestamp
}
```

### Message

```typescript
interface Message {
  id: string; // UUID v7
  chatId: string; // Parent chat ID
  text: string; // Message content
  read: boolean; // Read by current user
  createdAt: string; // ISO 8601 timestamp
  updatedAt: string; // ISO 8601 timestamp
  user: {
    id: string; // Sender's user ID
    name: string; // Sender's display name
    avatarUrl: string | null; // Sender's avatar URL
  };
}
```

## Quick Start

### Create a Private Chat

```http
POST /v1/chats HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "participantIds": ["018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e"]
}
```

### Send a Message

```http
POST /v1/chats/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99/messages HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "text": "Hello! Ready for the sailing trip?"
}
```

### Connect via WebSocket

```javascript
const socket = new WebSocket(
  `wss://api.skipperclub.app/v1/ws/chat?token=${encodeURIComponent(token)}`,
);

socket.addEventListener("open", () => {
  socket.send(
    JSON.stringify({
      event: "chat:join",
      data: { chatId: "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99" },
    }),
  );
});

socket.addEventListener("message", ({ data }) => {
  const frame = JSON.parse(data);
  if (frame.event === "message:new") console.log(frame.data);
});
```

---

## Related

- [Chats REST API](./chats.md) — Full REST API documentation
- [WebSocket Events](./websocket.md) — Real-time messaging
- [Cruises](../cruises/index.md) — Cruise chats (Q&A, Group)
- [Authentication](../getting-started/authentication.md) — JWT tokens
- [Error Handling](../getting-started/errors.md) — RFC 7807 format
