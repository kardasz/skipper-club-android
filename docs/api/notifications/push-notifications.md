# Push Notifications (iOS/Android)

This document describes native mobile push notifications in SkipperClub.

## Scope

MVP push covers events that already create records in `notifications` (all current `NotificationEventType` values produced by notification listeners).

Out of scope for this phase:

- chat/message push channel
- per-event push preferences
- silent/data-only notifications
- open/delivery analytics UI

## User Notification Settings Dependency

Push delivery is also gated by user notification settings.

- Push is delivered only when `pushNotificationsEnabled` is set to `true`.
- This preference is managed via `PUT /v1/profile/notification-settings`.
- In-app/WebSocket notifications are not affected by this toggle.

## Architecture Flow

Push does not replace existing notification behavior. Current semantics stay unchanged:

1. Domain listener creates `notifications` row in Postgres.
2. Listener emits WebSocket `notification:new` immediately.
3. Listener publishes `NotificationCreatedEvent(notificationId)`.
4. `PushNotificationListener` enqueues BullMQ job on `push` queue (`jobId = notificationId`).
5. Worker loads notification and recipient tokens, localizes payload, sends via APNs/FCM (when enabled and configured), saves per-token result in `push_delivery_logs`.

Push is asynchronous and best-effort. Push failures never fail originating business commands.

## Data Model

### `push_device_tokens`

- stores registered mobile device tokens
- unique constraints:
  - `(user_id, device_id)`
  - `(platform, token)`
- active token means global push opt-in for MVP

Key fields:

- `id`, `user_id`, `device_id`, `platform` (`IOS|ANDROID`), `token`
- `language` (`en|pl`, optional)
- `is_active`, `last_seen_at`
- `deactivated_at`, `deactivation_reason`

### `push_delivery_logs`

- stores delivery outcome for each `(notification, device token)` pair
- unique constraint: `(notification_id, device_token_id)`

Key fields:

- `provider` (`APNS|FCM`)
- `status` (`SENT|FAILED|SKIPPED`)
- `provider_message_id`, `error_code`, `error_message`, `attempt`

## API Endpoints

All endpoints require JWT authentication.

### Register or refresh token

```http
POST /v1/push/tokens
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "deviceId": "ios-installation-123",
  "platform": "IOS",
  "token": "native-token-12345678901234567890",
  "language": "pl"
}
```

Behavior:

- upsert by `(userId, deviceId)`
- if token is already assigned to another user, token ownership is rebound to current user
- endpoint is throttled

Response:

- `204 No Content`

### Unregister by device ID

```http
DELETE /v1/push/tokens/{deviceId}
Authorization: Bearer <token>
```

Behavior:

- deactivates current user's token for device
- idempotent

Response:

- `204 No Content`

### Unregister by `{platform, token}` (safety endpoint)

```http
POST /v1/push/tokens/unregister
Content-Type: application/json
Authorization: Bearer <token>
```

```json
{
  "platform": "ANDROID",
  "token": "native-token-12345678901234567890"
}
```

Behavior:

- use when `deviceId` is unavailable
- ownership enforced

Responses:

- `204 No Content` when unregistered (or already inactive)
- `403 /errors/push-token-ownership-forbidden` when token belongs to another user

## Localization and Payload

Language resolution order:

1. token language (`push_device_tokens.language`)
2. user preferred language (`users.preferred_language`)
3. fallback `en`

### APNs Payload Structure (iOS)

The APNs provider sends a single JSON object per notification. The `aps` key contains the visible alert and sound. Custom data fields are spread at the root level alongside `aps`:

```json
{
  "aps": {
    "alert": {
      "title": "Cruise invitation",
      "body": "Jan Kowalski invited you to \"Weekend Regatta\""
    },
    "sound": "default"
  },
  "notificationId": "019415a2-...",
  "eventType": "CRUISE_INVITATION_SENT",
  "sourceType": "CRUISE",
  "sourceId": "019415a0-...",
  "deepLink": "/notifications/019415a2-...",
  "relationId": "01941590-..."
}
```

### FCM Payload Structure (Android)

The FCM provider sends via FCM HTTP v1 API. The `notification` object contains the visible alert, and `data` carries routing fields as string key-value pairs:

```json
{
  "message": {
    "token": "<device-token>",
    "notification": {
      "title": "Cruise invitation",
      "body": "Jan Kowalski invited you to \"Weekend Regatta\""
    },
    "data": {
      "notificationId": "019415a2-...",
      "eventType": "CRUISE_INVITATION_SENT",
      "sourceType": "CRUISE",
      "sourceId": "019415a0-...",
      "deepLink": "/notifications/019415a2-...",
      "relationId": "01941590-..."
    },
    "android": {
      "priority": "HIGH"
    }
  }
}
```

### Data Fields Reference

