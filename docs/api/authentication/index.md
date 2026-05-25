# Authentication

SkipperClub uses JWT (JSON Web Token) authentication with access and refresh tokens. This module covers user registration and advanced session management.

For basic authentication flows (login, token refresh, logout), see [Getting Started: Authentication](../getting-started/authentication.md).

## Overview

```mermaid
flowchart LR
    A[Register]:::trigger --> B[Get Tokens]:::success
    C[Login]:::trigger --> B
    O[OTP Login]:::trigger --> B
    B --> D[Use Access Token]:::state
    D --> E{Token Expired?}:::decision
    E -->|No| D
    E -->|Yes| F[Refresh Token]:::trigger
    F --> D
    F -->|Refresh Expired| C

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef decision fill:#8B5CF6,stroke:#5B21B6,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

## User Registration

Create a new user account and receive authentication tokens in a single request.

### Endpoint

```http
POST /users
```

### Example Request

```http
POST /v1/users HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json
Accept-Language: en

{
  "name": "Jan Kowalski",
  "email": "jan.kowalski@email.com",
  "password": "SecurePass123!"
}
```

### Parameters

| Field      | Type   | Required | Constraints                                                                                                                                    |
| ---------- | ------ | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `name`     | string | Yes      | 1-100 characters                                                                                                                               |
| `email`    | string | Yes      | Valid email, max 320 characters, must be unique                                                                                                |
| `password` | string | Yes      | 8-128 characters; must contain at least one letter, one digit, and one special character (see [Password Requirements](#password-requirements)) |

### Success Response

**Status:** `201 Created`

**Body:**

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "user": {
    "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
    "email": "jan.kowalski@email.com",
    "name": "Jan Kowalski",
    "avatarUrl": null
  }
}
```

> **Note:** User role is included in the JWT payload (access and refresh tokens) but not in the session response body. To retrieve the role, decode the JWT token or use the `GET /profile` endpoint.

| Field          | Description                                         |
| -------------- | --------------------------------------------------- |
| `id`           | Session ID (UUID v7)                                |
| `accessToken`  | JWT for API authorization (15-minute expiry)        |
| `refreshToken` | JWT for obtaining new access tokens (7-day expiry)  |
| `expiresIn`    | Access token lifetime in seconds (900 = 15 minutes) |
| `user`         | Created user information                            |

### Error Responses

**Email Already Exists (422):**

```json
{
  "type": "/errors/email-already-exists",
  "title": "Email Already Exists",
  "status": 422,
  "detail": "A user with this email address already exists"
}
```

**Validation Error (422):**

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
      "message": "password must be longer than or equal to 8 characters"
    }
  ]
}
```

## Registration Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DB

    Note over Client,DB: User Registration
    Client->>API: POST /users {name, email, password}
    API->>DB: Check email uniqueness
    alt Email exists
        DB-->>API: User found
        API-->>Client: 422 Email Already Exists
    else Email available
        DB-->>API: No user found
        API->>API: Hash password (bcrypt)
        API->>DB: Create user
        DB-->>API: User created
        API->>DB: Create session
        DB-->>API: Session created
        API->>API: Generate JWT tokens
        API-->>Client: 201 {session, tokens, user}
    end
```

## Password Reset

Users can recover access to their account by requesting a one-time password reset link by email. The link contains a high-entropy random token (32 random bytes encoded as base64url), not a short numeric code.

### Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DB
    participant Email

    Client->>API: POST /auth/password-reset-request {email}
    API->>DB: Look up user by email
    alt User exists
        API->>DB: Persist random token (sha256-hashed)
        API->>Email: Queue password-reset email with link
    else User missing or deleted
        Note over API: Skip token generation
    end
    API-->>Client: 204 No Content

    Note over Client: User clicks link in email

    Client->>API: POST /auth/password-reset {email, code, password}
    API->>DB: Tx#1 — lock challenge, verify token, increment attempts on fail
    alt Valid token
        API->>API: Hash new password (bcrypt)
        API->>DB: Tx#2 — mark used, update password, delete sessions
        API->>Email: Queue password-changed notification
        API-->>Client: 204 No Content
    else Invalid / expired
        API-->>Client: 401 invalid-password-reset-code
    end
```

### Endpoints

#### Request reset link

```http
POST /auth/password-reset-request
Content-Type: application/json
Accept-Language: en

