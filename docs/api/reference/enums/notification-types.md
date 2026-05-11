# Notification Types

This document describes all notification-related enum values used in the notification system.

## Overview

The notification system uses two enums:

- `NotificationSourceType` — The module that generated the notification
- `NotificationEventType` — The specific event that triggered the notification

**Key principle**: Users never receive notifications for their own actions.

## Source Types

The `NotificationSourceType` enum identifies which module generated a notification.

| Type      | Description                                         |
| --------- | --------------------------------------------------- |
| `CRUISE`  | Cruise-related events (invitations, joins, updates) |
| `POST`    | Social post events (likes, comments)                |
| `MESSAGE` | Messaging events (future)                           |
| `REVIEW`  | Review system events                                |
| `MEDIA`   | Media events (future)                               |
| `FRIEND`  | Friend request events                               |

## Event Types

The `NotificationEventType` enum defines the 17 specific events that trigger notifications.

### Cruise Events (10 types)

| Event                        | Recipient                | Description                                     |
| ---------------------------- | ------------------------ | ----------------------------------------------- |
| `CRUISE_INVITATION_SENT`     | Invited user             | Organizer sent an invitation to join cruise     |
| `CRUISE_REQUEST_PENDING`     | Cruise organizer         | User requested to join the cruise               |
| `CRUISE_REQUEST_ACCEPTED`    | User who requested       | Organizer accepted the join request             |
| `CRUISE_INVITATION_ACCEPTED` | Cruise organizer         | User accepted the invitation                    |
| `CRUISE_PARTICIPANT_JOINED`  | Other participants       | User joined the cruise (notification to others) |
| `CRUISE_REQUEST_REJECTED`    | Requester                | Organizer rejected the join request             |
| `CRUISE_PARTICIPANT_LEFT`    | Organizer + participants | User left the cruise voluntarily                |
| `CRUISE_PARTICIPANT_REMOVED` | Removed user             | Organizer removed the user from cruise          |
| `CRUISE_DETAILS_CHANGED`     | Accepted participants    | Cruise details were updated                     |
| `CRUISE_REVIEW_REMINDER`     | Each participant         | Post-cruise reminder to review fellow crew      |

### Post Events (2 types)

| Event            | Recipient   | Description                   |
| ---------------- | ----------- | ----------------------------- |
| `POST_REACTED`   | Post author | Someone reacted to the post   |
| `POST_COMMENTED` | Post author | Someone commented on the post |

**Note**: No notification is sent if user reacts to/comments on their own post.

### Friend Events (3 types)

| Event                     | Recipient        | Description                   |
| ------------------------- | ---------------- | ----------------------------- |
| `FRIEND_REQUEST_SENT`     | Request receiver | Someone sent a friend request |
| `FRIEND_REQUEST_ACCEPTED` | Original sender  | Friend request was accepted   |
| `FRIEND_REQUEST_REJECTED` | Original sender  | Friend request was rejected   |

### Review Events (2 types)

| Event                     | Recipient     | Description                                |
| ------------------------- | ------------- | ------------------------------------------ |
| `REVIEW_PENDING_RECEIVED` | Reviewed user | Someone reviewed them (pending reciprocal) |
| `REVIEW_PUBLISHED`        | Both users    | Reciprocal review submitted, now visible   |

## Notification Status

| Status   | Description                      |
| -------- | -------------------------------- |
| `UNREAD` | New notification, not yet viewed |
| `READ`   | User has viewed the notification |

**Note**: Deleted notifications are soft-deleted and not returned by the API.

## Quick Reference Table

| Module     | Event Count | Events                                                                                                                                                                                 |
| ---------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Cruise** | 10          | INVITATION_SENT, REQUEST_PENDING, REQUEST_ACCEPTED, INVITATION_ACCEPTED, PARTICIPANT_JOINED, REQUEST_REJECTED, PARTICIPANT_LEFT, PARTICIPANT_REMOVED, DETAILS_CHANGED, REVIEW_REMINDER |
| **Post**   | 2           | REACTED, COMMENTED                                                                                                                                                                     |
| **Friend** | 3           | REQUEST_SENT, REQUEST_ACCEPTED, REQUEST_REJECTED                                                                                                                                       |
| **Review** | 2           | PENDING_RECEIVED, PUBLISHED                                                                                                                                                            |
| **Total**  | 17          | —                                                                                                                                                                                      |

## Data Model

