# Architecture

This document provides a high-level overview of the SkipperClub API architecture, its components, and data flow.

## System Overview

```mermaid
flowchart TB
    subgraph Clients
        WEB[Web App]:::trigger
        MOB[Mobile App]:::trigger
        EXT[Third-party]:::trigger
    end

    subgraph API["SkipperClub API"]
        REST[REST API]:::state
        WS[WebSocket Gateway]:::state
    end

    subgraph Data["Data Layer"]
        PG[(PostgreSQL)]:::success
        MG[(MongoDB)]:::success
    end

    subgraph Storage
        R2[Cloudflare R2]:::success
    end

    WEB --> REST
    WEB --> WS
    MOB --> REST
    MOB --> WS
    EXT --> REST

    REST --> PG
    REST --> MG
    REST --> R2

    WS --> MG

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

## Technology Stack

### Backend Platform

| Technology     | Version | Purpose               |
| -------------- | ------- | --------------------- |
| **Node.js**    | 22      | JavaScript runtime    |
| **NestJS**     | 11      | Application framework |
| **TypeScript** | 5       | Type-safe development |

The backend follows these architectural patterns:

- **CQRS** — Commands for writes, Queries for reads via `@nestjs/cqrs`
- **Event-driven** — Domain events for decoupled communication
- **Repository pattern** — Abstracted data access layer
- **Domain-driven design** — Module boundaries aligned with business domains

### Databases

```mermaid
flowchart LR
    subgraph PostgreSQL["PostgreSQL 16"]
        U[Users]:::state
        C[Cruises]:::state
        F[Friends]:::state
        R[Reviews]:::state
        P[Posts]:::state
        N[Notifications]:::state
        S[Sessions]:::state
    end

    subgraph MongoDB["MongoDB 8"]
        CH[Chats]:::trigger
        M[Messages]:::trigger
    end

    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
```

| Database       | Purpose                                                                                    |
| -------------- | ------------------------------------------------------------------------------------------ |
| **PostgreSQL** | Primary relational data — users, cruises, friends, reviews, posts, notifications, sessions |
| **MongoDB**    | Chat and messaging — optimized for real-time conversation workloads                        |

> **Note:** Redis backs the BullMQ job queues used for email and push notification delivery (including dead-letter queues). It is not yet used for caching or session storage.

### File Storage

| Service           | Purpose                               |
| ----------------- | ------------------------------------- |
| **Cloudflare R2** | Durable media storage (S3-compatible) |

Media uploads use pre-signed URLs for secure, direct client uploads:

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant R2 as Cloudflare R2

    Client->>API: Request upload URL
    API->>API: Generate pre-signed URL
    API-->>Client: Return upload URL
    Client->>R2: Upload file directly
    Client->>API: Confirm upload
    API->>API: Process and associate media
```

## API Architecture

### REST API

The REST API follows OpenAPI 3.1 specification defined in `docs/openapi.yaml`.

Key characteristics:

- **Versioned** — URL path versioning (`/v1/...`)
- **Resource-oriented** — RESTful endpoint design
- **JSON** — Request and response bodies in JSON format
- **RFC 7807** — Error responses in Problem Details format

### WebSocket API

Real-time features use WebSocket connections defined in `docs/asyncapi.yaml`.

Key features:

- **Socket.io** — Transport layer with fallback support
- **JWT authentication** — Token-based connection authorization
- **Namespaces** — Logical separation of event types
- **Rooms** — Chat-specific message broadcasting

