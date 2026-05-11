# Chat Types

This document describes all available chat type enum values used in the messaging system.

## Overview

The `ChatType` enum defines the different types of chat conversations supported by the SkipperClub messaging system. Each chat type has specific rules for creation, participants, and access control.

## Available Chat Types

### `ONE_TO_ONE`

**Private Chat**  
Direct messaging between exactly two users. The system ensures only one private chat exists between any two users — creating a chat with an existing participant returns the existing chat rather than creating a duplicate.

**Characteristics:**

- Exactly 2 participants
- No friendship relationship is required between participants
- Created on demand or when first message is sent
- Reuses existing chat if one already exists between the users
- Cannot be named (name is null)

### `GROUP`

**Group Chat**  
Multi-user conversations with a custom name. Unlike private chats, each group chat creation produces a new chat, even with the same participants. Users can create multiple group chats with overlapping membership.

**Characteristics:**

- 2+ participants
- No friendship relationship is required between participants
- Requires a name
- Multiple group chats can exist with same participants
- Creator defines initial participants

### `CRUISE_QNA`

**Cruise Q&A Chat**  
A dedicated 1:1 channel where users can ask questions about a cruise directly to the organizer before joining. Each user who wants to ask questions gets their own private Q&A chat with the organizer.

**Characteristics:**

- Exactly 2 participants (user + organizer)
- Created lazily when first message is sent
- One Q&A chat per user per cruise
- Organizer cannot initiate (uses general messages API to respond)
- Linked to cruise via `relatedCruiseId`
- Persists even after cruise deletion

### `CRUISE_GROUP`

**Cruise Group Chat**  
A shared chat for all accepted cruise participants. Created automatically when the cruise is set up. All participants with `accepted` status can read and send messages.

**Characteristics:**

- Organizer + all accepted participants
- Created automatically when cruise is created
- One group chat per cruise
- Participants added/removed based on cruise participation state
- Linked to cruise via `relatedCruiseId`
- Persists even after cruise deletion
- Messages from removed participants remain visible

## Quick Reference Table

| Type           | Participants         | Created         | Unique Per         |
| -------------- | -------------------- | --------------- | ------------------ |
| `ONE_TO_ONE`   | 2 users              | On demand       | User pair          |
| `GROUP`        | 2+ users             | On demand       | Never (always new) |
| `CRUISE_QNA`   | User + Organizer     | First message   | User + Cruise      |
| `CRUISE_GROUP` | Organizer + Accepted | Cruise creation | Cruise             |

## Access Rules

| Chat Type      | Who Can Access                    | Who Can Send Messages             |
| -------------- | --------------------------------- | --------------------------------- |
| `ONE_TO_ONE`   | Both participants                 | Both participants                 |
| `GROUP`        | All participants                  | All participants                  |
| `CRUISE_QNA`   | User and organizer                | User and organizer                |
| `CRUISE_GROUP` | Organizer + accepted participants | Organizer + accepted participants |

## Data Model (API Response)

The following interface represents the **API response** format, not the database schema. Some fields are computed at request time.

```typescript
interface ChatResponse {
  id: string; // UUID v7
  type: ChatType; // ONE_TO_ONE, GROUP, CRUISE_QNA, CRUISE_GROUP
  name: string | null; // Required for GROUP, optional for others
  participants: User[]; // List of participants
  lastMessage: Message | null; // Most recent message
  lastReadMessageId: string | null; // Computed from UserChatState
  relatedCruiseId: string | null; // For CRUISE_* types
  unreadCount: number; // Computed from UserChatState
  updatedAt: string; // Last activity timestamp
}
```

**Note:** `lastReadMessageId` and `unreadCount` are user-specific fields computed from the `UserChatState` collection (MongoDB). They are not stored directly in the `Chat` schema but are joined at query time to provide per-user read state.

## Usage Notes

- `ONE_TO_ONE` chats are idempotent — creating with same participant returns existing chat
- `GROUP` chats always create new chat, even with identical participants
- `CRUISE_QNA` is for users asking questions TO the organizer
- `CRUISE_GROUP` membership is managed automatically based on participant state
- Hidden chats (soft-deleted) reappear when new messages arrive

## Related

- [Messages API](../../messages/index.md) — Full messaging documentation
- [Cruise Chat Flows](../flows/cruise-chat-flows.md) — Detailed chat architecture
