# SkipperClub API Documentation

Welcome to the SkipperClub API documentation. This guide helps developers integrate with the SkipperClub platform — a sailing social network connecting skippers organizing sea voyages with people looking for spots on board.

## Documentation Sections

### Overview

Learn about the platform and its core concepts.

- [Platform Introduction](./overview/index.md) — What is SkipperClub and who it's for
- [Product Requirements](./prd/index.md) — PRD with user stories and success metrics
- [Key Concepts](./overview/concepts.md) — Cruises, participants, reviews, and chats
- [Architecture](./overview/architecture.md) — System components and data flow

### Getting Started

Start integrating with the API quickly.

- [Quick Start](./getting-started/index.md) — Make your first API call
- [Authentication](./getting-started/authentication.md) — JWT tokens and session management
- [Error Handling](./getting-started/errors.md) — RFC 7807 Problem Details format

### API Specifications

Machine-readable API contracts for automated tooling.

- [OpenAPI Specification](./openapi.yaml) — REST API contract (OpenAPI 3.1)
- [AsyncAPI Specification](./asyncapi.yaml) — WebSocket events contract (AsyncAPI 2.6)

### Feature Modules

Detailed documentation for each API module.

- [Authentication](./authentication/index.md) — JWT auth, sessions, token refresh
- [Invitations](./invitations/index.md) — User invitations and invite-only registration
- [Users](./users/index.md) — User profiles, settings, avatars
- [Cruises](./cruises/index.md) — Voyage organization and management
- [Friends](./friends/index.md) — Friend requests and connections
- [Messages](./messages/index.md) — Chat system and real-time messaging
- [Posts](./posts/index.md) — Social feed, likes, comments
- [Reviews](./reviews/index.md) — Post-voyage blind review system
- [Notifications](./notifications/index.md) — Notification center
- [Notification Settings](./notifications/notification-settings.md) — User-level notification channel preferences
- [Push Notifications](./notifications/push-notifications.md) — APNs/FCM setup and mobile push flow
- [Regions](./regions/index.md) — Sailing regions
- [Sailing Brief](./sailing-brief/index.md) — AI-generated regional sailing briefings
- [Media](./media/index.md) — File uploads and media processing
- [Audio](./audio/index.md) — Audio transcription
- [Geocoder](./geocoder/index.md) — Location search and reverse geocoding
- [Check-ins](./check-ins/index.md) — Latest user location presence and nearby discovery
- [Spots](./spots/index.md) — Community-driven spots directory with admin approval workflow
- [Map](./map/index.md) — Unified map view across posts, spots, check-ins, and alerts
- [Alerts](./alerts/index.md) — User-submitted navigation alerts (weather, obstructions, regattas, …)
- [Email](./email/index.md) — Queue-based email delivery system

### Reference

Focused reference documents for enums and flows, optimized for AI context and quick lookup.

- [Reference Index](./reference/index.md) — All enums, state machines, and flows
- Enums: [Cruise Types](./reference/enums/cruise-types.md), [Vessel Types](./reference/enums/vessel-types.md), [Chat Types](./reference/enums/chat-types.md), [Notification Types](./reference/enums/notification-types.md), [Sailing Experience](./reference/enums/sailing-experience.md), [Alert Categories](./reference/enums/alert-categories.md)
- Flows: [Participant States](./reference/flows/cruise-participant-state-flow.md), [Chat Flows](./reference/flows/cruise-chat-flows.md), [Notifications](./reference/flows/notification-flows.md), [Friend Requests](./reference/flows/friend-request-flow.md), [Blind Reviews](./reference/flows/blind-review-flow.md), [Alert Ownership](./reference/flows/alert-ownership-flow.md)

### Technical Reference

Setup and deployment information.

- [Technical Overview](./technical/index.md) — System requirements and environments
- [Tech Stack](./technical/tech-stack.md) — Technologies and frameworks
- [Docker Setup](./technical/docker.md) — Container deployment
- [Development](./technical/development.md) — Local development setup
- [Deployment](./technical/deployment.md) — CI/CD pipelines and production deployment

## Base URL

All API endpoints are prefixed with:

```
https://api.skipperclub.app/v1
```

## Authentication

Most endpoints require a Bearer token in the `Authorization` header:

```http
Authorization: Bearer <access_token>
```

See [Authentication](./getting-started/authentication.md) for details on obtaining tokens.

## Content Negotiation

### Request Format

Send JSON payloads with:

```http
Content-Type: application/json
```

### Response Format

The API returns JSON responses. Errors use RFC 7807 Problem Details:

```http
Content-Type: application/problem+json
```

### Language

Request localized error messages using:

```http
Accept-Language: en
```

Supported languages: `en` (English), `pl` (Polish).

## Versioning

The API uses URL path versioning. The current version is `v1`.

## Support

For API questions and issues, contact the SkipperClub team at contact@skipperclub.com.
