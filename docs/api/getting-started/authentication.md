# Authentication

SkipperClub uses JWT (JSON Web Token) authentication with access and refresh tokens. This guide covers the complete authentication flow.

## Overview

```mermaid
flowchart LR
    A[Login]:::trigger --> B[Get Tokens]:::success
    B --> C[Use Access Token]:::state
    C --> D{Token Expired?}:::decision
    D -->|No| C
    D -->|Yes| E[Refresh Token]:::trigger
    E --> C
    E -->|Refresh Expired| A

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef decision fill:#8B5CF6,stroke:#5B21B6,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

## Token Specifications

| Token             | Lifetime   | Purpose                  |
| ----------------- | ---------- | ------------------------ |
| **Access Token**  | 15 minutes | Authorize API requests   |
| **Refresh Token** | 7 days     | Obtain new access tokens |

## Login

Create a session to obtain tokens.

### Endpoint

```http
POST /auth/login
```

### Example Request

```http
POST /v1/auth/login HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json
Accept-Language: en

{
  "email": "jan.kowalski@email.com",
  "password": "demo123!"
}
```

### Parameters

| Field      | Type   | Required | Constraints                     |
| ---------- | ------ | -------- | ------------------------------- |
| `email`    | string | Yes      | Valid email, max 320 characters |
| `password` | string | Yes      | 8-128 characters                |

### Success Response

**Status:** `201 Created`

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMThmYTJlNC04ZTNiLTdiMmUtOGUzYi03YjJlOGUzYjdiMmUiLCJpYXQiOjE3MDA0ODAwMDAsImV4cCI6MTcwMDQ4MDkwMH0.signature",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIwMThmYTJlNC04ZTNiLTdiMmUtOGUzYi03YjJlOGUzYjdiMmUiLCJpYXQiOjE3MDA0ODAwMDAsImV4cCI6MTcwMTA4NDgwMH0.signature",
  "expiresIn": 900,
  "user": {
    "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
    "email": "jan.kowalski@email.com",
    "name": "Jan Kowalski"
  }
}
```

| Field          | Description                                         |
| -------------- | --------------------------------------------------- |
| `id`           | Session ID (UUID v7)                                |
| `accessToken`  | JWT for API authorization                           |
| `refreshToken` | JWT for obtaining new access tokens                 |
| `expiresIn`    | Access token lifetime in seconds (900 = 15 minutes) |
| `user`         | Basic user information                              |

### Error Responses

**Invalid Credentials (401):**

```json
{
  "type": "/errors/invalid-credentials",
  "title": "Invalid Credentials",
  "status": 401,
  "detail": "The provided email or password is incorrect"
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
    }
  ]
}
```

## OTP (Passwordless) Login

Login without a password using a one-time code sent to your email.

### Step 1: Request OTP Code

```http
POST /auth/otp
```

### Example Request

```http
POST /v1/auth/otp HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json

{
  "email": "jan.kowalski@email.com"
}
```

### Parameters

| Field   | Type   | Required | Constraints                     |
| ------- | ------ | -------- | ------------------------------- |
| `email` | string | Yes      | Valid email, max 320 characters |

### Success Response

**Status:** `204 No Content`

No response body. A 6-digit verification code is sent to the email address.

**Note:** The response is always 204 regardless of whether the email exists to prevent email enumeration.

### Step 2: Verify OTP Code

```http
POST /auth/otp/verify
```

### Example Request

```http
POST /v1/auth/otp/verify HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json

{
  "email": "jan.kowalski@email.com",
  "code": "123456"
}
```

### Parameters

| Field   | Type   | Required | Constraints                     |
| ------- | ------ | -------- | ------------------------------- |
| `email` | string | Yes      | Valid email, max 320 characters |
| `code`  | string | Yes      | 6-digit numeric code            |

### Success Response

**Status:** `200 OK`

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "user": {
    "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
    "email": "jan.kowalski@email.com",
    "name": "Jan Kowalski"
  }
}
```

### Error Responses

**Invalid or Expired Code (401):**

```json
{
  "type": "/errors/invalid-otp-code",
  "title": "Invalid OTP Code",
  "status": 401,
  "detail": "The provided verification code is invalid or has expired"
}
```

**Rate Limited (429):**

```json
{
  "type": "/errors/otp-rate-limit",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Too many OTP requests. Please try again later."
}
```

### OTP Security Features

- Codes expire after 15 minutes
- Rate limiting: 3 requests per minute per IP
- Lockout after 10 failed attempts from the same IP+email pair (15-minute lockout)
- Additional global lockout after 20 failed attempts for the same email across all IPs (60-minute lockout)
- Codes are single-use and invalidated after verification

## Using Access Tokens

Include the access token in the `Authorization` header for all authenticated requests.

### Header Format

```http
Authorization: Bearer <access_token>
```

### Example

```http
GET /v1/profile HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Unauthorized Response

If the token is missing, invalid, or expired:

```json
{
  "type": "/errors/authentication-required",
  "title": "Authentication Required",
  "status": 401,
  "detail": "Invalid or expired access token"
}
```