{
  "email": "jan.kowalski@email.com"
}
```

| Field   | Type   | Required | Constraints                     |
| ------- | ------ | -------- | ------------------------------- |
| `email` | string | Yes      | Valid email, max 320 characters |

Responses:

- `204 No Content` — Always returned for valid email payloads, regardless of whether the address is registered (prevents enumeration).
- `422` — Email missing or malformed.
- `403` — CAPTCHA verification failed.
- `429` — Per-IP request rate limit exceeded.

Behaviour notes:

- A random 32-byte token is generated with `crypto.randomBytes` and encoded as base64url (43 characters, no padding). Only the `sha256` of the token is stored in `auth_challenges` with type `password_reset`.
- Tokens expire after 10 minutes by default (`PASSWORD_RESET_CODE_EXPIRATION_MINUTES`).
- Re-requesting within the per-user rate limit window (`PASSWORD_RESET_RATE_LIMIT_MINUTES`, default 10 minutes) is silently ignored — the original token stays valid.
- The reset email contains a deep link `https://{webapp}/{lang}/password-reset?email=...&code=...`. The token is not designed to be typed by hand.

#### Reset password

```http
POST /auth/password-reset
Content-Type: application/json
Accept-Language: en

{
  "email": "jan.kowalski@email.com",
  "code": "kL3oTm9XvP8aR2qZ7nB4cD5eF6gH1iJ0wXyZaBcDe-_",
  "password": "NewSecurePass123!"
}
```

| Field      | Type   | Required | Constraints                                                                 |
| ---------- | ------ | -------- | --------------------------------------------------------------------------- |
| `email`    | string | Yes      | Valid email, max 320 characters                                             |
| `code`     | string | Yes      | Exactly 43 URL-safe characters (`A-Z`, `a-z`, `0-9`, `_`, `-`)              |
| `password` | string | Yes      | 8-128 characters; at least one letter, one digit, and one special character |

Responses:

- `204 No Content` — Password updated, all existing sessions revoked, notification email queued. The client must log in again with the new password.
- `401 /errors/invalid-password-reset-code` — Token is invalid, expired, used, or the user does not exist (single uniform response to prevent enumeration).
- `422` — Validation failure (missing fields, weak password, malformed email or token).
- `429 /errors/password-reset-rate-limit` — Per-IP+email or global per-email lockout reached.

### Security Considerations

- Tokens have ~256 bits of entropy (32 random bytes); only their `sha256` is persisted, and verification uses `timingSafeEqual` against a dummy buffer when no challenge exists (constant-time, hides "does the email exist" from timing oracles).
- The verification handler holds a `pessimistic_write` row lock across `findOne` → code compare → `attempts++` / `usedAt` so two concurrent verify requests cannot both observe an unused challenge. Password reset uses a two-transaction pattern: tx#1 locks + verifies (no bcrypt under lock); bcrypt runs unlocked; tx#2 re-acquires the lock and applies the password change atomically with session revocation, guarding against double-use via a second `usedAt IS NULL` check.
- Per-challenge `maxAttempts` is stored on the `auth_challenges` row at creation time (5 for password reset) and consulted during verification — there is no config override that can drift from the persisted value.
- Each successful reset revokes **every** existing session for the user and triggers an `AuthPasswordChangedEmail` notification (sent best-effort; reset succeeds even if the email queue is unavailable).
- Failed attempts are tracked in two independent Redis counters: per IP+email (10 failures within `PASSWORD_RESET_LOCKOUT_MINUTES`, default 15) **and** globally per email (20 failures within `PASSWORD_RESET_EMAIL_LOCKOUT_MINUTES`, default 60). The global per-email counter defends against attackers rotating IPs.
- Response timing is normalized (`PASSWORD_RESET_RESPONSE_DELAY_MS`, default 500ms) to limit timing-based enumeration.

## Session Management

### Multiple Device Sessions

Each login or registration creates a new session. Users can have multiple active sessions across devices.

**Session Properties:**

| Property      | Description                        |
| ------------- | ---------------------------------- |
| Session ID    | Unique identifier for the session  |
| User ID       | Owner of the session               |
| Access Token  | Current valid access token         |
| Refresh Token | Current valid refresh token        |
| Expires At    | Refresh token expiration timestamp |
| Created At    | Session creation timestamp         |

### Session Lifecycle

