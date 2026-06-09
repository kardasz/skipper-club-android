# Error Handling

SkipperClub API uses RFC 7807 Problem Details format for all error responses. This provides consistent, machine-readable error information.

## Response Format

All errors return `Content-Type: application/problem+json` with this structure:

```json
{
  "type": "/errors/error-type",
  "title": "Human Readable Title",
  "status": 400,
  "detail": "Detailed explanation of what went wrong"
}
```

### Fields

| Field        | Type    | Description                                      |
| ------------ | ------- | ------------------------------------------------ |
| `type`       | string  | URI reference identifying the error type         |
| `title`      | string  | Short, human-readable summary                    |
| `status`     | integer | HTTP status code                                 |
| `detail`     | string  | Detailed explanation specific to this occurrence |
| `violations` | array   | Field-level validation errors (optional)         |

## Common Error Types

### Authentication Errors (401)

**Invalid Credentials:**

```json
{
  "type": "/errors/invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "The provided email or password is incorrect"
}
```

**Authentication Required (missing or invalid token):**

```json
{
  "type": "/errors/authentication-required",
  "title": "Authentication Required",
  "status": 401,
  "detail": "Invalid or expired access token"
}
```

**Invalid Refresh Token:**

```json
{
  "type": "/errors/invalid-refresh-token",
  "title": "Invalid Refresh Token",
  "status": 401,
  "detail": "The provided refresh token is invalid or does not match the session"
}
```

**Expired Refresh Token:**

```json
{
  "type": "/errors/refresh-token-expired",
  "title": "Refresh Token Expired",
  "status": 401,
  "detail": "The refresh token has expired and a new login is required"
}
```

### Authorization Errors (403)

**Access Forbidden:**

```json
{
  "type": "/errors/forbidden",
  "title": "Forbidden",
  "status": 403,
  "detail": "You do not have permission to access this resource"
}
```

**Friend Request Access Forbidden:**

```json
{
  "type": "/errors/friend-request-access-forbidden",
  "title": "Friend Request Access Forbidden",
  "status": 403,
  "detail": "You are not allowed to access this friend request"
}
```

### Not Found Errors (404)

**Generic Not Found:**

```json
{
  "type": "/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "The requested resource was not found"
}
```

**Session Not Found:**

```json
{
  "type": "/errors/session-not-found",
  "title": "Session Not Found",
  "status": 404,
  "detail": "The requested session could not be found"
}
```

**User Not Found:**

```json
{
  "type": "/errors/target-user-not-found",
  "title": "Target User Not Found",
  "status": 404,
  "detail": "The target user could not be found"
}
```

**Friend Request Not Found:**

```json
{
  "type": "/errors/friend-request-not-found",
  "title": "Friend Request Not Found",
  "status": 404,
  "detail": "The requested friend request could not be found"
}
```

### Validation Errors (422)

Validation errors include a `violations` array with field-level details:

```json
{
  "type": "/errors/validation",
  "title": "Validation Failed",
  "status": 422,
  "detail": "The request contains invalid data",
  "violations": [
    {
      "propertyPath": "email",
      "message": "email must be a valid email address"
    },
    {
      "propertyPath": "password",
      "message": "password must be at least 8 characters long"
    }
  ]
}
```

### Business Logic Errors (422)

**Cannot Friend Yourself:**

```json
{
  "type": "/errors/cannot-friend-yourself",
  "title": "Cannot Friend Yourself",
  "status": 422,
  "detail": "You cannot send a friend request to yourself"
}
```

**Users Already Friends:**

```json
{
  "type": "/errors/users-already-friends",
  "title": "Users Already Friends",
  "status": 422,
  "detail": "These users are already friends"
}
```

**Friend Request Already Exists:**

```json
{
  "type": "/errors/friend-request-already-exists",
  "title": "Friend Request Already Exists",
  "status": 422,
  "detail": "A friend request already exists between these users"
}
```

### File Upload Errors

**No Audio Provided (400):**

```json
{
  "type": "/errors/no-audio-provided",
  "title": "No Audio Provided",
  "status": 400,
  "detail": "An audio file must be provided for transcription"
}
```

**Unsupported Format (400):**

```json
{
  "type": "/errors/unsupported-audio-format",
  "title": "Unsupported Audio Format",
  "status": 400,
  "detail": "The audio file format is not supported. Please use audio/webm format."
}
```

**File Too Large (413):**

```json
{
  "type": "/errors/audio-file-too-large",
  "title": "Audio File Too Large",
  "status": 413,
  "detail": "The audio file exceeds the maximum size of 20MB"
}
```

### Server Errors (5xx)

**Service Unavailable (502):**