### JWT Token Payload

The access token contains user information including the user's role (`user` or `admin`). For detailed JWT payload structure and claims, see [Authentication Module](../authentication/index.md#jwt-payload).

## Token Refresh

When the access token expires, use the refresh token to obtain a new one without re-authenticating.

### Endpoint

```http
POST /sessions/{sessionId}/refresh
```

### Example Request

```http
POST /v1/sessions/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e/refresh HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Parameters

| Parameter      | Location | Type   | Required |
| -------------- | -------- | ------ | -------- |
| `sessionId`    | Path     | UUID   | Yes      |
| `refreshToken` | Body     | string | Yes      |

### Success Response

**Status:** `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new-token...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.new-refresh...",
  "expiresIn": 900
}
```

Both tokens are rotated on refresh for security.

### Error Responses

**Invalid Refresh Token (401):**

```json
{
  "type": "/errors/invalid-refresh-token",
  "title": "Invalid Refresh Token",
  "status": 401,
  "detail": "The provided refresh token is invalid or does not match the session"
}
```

**Expired Refresh Token (401):**

```json
{
  "type": "/errors/refresh-token-expired",
  "title": "Refresh Token Expired",
  "status": 401,
  "detail": "The refresh token has expired and a new login is required"
}
```

## Logout

Delete the session to invalidate all tokens.

### Endpoint

```http
DELETE /sessions/{sessionId}
```

### Example Request

```http
DELETE /v1/sessions/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <access_token>
```

### Success Response

**Status:** `204 No Content`

No response body.

### Error Responses

**Session Not Found (404):**

```json
{
  "type": "/errors/session-not-found",
  "title": "Session Not Found",
  "status": 404,
  "detail": "The requested session could not be found"
}
```

## Authentication Flow Diagram

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DB
    participant Email

    Note over Client,DB: Password Login Flow
    Client->>API: POST /auth/login {email, password}
    API->>DB: Verify credentials
    DB-->>API: User found
    API->>API: Generate tokens
    API-->>Client: 201 {accessToken, refreshToken, user}

    Note over Client,Email: OTP Login Flow
    Client->>API: POST /auth/otp {email}
    API->>API: Generate 6-digit code
    API->>DB: Store hashed code
    API->>Email: Send verification email
    API-->>Client: 204 No Content
    Client->>API: POST /auth/otp/verify {email, code}
    API->>DB: Verify code
    DB-->>API: Code valid
    API->>API: Generate tokens
    API-->>Client: 200 {accessToken, refreshToken, user}

    Note over Client,DB: API Request
    Client->>API: GET /profile (Authorization: Bearer token)
    API->>API: Validate token
    API->>DB: Fetch user data
    DB-->>API: User data
    API-->>Client: 200 {profile}

    Note over Client,DB: Token Refresh
    Client->>API: POST /sessions/{id}/refresh {refreshToken}
    API->>API: Validate refresh token
    API->>API: Generate new tokens
    API-->>Client: 200 {accessToken, refreshToken}

    Note over Client,DB: Logout
    Client->>API: DELETE /sessions/{id}
    API->>DB: Delete session
    API-->>Client: 204 No Content
```

## Best Practices

### Token Storage

| Platform  | Recommended Storage                      |
| --------- | ---------------------------------------- |
| Web (SPA) | Memory or HTTP-only cookies              |
| Mobile    | Secure storage (Keychain/Keystore)       |
| Server    | Environment variables or secrets manager |

**Avoid:** localStorage, sessionStorage (vulnerable to XSS)

### Token Refresh Strategy

Implement proactive token refresh to avoid request failures:

```typescript
const TOKEN_REFRESH_MARGIN = 60; // seconds before expiry

function shouldRefreshToken(expiresAt: number): boolean {
  const now = Math.floor(Date.now() / 1000);
  return now >= expiresAt - TOKEN_REFRESH_MARGIN;
}
```

### Error Handling

Handle authentication errors gracefully:

```typescript
async function authenticatedRequest(url: string, options: RequestInit) {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      Authorization: `Bearer ${getAccessToken()}`,
    },
  });

  if (response.status === 401) {
    // Try to refresh token
    const refreshed = await refreshToken();
    if (refreshed) {
      // Retry the request
      return authenticatedRequest(url, options);
    }
    // Redirect to login
    redirectToLogin();
  }

  return response;
}
```

### Session Management

- Store the session ID to enable logout
- Implement "logout all devices" by deleting all user sessions
- Handle concurrent sessions appropriately

## Security Considerations

1. **HTTPS Only** — Never send tokens over unencrypted connections
2. **Token Rotation** — Refresh tokens are single-use and rotated
3. **Short Expiry** — Access tokens expire quickly (15 minutes)
4. **Secure Storage** — Use platform-appropriate secure storage
5. **Logout on Sensitive Actions** — Invalidate sessions on password change

## Next Steps

- [Error Handling](./errors.md) — RFC 7807 error format details
- [Quick Start](./index.md) — Make your first API call
- [OpenAPI Specification](../openapi.yaml) — Full API reference