```mermaid
flowchart TD
    A[Registration/Login]:::trigger --> B[Session Created]:::success
    B --> C[Tokens Issued]:::state
    C --> D{Token Used}:::decision
    D -->|Access Token| E[API Request]:::trigger
    D -->|Refresh Token| F[New Tokens]:::success
    F --> C
    E --> G{Session Valid?}:::decision
    G -->|Yes| H[Request Processed]:::success
    G -->|No| I[401 Unauthorized]:::negative
    B --> J[Manual Logout]:::trigger
    J --> K[Session Deleted]:::state
    B --> L[Token Expired]:::negative
    L --> K

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef decision fill:#8B5CF6,stroke:#5B21B6,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
    classDef negative fill:#F59E0B,stroke:#B45309,color:#000000
```

### Token Rotation

When refreshing tokens:

1. Both access and refresh tokens are regenerated
2. Previous tokens are invalidated
3. Session record is updated with new tokens

This prevents token reuse and limits exposure from stolen tokens.

## Authentication Endpoints Summary

| Endpoint                       | Method | Auth Required | Description                           |
| ------------------------------ | ------ | ------------- | ------------------------------------- |
| `/users`                       | POST   | No            | Register new user                     |
| `/auth/login`                  | POST   | No            | Login with email and password         |
| `/auth/otp`                    | POST   | No            | Request OTP code via email            |
| `/auth/otp/verify`             | POST   | No            | Verify OTP code and get tokens        |
| `/auth/password-reset-request` | POST   | No            | Request password reset code via email |
| `/auth/password-reset`         | POST   | No            | Reset password with code              |
| `/sessions/{id}/refresh`       | POST   | No            | Refresh tokens                        |
| `/sessions/{id}`               | DELETE | Yes           | Logout (delete session)               |

## Token Specifications

| Token             | Lifetime   | Purpose                  |
| ----------------- | ---------- | ------------------------ |
| **Access Token**  | 15 minutes | Authorize API requests   |
| **Refresh Token** | 7 days     | Obtain new access tokens |

### JWT Payload

**Access Token:**

```json
{
  "sub": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "email": "jan.kowalski@email.com",
  "sessionId": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "role": "user",
  "iat": 1700480000,
  "exp": 1700480900
}
```

**Refresh Token:**

```json
{
  "sub": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "email": "jan.kowalski@email.com",
  "sessionId": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "role": "user",
  "type": "refresh",
  "iat": 1700480000,
  "exp": 1701084800
}
```

| Claim       | Description                                                     |
| ----------- | --------------------------------------------------------------- |
| `sub`       | User ID                                                         |
| `email`     | User email address                                              |
| `sessionId` | Current session ID                                              |
| `role`      | User role (`user` or `admin`) for authorization                 |
| `type`      | Token type (only present in refresh tokens, value: `"refresh"`) |
| `iat`       | Issued at timestamp                                             |
| `exp`       | Expiration timestamp                                            |

## Security Considerations

### Password Requirements

- Minimum 8 characters
- Maximum 128 characters
- At least one letter (A-Z or a-z)
- At least one digit (0-9)
- At least one special character (any non-alphanumeric, non-whitespace character)
- Stored using bcrypt hashing (cost factor 10)

These requirements apply to every endpoint that accepts a password, including:

- `POST /users` — Register new user
- `POST /invitations/register` — Register using invitation code
- `POST /auth/password-reset` — Reset password using verification code

### Email Handling

- Emails are normalized to lowercase
- Each email can only be associated with one account
- Email validation uses RFC 5322 standard

### Best Practices

1. **HTTPS Only** — Never send credentials over unencrypted connections
2. **Secure Storage** — Store tokens in secure storage (Keychain, Keystore)
3. **Token Refresh** — Implement proactive refresh before expiration
4. **Logout on Sensitive Actions** — Invalidate sessions on password change
5. **Handle Token Errors** — Redirect to login on 401 responses

## CAPTCHA Protection

### Overview