```json
{
  "type": "/errors/transcription-failed",
  "title": "Transcription Failed",
  "status": 502,
  "detail": "The transcription service failed to process the audio"
}
```

## HTTP Status Code Summary

| Code  | Meaning               | Common Causes                               |
| ----- | --------------------- | ------------------------------------------- |
| `400` | Bad Request           | Malformed JSON, missing required fields     |
| `401` | Unauthorized          | Missing token, invalid token, expired token |
| `403` | Forbidden             | Valid token but insufficient permissions    |
| `404` | Not Found             | Resource doesn't exist                      |
| `413` | Payload Too Large     | File exceeds size limit                     |
| `422` | Unprocessable Entity  | Validation failed, business rule violated   |
| `500` | Internal Server Error | Unexpected server error                     |
| `502` | Bad Gateway           | External service failed                     |

## Internationalization

Error messages support English (en) and Polish (pl). Set the `Accept-Language` header to receive localized messages:

```http
GET /v1/profile HTTP/1.1
Host: api.skipperclub.app
Accept-Language: pl
```

### Example: Polish Error Message

```json
{
  "type": "/errors/authentication-required",
  "title": "Wymagana autoryzacja",
  "status": 401,
  "detail": "Nieprawidłowy lub wygasły token dostępu"
}
```

### Supported Languages

| Code | Language          |
| ---- | ----------------- |
| `en` | English (default) |
| `pl` | Polish            |

If the requested language is not supported, English is used as fallback.

## Error Type Reference

### Authentication Module

| Type                               | Status | Description                         |
| ---------------------------------- | ------ | ----------------------------------- |
| `/errors/invalid-credentials`      | 401    | Wrong email or password             |
| `/errors/authentication-required`  | 401    | Missing or invalid token            |
| `/errors/invalid-refresh-token`    | 401    | Refresh token doesn't match session |
| `/errors/refresh-token-expired`    | 401    | Refresh token has expired           |
| `/errors/session-not-found`        | 404    | Session doesn't exist               |
| `/errors/session-access-forbidden` | 403    | Not allowed to access session       |
| `/errors/invalid-session`          | 401    | Session is invalid                  |

### Friends Module

| Type                                      | Status | Description                   |
| ----------------------------------------- | ------ | ----------------------------- |
| `/errors/cannot-friend-yourself`          | 422    | Attempted self-friend request |
| `/errors/users-already-friends`           | 422    | Users are already connected   |
| `/errors/friend-request-already-exists`   | 422    | Duplicate friend request      |
| `/errors/friend-request-not-found`        | 404    | Friend request doesn't exist  |
| `/errors/friend-request-access-forbidden` | 403    | Not allowed to access request |
| `/errors/target-user-not-found`           | 404    | Target user doesn't exist     |

### Media Module

| Type                               | Status | Description             |
| ---------------------------------- | ------ | ----------------------- |
| `/errors/no-audio-provided`        | 400    | Missing audio file      |
| `/errors/unsupported-audio-format` | 400    | Invalid file format     |
| `/errors/audio-file-too-large`     | 413    | File exceeds size limit |
| `/errors/transcription-failed`     | 502    | External service error  |

### Alerts Module

| Type                             | Status | Description                                                                |
| -------------------------------- | ------ | -------------------------------------------------------------------------- |
| `/errors/alert-not-found`        | 404    | Alert id missing or soft-deleted                                           |
| `/errors/alert-forbidden`        | 403    | Non-admin non-owner attempted `PUT` or `DELETE`                            |
| `/errors/invalid-alert-geometry` | 422    | Geometry type, coordinates, or polygon rings violate the GeoJSON rules     |
| `/errors/invalid-alert-viewport` | 422    | Only some viewport edges supplied, or `north <= south` on a viewport query |

### Common

| Type                 | Status | Description               |
| -------------------- | ------ | ------------------------- |
| `/errors/validation` | 422    | Request validation failed |
| `/errors/not-found`  | 404    | Resource not found        |
| `/errors/forbidden`  | 403    | Access denied             |

## Best Practices

1. **Always check status code first** — Use HTTP status to determine error category
2. **Parse error body** — Extract detailed information from the JSON response
3. **Handle validation errors specially** — Display field-level messages to users
4. **Implement retry logic** — For 401 errors, try refreshing the token
5. **Log error details** — Include `type` and `detail` in logs for debugging
6. **Show user-friendly messages** — Use `title` for display, `detail` for debugging
7. **Support internationalization** — Set `Accept-Language` for user's locale

## Next Steps

- [Authentication](./authentication.md) — Token management and refresh flow
- [Quick Start](./index.md) — Make your first API call
- [OpenAPI Specification](../openapi.yaml) — Full API reference