| Field            | Type             | Required | Description                                                       |
| ---------------- | ---------------- | -------- | ----------------------------------------------------------------- |
| `notificationId` | string (UUID v7) | yes      | Notification record ID                                            |
| `eventType`      | string           | yes      | One of 17 `NotificationEventType` values (see table below)        |
| `sourceType`     | string           | yes      | `CRUISE`, `POST`, `FRIEND`, or `REVIEW`                           |
| `sourceId`       | string (UUID v7) | yes      | ID of the source entity (cruise, post, friend request, or review) |
| `deepLink`       | string           | yes      | Always `/notifications/{notificationId}`                          |
| `relationId`     | string (UUID v7) | no       | Actor or related user ID (present on all current event types)     |

### Event Type Reference

| Event Type                   | Source Type | `sourceId` refers to | `relationId` refers to | Title (EN)                     | Body template (EN)                                            | Suggested screen    |
| ---------------------------- | ----------- | -------------------- | ---------------------- | ------------------------------ | ------------------------------------------------------------- | ------------------- |
| `CRUISE_INVITATION_SENT`     | `CRUISE`    | cruise ID            | inviter user ID        | Cruise invitation              | {actor} invited you to "{cruiseTitle}"                        | Cruise detail       |
| `CRUISE_PARTICIPANT_JOINED`  | `CRUISE`    | cruise ID            | joined user ID         | New cruise participant         | {actor} joined "{cruiseTitle}"                                | Cruise detail       |
| `CRUISE_REQUEST_PENDING`     | `CRUISE`    | cruise ID            | requester user ID      | New join request               | {actor} requested to join "{cruiseTitle}"                     | Cruise participants |
| `CRUISE_REQUEST_ACCEPTED`    | `CRUISE`    | cruise ID            | organizer user ID      | Request accepted               | {actor} accepted your join request                            | Cruise detail       |
| `CRUISE_REQUEST_REJECTED`    | `CRUISE`    | cruise ID            | organizer user ID      | Request rejected               | {actor} rejected your join request                            | Cruise list         |
| `CRUISE_INVITATION_ACCEPTED` | `CRUISE`    | cruise ID            | accepting user ID      | Invitation accepted            | {actor} accepted your cruise invitation                       | Cruise detail       |
| `CRUISE_PARTICIPANT_LEFT`    | `CRUISE`    | cruise ID            | user who left ID       | Participant left               | {actor} left "{cruiseTitle}"                                  | Cruise detail       |
| `CRUISE_PARTICIPANT_REMOVED` | `CRUISE`    | cruise ID            | remover user ID        | You were removed from a cruise | {actor} removed you from "{cruiseTitle}"                      | Cruise list         |
| `CRUISE_DETAILS_CHANGED`     | `CRUISE`    | cruise ID            | editor user ID         | Cruise details updated         | {actor} updated "{cruiseTitle}"                               | Cruise detail       |
| `POST_REACTED`               | `POST`      | post ID              | reactor user ID        | New post reaction              | {actor} reacted to your post (reactionType)                   | Post detail         |
| `POST_COMMENTED`             | `POST`      | post ID              | commenter user ID      | New post comment               | {commentText} or "{actor} commented on your post"             | Post detail         |
| `FRIEND_REQUEST_SENT`        | `FRIEND`    | friend request ID    | sender user ID         | New friend request             | {actor} sent you a friend request                             | Friend requests     |
| `FRIEND_REQUEST_ACCEPTED`    | `FRIEND`    | friend request ID    | accepter user ID       | Friend request accepted        | {actor} accepted your friend request                          | User profile        |
| `FRIEND_REQUEST_REJECTED`    | `FRIEND`    | friend request ID    | rejecter user ID       | Friend request rejected        | {actor} rejected your friend request                          | Friend list         |
| `REVIEW_PENDING_RECEIVED`    | `REVIEW`    | review ID            | reviewer user ID       | New review pending             | {actor} reviewed you after "{cruiseTitle}"                    | Review detail       |
| `REVIEW_PUBLISHED`           | `REVIEW`    | review ID            | reviewer user ID       | Review published               | {actor} published a review after "{cruiseTitle}"              | Review detail       |
| `CRUISE_REVIEW_REMINDER`     | `CRUISE`    | cruise ID            | `null`                 | Review your crew               | Your cruise "{cruiseTitle}" has ended. Share your experience! | Cruise reviews      |

### Full APNs Example Payloads

**Cruise invitation:**

```json
{
  "aps": {
    "alert": {
      "title": "Cruise invitation",
      "body": "Jan Kowalski invited you to \"Weekend Regatta\""
    },
    "sound": "default"
  },
  "notificationId": "019415a2-3b7c-7d8e-9f01-234567890abc",
  "eventType": "CRUISE_INVITATION_SENT",
  "sourceType": "CRUISE",
  "sourceId": "019415a0-1a2b-7c3d-4e5f-678901234567",
  "deepLink": "/notifications/019415a2-3b7c-7d8e-9f01-234567890abc",
  "relationId": "01941590-aabb-7ccd-deef-001122334455"
}
```

**Post comment:**

```json
{
  "aps": {
    "alert": {
      "title": "New post comment",
      "body": "Great photos from the trip!"
    },
    "sound": "default"
  },
  "notificationId": "019415b0-4c5d-7e6f-8a9b-0c1d2e3f4a5b",
  "eventType": "POST_COMMENTED",
  "sourceType": "POST",
  "sourceId": "019415ae-7f8e-7d6c-5b4a-392817263540",
  "deepLink": "/notifications/019415b0-4c5d-7e6f-8a9b-0c1d2e3f4a5b",
  "relationId": "01941591-bbcc-7dde-ef00-112233445566"
}
```

