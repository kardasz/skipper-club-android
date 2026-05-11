# User Roles

This document describes the user role enum values used for authorization and access control.

## Overview

The `UserRole` enum defines the authorization levels for users. Roles are assigned during registration and can be used to restrict access to certain features or endpoints.

## Available Roles

### `user`

**Standard User**
The default role assigned to all new users upon registration. Standard users have access to all public features of the platform.

**Capabilities:**

- Create and manage their own profile
- Create and manage posts
- Participate in cruises
- Send and receive messages
- Manage friend connections
- Access all public content

### `admin`

**Administrator**
An elevated role with administrative privileges. Admins have access to management features and can perform actions that regular users cannot.

**Capabilities:**

- All standard user capabilities
- Access to admin-only endpoints (when implemented)
- Ability to moderate content
- Access to platform management features

## Quick Reference Table

| Role    | Description            | Default | Elevated Privileges |
| ------- | ---------------------- | ------- | ------------------- |
| `user`  | Standard platform user | Yes     | No                  |
| `admin` | Administrative user    | No      | Yes                 |

## Usage Notes

- The role is included in the JWT token payload as `role`
- The role is returned in user profile and session responses
- Role-based access control uses the `@Roles()` decorator on protected endpoints
- New users are assigned `user` role by default
- Admin roles must be assigned manually (database update or admin panel)

## JWT Token Payload

The role is included in both access and refresh tokens:

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

## Response Examples

### Session Response

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
    "avatarUrl": null,
    "role": "user"
  }
}
```

### Profile Response

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "name": "Jan Kowalski",
  "email": "jan.kowalski@email.com",
  "avatarUrl": "https://cdn.skipperclub.com/avatars/user-123.jpg",
  "role": "user",
  ...
}
```

## Error Responses

When a user attempts to access a role-protected endpoint without the required role:

**403 Forbidden:**

```json
{
  "type": "/errors/forbidden-role",
  "title": "Forbidden",
  "status": 403,
  "detail": "You do not have permission to perform this action"
}
```

## Related

- [Authentication](../../authentication/index.md) - JWT authentication documentation
- [Users API](../../users/index.md) - User profile documentation
- [OpenAPI Specification](../../openapi.yaml) - Full API reference