```typescript
interface Notification {
  id: string; // UUID v7
  recipientId: string; // UUID v7 of the recipient
  eventType: NotificationEventType; // Event that triggered notification
  sourceType: NotificationSourceType; // Source module
  sourceId: string; // ID of source object (e.g., cruise ID)
  relationId: string | null; // ID of related user (e.g., actor)
  status: NotificationStatus; // UNREAD or READ
  metadata: object | null; // Context data for rendering
  createdAt: string; // ISO 8601 timestamp
  readAt: string | null; // When marked as read
}
```

## Understanding sourceId and relationId

| Event Type                   | sourceId          | relationId                     |
| ---------------------------- | ----------------- | ------------------------------ |
| `CRUISE_INVITATION_SENT`     | Cruise ID         | Inviter (organizer)            |
| `CRUISE_REQUEST_PENDING`     | Cruise ID         | Requester                      |
| `CRUISE_REQUEST_ACCEPTED`    | Cruise ID         | Actor (organizer who accepted) |
| `CRUISE_INVITATION_ACCEPTED` | Cruise ID         | User who accepted invitation   |
| `CRUISE_PARTICIPANT_JOINED`  | Cruise ID         | User who joined                |
| `CRUISE_REQUEST_REJECTED`    | Cruise ID         | Rejecter (organizer)           |
| `CRUISE_PARTICIPANT_LEFT`    | Cruise ID         | User who left                  |
| `CRUISE_PARTICIPANT_REMOVED` | Cruise ID         | Remover (organizer)            |
| `CRUISE_REVIEW_REMINDER`     | Cruise ID         | `null` (system-generated)      |
| `POST_REACTED`               | Post ID           | User who reacted               |
| `POST_COMMENTED`             | Post ID           | User who commented             |
| `FRIEND_REQUEST_*`           | Friend request ID | Other user                     |
| `REVIEW_*`                   | Review ID         | Reviewer                       |

## Metadata by Event Type

Each notification includes a `metadata` field with context-specific data for rendering. The table below shows the metadata structure for all 17 event types.

Most event types include `actorName` — the display name of the user who triggered the notification. The exception is `CRUISE_REVIEW_REMINDER`, which is system-generated and has no actor.

| Event Type                   | Metadata                                              |
| ---------------------------- | ----------------------------------------------------- |
| `CRUISE_INVITATION_SENT`     | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_REQUEST_PENDING`     | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_REQUEST_ACCEPTED`    | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_INVITATION_ACCEPTED` | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_PARTICIPANT_JOINED`  | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_REQUEST_REJECTED`    | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_PARTICIPANT_LEFT`    | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_PARTICIPANT_REMOVED` | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_DETAILS_CHANGED`     | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `CRUISE_REVIEW_REMINDER`     | `{ "cruiseTitle": "string" }`                         |
| `POST_REACTED`               | `{ "actorName": "string", "reactionType": "string" }` |
| `POST_COMMENTED`             | `{ "commentText": "string", "actorName": "string" }`  |
| `FRIEND_REQUEST_SENT`        | `{ "actorName": "string" }`                           |
| `FRIEND_REQUEST_ACCEPTED`    | `{ "actorName": "string" }`                           |
| `FRIEND_REQUEST_REJECTED`    | `{ "actorName": "string" }`                           |
| `REVIEW_PENDING_RECEIVED`    | `{ "cruiseTitle": "string", "actorName": "string" }`  |
| `REVIEW_PUBLISHED`           | `{ "cruiseTitle": "string", "actorName": "string" }`  |

## Metadata Examples

### Cruise Events (9 types)

All cruise events include the cruise title and actor name for display purposes.

```json
{
  "cruiseTitle": "Mediterranean Adventure",
  "actorName": "John Smith"
}
```

### Post Events (2 types)

```json
// POST_REACTED - includes the name of the user who reacted and the reaction type
{
  "actorName": "Jane Doe",
  "reactionType": "heart"
}

// POST_COMMENTED - includes truncated comment text (max 100 characters) and commenter name
{
  "commentText": "Great sailing trip! I'd love to join next time...",
  "actorName": "Jane Doe"
}
```

### Friend Events (3 types)

All friend events include the actor name. The `relationId` field contains the other user's ID for fetching their full profile if needed.

```json
{
  "actorName": "Jane Doe"
}
```

### Review Events (2 types)

Review events include the cruise title and reviewer name.

```json
{
  "cruiseTitle": "Mediterranean Adventure",
  "actorName": "John Smith"
}
```

## Related

- [Notifications API](../../notifications/index.md) — Full notifications documentation
- [Notification Flows](../flows/notification-flows.md) — When notifications are triggered
