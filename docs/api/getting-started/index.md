# Quick Start

Get started with the SkipperClub API in minutes. This guide walks you through authentication and making your first API call.

## Base URL

All API endpoints are prefixed with:

```
https://api.skipperclub.app/v1
```

## Prerequisites

To use the SkipperClub API, you need:

1. A registered user account (email and password)
2. An HTTP client (curl, Postman, or your application)

## Step 1: Login

Authenticate by creating a session. This returns access and refresh tokens.

### Request

```http
POST /v1/auth/login HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json

{
  "email": "your.email@example.com",
  "password": "your-password"
}
```

### Response

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900,
  "user": {
    "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
    "email": "your.email@example.com",
    "name": "Your Name",
    "avatarUrl": null
  }
}
```

Save the `accessToken` — you'll need it for all subsequent requests.

## Step 2: Make Authenticated Requests

Include the access token in the `Authorization` header:

```http
GET /v1/profile HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <your-access-token>
```

### Example: Get Your Profile

```http
GET /v1/profile HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Response:

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "name": "Your Name",
  "email": "your.email@example.com",
  "avatarUrl": "https://cdn.example.com/avatars/user.jpg",
  "bio": "Sailing enthusiast",
  "city": "Gdańsk",
  "country": "PL",
  "cruisesCount": 7,
  "currentUserFriendshipStatus": "none",
  "facebookUrl": null,
  "friendsCount": 42,
  "instagramUsername": "@your_sailing",
  "postsCount": 15,
  "sailingExperience": "intermediate",
  "tiktokUsername": null,
  "whatsappNumber": "+48123456789",
  "sailingLicenses": "Yacht Skipper",
  "yearsOfExperience": 5,
  "languagesSpoken": ["pl", "en"],
  "preferredVoyageStyles": ["coastal", "offshore"],
  "createdAt": "2024-01-15T10:30:00.000Z",
  "updatedAt": "2024-06-20T14:22:00.000Z"
}
```

### Example: List Available Cruises

```http
GET /v1/cruises?limit=10 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <your-access-token>
```

Response:

```json
{
  "cruises": [
    {
      "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
      "title": "Mediterranean Adventure",
      "description": "7-day sailing trip along the Croatian coast",
      "departureDate": "2025-07-15",
      "arrivalDate": "2025-07-22",
      "departurePort": {
        "name": "Split, Croatia",
        "coordinates": { "lat": 43.5081, "lng": 16.4402 }
      },
      "arrivalPort": {
        "name": "Dubrovnik, Croatia",
        "coordinates": { "lat": 42.6507, "lng": 18.0944 }
      },
      "stops": [],
      "maxParticipants": 6,
      "participantsCount": 3,
      "costPerPerson": 1500,
      "currency": "EUR",
      "vessel": "Bavaria 46 Cruiser",
      "vesselType": "SAILING_YACHT",
      "currentUserRole": "none",
      "organizer": {
        "id": "018fa2e4-1234-5678-9abc-def012345678",
        "name": "Captain Smith",
        "avatarUrl": null
      }
    }
  ],
  "total": 42,
  "limit": 10,
  "offset": 0
}
```

## Common Headers

Include these headers in your requests:

| Header            | Value              | Required           | Description                     |
| ----------------- | ------------------ | ------------------ | ------------------------------- |
| `Authorization`   | `Bearer <token>`   | Yes\*              | Access token for authentication |
| `Content-Type`    | `application/json` | For POST/PUT/PATCH | Request body format             |
| `Accept-Language` | `en` or `pl`       | No                 | Language for error messages     |

\*Not required for login and public endpoints.

## Token Expiration

- **Access tokens** expire after **15 minutes**
- **Refresh tokens** expire after **7 days**

When your access token expires, use the refresh token to get a new one:

```http
POST /v1/sessions/<session-id>/refresh HTTP/1.1
Host: api.skipperclub.app
Content-Type: application/json

{
  "refreshToken": "<your-refresh-token>"
}
```

See [Authentication](./authentication.md) for complete token management details.

## Error Handling

All errors follow RFC 7807 Problem Details format:

```json
{
  "type": "/errors/authentication-required",
  "title": "Authentication Required",
  "status": 401,
  "detail": "Invalid or expired access token"
}
```

See [Error Handling](./errors.md) for complete error documentation.

## Quick Reference

### Common Endpoints

| Method   | Endpoint           | Description                   |
| -------- | ------------------ | ----------------------------- |
| `POST`   | `/auth/login`      | Login with email and password |
| `POST`   | `/auth/otp`        | Request OTP code via email    |
| `POST`   | `/auth/otp/verify` | Verify OTP and get tokens     |
| `DELETE` | `/sessions/{id}`   | Logout (delete session)       |
| `GET`    | `/profile`         | Get your profile              |
| `PUT`    | `/profile`         | Update your profile           |
| `GET`    | `/cruises`         | List cruises                  |
| `POST`   | `/cruises`         | Create a cruise               |
| `GET`    | `/friends`         | List friends                  |
| `POST`   | `/friend-requests` | Send friend request           |

### HTTP Status Codes

| Code  | Meaning                                 |
| ----- | --------------------------------------- |
| `200` | Success                                 |
| `201` | Created                                 |
| `204` | No Content (successful deletion)        |
| `400` | Bad Request                             |
| `401` | Unauthorized (missing or invalid token) |
| `403` | Forbidden (insufficient permissions)    |
| `404` | Not Found                               |
| `422` | Validation Error                        |

## Next Steps

- [Authentication](./authentication.md) — Complete auth flow documentation
- [Error Handling](./errors.md) — RFC 7807 error format details
- [Key Concepts](../overview/concepts.md) — Learn about cruises, participants, reviews
- [OpenAPI Specification](../openapi.yaml) — Full API reference
