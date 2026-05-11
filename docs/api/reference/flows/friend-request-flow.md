# Friend Request Flow

This document describes the state machine for friend requests in the SkipperClub platform.

## Overview

The friend request system allows users to connect with each other. The flow is straightforward:

1. User A sends a friend request to User B
2. User B accepts or rejects the request
3. If accepted, both users become friends

## Friend Request States

### From Sender's Perspective

| State  | Description                                    |
| ------ | ---------------------------------------------- |
| `sent` | Request sent, waiting for recipient's response |

### From Recipient's Perspective

| State     | Description                                 |
| --------- | ------------------------------------------- |
| `pending` | Request received, waiting for your response |

### After Action

| State      | Description                                 |
| ---------- | ------------------------------------------- |
| `accepted` | Request was accepted, users are now friends |
| `rejected` | Request was declined                        |
| `canceled` | Request was canceled by sender              |

## State Diagram

```mermaid
stateDiagram-v2
    [*] --> pending_sent: User A sends request

    state pending_sent {
        [*] --> sender_view: Sender sees
        [*] --> receiver_view: Receiver sees
        sender_view: sent
        receiver_view: pending
    }

    pending_sent --> accepted: User B accepts
    pending_sent --> rejected: User B rejects
    pending_sent --> canceled: User A cancels

    accepted --> friendship: Both are friends
    friendship --> [*]: Friend removed

    rejected --> [*]
    canceled --> [*]

    classDef initialState fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF,stroke-width:2px
    classDef acceptedState fill:#10B981,stroke:#047857,color:#FFFFFF,stroke-width:2px
    classDef rejectedState fill:#F59E0B,stroke:#B45309,color:#000000,stroke-width:2px
    classDef canceledState fill:#EF4444,stroke:#B91C1C,color:#FFFFFF,stroke-width:2px

    class pending_sent initialState
    class accepted,friendship acceptedState
    class rejected rejectedState
    class canceled canceledState
```

## Flow Diagram

```mermaid
flowchart TB
    A[User A sends request]:::trigger --> B[Request created<br/>state: pending/sent]:::state
    B --> C{User B decision}:::decision
    B --> H{User A cancels?}:::decision
    C -->|Accept| D[Friendship created]:::success
    C -->|Reject| E[Request marked rejected]:::negative
    H -->|Cancel| I[Request canceled]:::canceled

    D --> F[Both users are friends]:::success
    E --> G[No friendship]:::state
    I --> G

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef decision fill:#8B5CF6,stroke:#5B21B6,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
    classDef negative fill:#F59E0B,stroke:#B45309,color:#000000
    classDef canceled fill:#EF4444,stroke:#B91C1C,color:#FFFFFF
```

## Sequence Diagram - Successful Request

```mermaid
sequenceDiagram
    participant UserA as User A
    participant API
    participant UserB as User B

    UserA->>API: POST /friend-requests { userId: B }
    API-->>UserA: 201 Created { state: "sent" }
    Note over API: Notification sent to User B

    UserB->>API: GET /friend-requests?state=pending
    API-->>UserB: { requests: [{ state: "pending", user: A }] }

    UserB->>API: PATCH /friend-requests/{id} { state: "accepted" }
    API-->>UserB: 200 OK { state: "accepted" }
    Note over API: Notification sent to User A

    Note over UserA,UserB: Both users are now friends

    UserA->>API: GET /friends
    API-->>UserA: { friends: [User B, ...] }

    UserB->>API: GET /friends
    API-->>UserB: { friends: [User A, ...] }
```

## State Transitions

### Available Actions

| Current State    | Action | Who Can Perform | Result             |
| ---------------- | ------ | --------------- | ------------------ |
| `pending`/`sent` | Accept | Recipient only  | Friendship created |
| `pending`/`sent` | Reject | Recipient only  | Request rejected   |
| `pending`/`sent` | Cancel | Sender only     | Request canceled   |

### Authorization Rules

| Action          | Sender       | Recipient                          |
| --------------- | ------------ | ---------------------------------- |
| Cancel (DELETE) | ✅ Allowed   | ❌ Forbidden (use PATCH to reject) |
| Accept (PATCH)  | ❌ Forbidden | ✅ Allowed                         |
| Reject (PATCH)  | ❌ Forbidden | ✅ Allowed                         |

## Notifications

| Event            | Recipient | Notification Type         |
| ---------------- | --------- | ------------------------- |
| Request sent     | User B    | `FRIEND_REQUEST_SENT`     |
| Request accepted | User A    | `FRIEND_REQUEST_ACCEPTED` |
| Request rejected | User A    | `FRIEND_REQUEST_REJECTED` |
| Request canceled | —         | No notification           |

## Error Scenarios

| Error                                     | Status | Description                     |
| ----------------------------------------- | ------ | ------------------------------- |
| `/errors/target-user-not-found`           | 404    | Target user doesn't exist       |
| `/errors/friend-request-not-found`        | 404    | Request doesn't exist           |
| `/errors/friend-request-access-forbidden` | 403    | Cannot access this request      |
| `/errors/cannot-friend-yourself`          | 422    | Cannot send request to yourself |
| `/errors/users-already-friends`           | 422    | Already friends                 |
| `/errors/friend-request-already-exists`   | 422    | Request already exists          |
| `/errors/invalid-friend-request-state`    | 422    | Invalid state transition        |

## Removing Friends

After becoming friends, either user can remove the friendship:

```mermaid
flowchart LR
    A[Friends]:::success --> B["DELETE /friends/:userId"]:::trigger
    B --> C[Friendship removed]:::state
    C --> D[Both users no longer friends]:::state

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

**Note**: Removing a friend does not notify the other user.

## Related

- [Friends API](../../friends/index.md) — Full friends documentation
- [Notification Flows](./notification-flows.md) — When notifications are triggered
