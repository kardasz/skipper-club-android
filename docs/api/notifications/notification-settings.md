# Notification Settings

This document describes user-facing notification settings in SkipperClub: what users can control, what remains always on, and which API endpoints manage these preferences.

## Overview

Notification settings let each user decide whether they want to receive:

- notification emails
- mobile push notifications (iOS/Android)

These settings are account-level preferences and apply to the authenticated user only.
For this reason, settings endpoints are placed under the profile namespace (`/profile/...`), not under the notification center feed endpoints.

## Business Rules

### What users can control

| Channel                                  | Controlled by Settings | Notes                                                                                                     |
| ---------------------------------------- | ---------------------- | --------------------------------------------------------------------------------------------------------- |
| In-app (WebSocket + notification center) | No                     | Always delivered and visible in the in-app notification center.                                           |
| Push (APNs/FCM)                          | Yes                    | Controlled by `pushNotificationsEnabled`.                                                                 |
| Notification email                       | Yes                    | Controlled by `emailNotificationsEnabled`.                                                                |
| Transactional email                      | No                     | Always sent for critical account flows (for example login codes, invitations, account lifecycle actions). |

### Current scope

- Preferences are global per channel (email/push).
- Per-event preferences are not available in public API yet.

## API Endpoints

All endpoints below require JWT authentication and operate on the current user account.

| Method | Endpoint                            | Description                                                              |
| ------ | ----------------------------------- | ------------------------------------------------------------------------ |
| GET    | `/v1/profile/notification-settings` | Get current notification settings (creates default settings if missing). |
| PUT    | `/v1/profile/notification-settings` | Replace current settings (both fields required).                         |

## Get Notification Settings

```http
GET /v1/profile/notification-settings
Authorization: Bearer <token>
```

Returns the current channel preferences for the authenticated user.

If the user has no settings yet, the API initializes defaults:

- `emailNotificationsEnabled: true`
- `pushNotificationsEnabled: true`

### Example Response

```json
{
  "emailNotificationsEnabled": true,
  "pushNotificationsEnabled": true
}
```

## Update Notification Settings

```http
PUT /v1/profile/notification-settings
Content-Type: application/json
Authorization: Bearer <token>
```

The endpoint uses full replacement semantics:

- both fields are required in every request
- omitted fields cause validation error (`422`)

### Request Body

| Field                       | Type    | Required | Description                           |
| --------------------------- | ------- | -------- | ------------------------------------- |
| `emailNotificationsEnabled` | boolean | Yes      | Enables/disables notification emails. |
| `pushNotificationsEnabled`  | boolean | Yes      | Enables/disables push notifications.  |

### Example Request

```json
{
  "emailNotificationsEnabled": true,
  "pushNotificationsEnabled": false
}
```

### Example Response

```json
{
  "emailNotificationsEnabled": true,
  "pushNotificationsEnabled": false
}
```

## Typical Product Scenarios

### User wants only in-app notifications

Set both flags to `false`. The user still receives:

- in-app notification records
- real-time `notification:new` events over WebSocket

### User wants push, but no notification emails

Set:

- `pushNotificationsEnabled: true`
- `emailNotificationsEnabled: false`

### User wants full visibility across channels

Set both flags to `true`.

## Related

- [Notifications](./index.md) — Notification center API and event coverage
- [Push Notifications](./push-notifications.md) — Mobile token management and push behavior
- [Users](../users/index.md) — Profile endpoints and account settings
- [OpenAPI Specification](../openapi.yaml) — Source API contract