**Friend request:**

```json
{
  "aps": {
    "alert": {
      "title": "New friend request",
      "body": "Anna Nowak sent you a friend request"
    },
    "sound": "default"
  },
  "notificationId": "019415c0-6d7e-7f8a-9b0c-1d2e3f4a5b6c",
  "eventType": "FRIEND_REQUEST_SENT",
  "sourceType": "FRIEND",
  "sourceId": "019415be-8a9b-7c0d-1e2f-3a4b5c6d7e8f",
  "deepLink": "/notifications/019415c0-6d7e-7f8a-9b0c-1d2e3f4a5b6c",
  "relationId": "01941592-ccdd-7eef-0011-223344556677"
}
```

## Provider Configuration

APNs and FCM are controlled independently. API endpoints always stay active regardless of provider configuration.

```env
PUSH_APNS_ENABLED=false
PUSH_FCM_ENABLED=false
```

Behavior:

- `PUSH_APNS_ENABLED=true` + complete APNs env vars => iOS push can be sent
- `PUSH_FCM_ENABLED=true` + complete FCM env vars => Android push can be sent
- provider disabled or misconfigured => worker marks matching deliveries as `SKIPPED` (with reason in `error_code` / `error_message`)
- skipped deliveries are consumed from queue and are not retried later after enabling credentials

### Key/Certificate Format (Important)

Current implementation supports **only inline private keys in env vars** with escaped newlines (`\n`).

Supported now:

- inline PEM/key string with escaped newlines (recommended)

Not supported now:

- path to key file (e.g. `/secrets/AuthKey.p8`)
- base64-encoded key value (unless you decode it first yourself and pass inline PEM)

Summary:

- APNs uses a **p8 private key** (token auth), not classic certificate upload in this backend.
- FCM uses **service account private key** from JSON credentials.

### APNs (iOS)

Required:

```env
PUSH_APNS_TEAM_ID=
PUSH_APNS_KEY_ID=
PUSH_APNS_BUNDLE_ID=
PUSH_APNS_PRIVATE_KEY=
```

Optional:

```env
PUSH_APNS_USE_SANDBOX=true
PUSH_APNS_PRIORITY=10
```

Notes:

- `PUSH_APNS_PRIVATE_KEY` should be stored as one line with escaped newlines (`\n`).
- Use Apple p8 key auth (team id + key id + bundle id topic).
- Example value format:

```env
PUSH_APNS_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIGTAgEAMBMG...\n-----END PRIVATE KEY-----\n"
```

How to convert `.p8` file to env-friendly inline value:

```bash
awk '{printf "%s\\n", $0}' AuthKey_ABC123XYZ.p8
```

### FCM HTTP v1 (Android)

Required:

```env
PUSH_FCM_PROJECT_ID=
PUSH_FCM_CLIENT_EMAIL=
PUSH_FCM_PRIVATE_KEY=
```

Optional:

```env
PUSH_FCM_TOKEN_URI=https://oauth2.googleapis.com/token
PUSH_FCM_SCOPE=https://www.googleapis.com/auth/firebase.messaging
```

Notes:

- `PUSH_FCM_PRIVATE_KEY` should be stored as one line with escaped newlines (`\n`).
- Integration uses OAuth2 service account JWT flow and FCM HTTP v1 endpoint.
- Example value format:

```env
PUSH_FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBg...\n-----END PRIVATE KEY-----\n"
```

How to extract config from service account JSON:

```bash
# project id
jq -r '.project_id' service-account.json

# client email
jq -r '.client_email' service-account.json

# private key as inline env value
jq -r '.private_key' service-account.json | awk '{printf "%s\\n", $0}'
```

Example complete block:

```env
PUSH_FCM_PROJECT_ID="my-firebase-project"
PUSH_FCM_CLIENT_EMAIL="firebase-adminsdk-xxx@my-firebase-project.iam.gserviceaccount.com"
PUSH_FCM_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

### Production Secrets Recommendation

- keep keys in secret manager (not committed files)
- inject as environment variables at runtime
- do not log raw key values
- rotate APNs/FCM credentials periodically

## Queue and Worker

Queues:

- `push`
- `push-dlq`

Job payload:

```json
{
  "notificationId": "uuid"
}
```

Retry policy:

- exponential backoff
- retry only transient provider failures
- permanent token errors deactivate token
- disabled/misconfigured provider deliveries are marked `SKIPPED` and acknowledged (no backlog buildup)

Run API + worker locally:

```bash
npm run start:all:dev
```

Worker entrypoint:

- `src/worker-push-notifications.ts` (push notification worker)

## Error Monitoring and Security

- unexpected worker errors are captured to Sentry
- raw push tokens are never logged
- token ownership checks are enforced in unregister-by-token endpoint

## Related Docs

- [Notifications](./index.md)
- [OpenAPI](../openapi.yaml)
- [Sentry Integration](../technical/sentry.md)