```mermaid
flowchart LR
    subgraph WebSocket Events
        direction TB
        IN[Incoming]:::trigger
        OUT[Outgoing]:::success
    end

    subgraph Incoming
        JC[chat:join]:::trigger
        LC[chat:leave]:::trigger
        SM[message:send]:::trigger
        MR[message:read]:::trigger
        TY[chat:typing]:::trigger
    end

    subgraph Outgoing
        NM[message:new]:::success
        MS[message:sent]:::success
        RR[message:read:receipt]:::success
        TI[chat:typing]:::success
        PU[presence:update]:::success
    end

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

## Module Structure

The application is organized by domain modules:

```
src/modules/
├── ai/             # Audio transcription & AI features
├── alerts/         # Navigational alerts & warnings
├── auth/           # JWT authentication & sessions
├── check-ins/      # User location check-ins
├── cruises/        # Cruise organization
├── debug/          # Sentry debug endpoints (non-production only)
├── email/          # Email queue & templates
├── filestorage/    # File storage management
├── friends/        # Friend system
├── geocoder/       # Reverse geocoding with caching
├── invitations/    # App invitations (codes & links)
├── map/            # Unified map items endpoint
├── media/          # Media sharing
├── messages/       # Chat system
├── notifications/  # Notification center
├── posts/          # Social posts
├── push/           # Push notifications (APNs/FCM)
├── redis/          # Redis connection module
├── regions/        # Sailing regions
├── reviews/        # Rating system
├── sailing-brief/  # AI-generated sailing briefs
├── spots/          # Sailing spots & validity voting
└── users/          # User profiles
```

Each module follows CQRS pattern:

```
src/modules/{module}/
├── commands/
│   ├── handlers/       # Command handlers (writes)
│   └── impl/           # Command definitions
├── queries/
│   ├── handlers/       # Query handlers (reads)
│   └── impl/           # Query definitions
├── dto/                # Data transfer objects
├── exceptions/         # Module-specific exceptions
├── {module}.controller.ts
├── {module}.module.ts
└── {module}.service.ts
```

## Security Architecture

### Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant API
    participant DB as PostgreSQL

    Client->>API: POST /auth/login (email, password)
    API->>DB: Verify credentials
    DB-->>API: User found
    API->>API: Generate JWT tokens
    API-->>Client: Access token + Refresh token

    Note over Client,API: Access token expires in 15 minutes

    Client->>API: POST /sessions/{id}/refresh
    API->>API: Verify refresh token
    API-->>Client: New access token + refresh token
```

### Token Specifications

| Token             | Lifetime   | Purpose                   |
| ----------------- | ---------- | ------------------------- |
| **Access Token**  | 15 minutes | API request authorization |
| **Refresh Token** | 7 days     | Obtain new access tokens  |

### Authorization

- Bearer token required for most endpoints
- Token contains user ID and session ID
- Resource access validated against ownership and permissions

## Error Handling

All errors follow RFC 7807 Problem Details format:

```json
{
  "type": "/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "The requested resource was not found"
}
```

Key aspects:

- **Consistent format** across all modules
- **i18n support** — Error messages in EN/PL
- **Validation errors** include field-level violations

## Scalability Considerations

### Current Design

- Single-instance deployment
- Vertical scaling approach
- Local session management

### Future Considerations

- Horizontal scaling with Redis session store
- Database read replicas
- CDN for static assets
- Message queue for async processing

## Development & Deployment

### Environments

| Environment     | Purpose                                   |
| --------------- | ----------------------------------------- |
| **Development** | Local development with hot reload         |
| **Test**        | Automated testing with isolated databases |
| **Staging**     | Pre-production verification               |
| **Production**  | Live environment                          |

### CI/CD Pipeline

```mermaid
flowchart LR
    A[Push]:::trigger --> B[Lint & Format]:::state
    B --> C[TypeScript Check]:::state
    C --> D[Unit Tests]:::state
    D --> E[E2E Tests]:::state
    E --> F[Build Docker]:::state
    F --> G[Deploy]:::success

    classDef trigger fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF
    classDef state fill:#6B7280,stroke:#374151,color:#FFFFFF
    classDef success fill:#10B981,stroke:#047857,color:#FFFFFF
```

### Infrastructure

| Component            | Technology                      |
| -------------------- | ------------------------------- |
| **Reverse Proxy**    | Traefik v3 with automatic HTTPS |
| **Containerization** | Docker & Docker Compose         |
| **CI/CD**            | GitHub Actions / GitLab CI      |

## Next Steps

- [Quick Start](../getting-started/index.md) — Make your first API call
- [Authentication](../getting-started/authentication.md) — Learn about JWT tokens
- [Tech Stack](../technical/tech-stack.md) — Detailed technology information