All public authentication endpoints are protected by [Cloudflare Turnstile](https://www.cloudflare.com/application-services/products/turnstile/) CAPTCHA to prevent automated attacks, credential stuffing, and brute-force attempts.

### Protected Endpoints

The following endpoints require CAPTCHA verification when enabled:

- `POST /auth/otp` - Request OTP code
- `POST /auth/otp/verify` - Verify OTP code
- `POST /auth/login` - Email and password login

### How It Works

When CAPTCHA is enabled (server has `TURNSTILE_SECRET_KEY` configured), clients must include a Turnstile token in the `X-Turnstile-Token` header:

```http
POST /v1/auth/login HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json
X-Turnstile-Token: <turnstile-token>

{
  "email": "jan.kowalski@email.com",
  "password": "demo123!"
}
```

### CAPTCHA Lifecycle

```mermaid
sequenceDiagram
    participant Client
    participant Cloudflare
    participant API

    Client->>Cloudflare: Solve CAPTCHA challenge
    Cloudflare-->>Client: Return token
    Client->>API: POST /auth/login {X-Turnstile-Token}
    API->>Cloudflare: Verify token
    alt Token valid
        Cloudflare-->>API: success: true
        API-->>Client: 201 Session created
    else Token invalid
        Cloudflare-->>API: success: false
        API-->>Client: 403 CAPTCHA verification failed
    end
```

### Kill Switch

When `TURNSTILE_SECRET_KEY` is not configured, CAPTCHA verification is completely disabled:

- The `X-Turnstile-Token` header is ignored
- All requests proceed without CAPTCHA checks
- Useful for testing and development environments

### Error Responses

**CAPTCHA Token Missing (403):**

```json
{
  "type": "/errors/captcha-token-missing",
  "title": "CAPTCHA Token Required",
  "status": 403,
  "detail": "A CAPTCHA verification token is required"
}
```

**CAPTCHA Verification Failed (403):**

```json
{
  "type": "/errors/captcha-verification-failed",
  "title": "CAPTCHA Verification Failed",
  "status": 403,
  "detail": "The CAPTCHA verification failed. Please try again."
}
```

**CAPTCHA Service Unavailable (503):**

```json
{
  "type": "/errors/captcha-service-unavailable",
  "title": "CAPTCHA Service Unavailable",
  "status": 503,
  "detail": "The CAPTCHA verification service is temporarily unavailable"
}
```

### Token Constraints

- **Single-use**: Each token can only be validated once
- **Expiration**: Tokens expire after 5 minutes (300 seconds)
- **Maximum length**: 2048 characters

### Client Implementation

1. Load the Turnstile widget on your login/auth page
2. Wait for the user to solve the challenge
3. Extract the token from the widget callback
4. Include the token in the `X-Turnstile-Token` header
5. Handle 403 errors by requesting a new CAPTCHA

### Testing

For testing purposes, Cloudflare provides test keys that always pass or always fail validation. See the [Turnstile testing documentation](https://developers.cloudflare.com/turnstile/troubleshooting/testing/) for details.

## Error Reference

| Error Type                            | Status | Cause                                                   |
| ------------------------------------- | ------ | ------------------------------------------------------- |
| `/errors/email-already-exists`        | 422    | Email address is already registered                     |
| `/errors/validation`                  | 422    | Request body validation failed                          |
| `/errors/invalid-credentials`         | 401    | Wrong email or password (login)                         |
| `/errors/invalid-otp-code`            | 401    | OTP code is invalid or expired                          |
| `/errors/otp-rate-limit`              | 429    | Too many OTP requests or failed attempts                |
| `/errors/invalid-password-reset-code` | 401    | Password reset code is invalid, expired, or unknown     |
| `/errors/password-reset-rate-limit`   | 429    | Too many failed password-reset attempts                 |
| `/errors/invalid-refresh-token`       | 401    | Refresh token is invalid or doesn't match session       |
| `/errors/refresh-token-expired`       | 401    | Refresh token has expired                               |
| `/errors/session-not-found`           | 404    | Session doesn't exist                                   |
| `/errors/authentication-required`     | 401    | Access token is missing, invalid, or expired            |
| `/errors/forbidden-role`              | 403    | User lacks required role for the endpoint               |
| `/errors/captcha-token-missing`       | 403    | CAPTCHA token is missing from request headers           |
| `/errors/captcha-verification-failed` | 403    | CAPTCHA token verification failed                       |
| `/errors/captcha-service-unavailable` | 503    | Cloudflare Turnstile service is temporarily unavailable |

## Related

- [Getting Started: Authentication](../getting-started/authentication.md) — Login, refresh, logout flows
- [Getting Started: Errors](../getting-started/errors.md) — RFC 7807 error format
- [Users Module](../users/index.md) — User profile management
- [OpenAPI Specification](../openapi.yaml) — Full API reference
