# PRD-011: Sailing Brief MVP

## Product Overview

**Feature**: Region-based Sailing Brief  
**Version**: MVP (v6.6.0)  
**Status**: Implemented  
**Release Date**: 2026-02-11

## Goal

Deliver pre-generated sailing briefs for selected regions three times per day using AI with web search, providing sailors with up-to-date local sailing conditions, navigation tips, and practical information.

## Scope

### In Scope (MVP)

- ✅ Pre-generation of sailing briefs 3× daily (morning/noon/evening)
- ✅ AI-powered content with OpenAI Responses API + web search
- ✅ EN/PL language support (independent generation)
- ✅ Consumer REST endpoint: `GET /v1/sailing-briefs` (JWT authenticated)
- ✅ Admin list endpoint: `GET /v1/sailing-briefs/list` (admin role)
- ✅ Admin regenerate endpoint: `POST /v1/sailing-briefs/regenerate` (admin role)
- ✅ BullMQ worker with retry and DLQ
- ✅ Hourly distributed cron scheduler
- ✅ CLI command for manual generation
- ✅ Initial rollout: HR (Croatia), GR (Greece)
- ✅ Immutable historical versioning
- ✅ Timezone-aware scheduling with DST support

### Out of Scope (Future)

- ❌ Personalization per user
- ❌ Integration with internal post data
- ❌ Push notifications on new brief
- ❌ Granular sub-region briefs
- ❌ User feedback/ratings

## Technical Decisions

### MVP Decisions

| Decision                    | Rationale                                              |
| --------------------------- | ------------------------------------------------------ |
| **OpenAI Responses API**    | Supports structured output + web search in single call |
| **Web search enabled**      | Ensures current, real-time sailing conditions          |
| **3 time slots/day**        | Balance between freshness and generation cost          |
| **Region-level only**       | Simpler MVP; sub-region expansion deferred             |
| **Immutable history**       | Audit trail, version comparison, rollback capability   |
| **JWT authentication**      | Consistent with app security model                     |
| **Admin-only regeneration** | Quality control, prevent abuse                         |

### Data Model Decisions

| Decision                     | Rationale                                             |
| ---------------------------- | ----------------------------------------------------- |
| **PostgreSQL storage**       | Relational data, complex queries, region FK integrity |
| **9 content fields**         | Granular sections for UI flexibility                  |
| **Markdown format**          | Rich formatting, easy rendering                       |
| **Version incrementing**     | Per-slot versioning allows multiple regenerations     |
| **expires_at informational** | Always serve latest, expiry is UI hint only           |

### Architecture Decisions

| Decision                                  | Rationale                                       |
| ----------------------------------------- | ----------------------------------------------- |
| **Separate worker process**               | Isolate heavy AI work from API server           |
| **BullMQ over direct AI**                 | Retry logic, DLQ, monitoring, rate limiting     |
| **Distributed cron lock**                 | Multi-instance deployment safety                |
| **Deterministic job IDs**                 | Prevents duplicate scheduled generations        |
| **Configurable AI timeout (default 90s)** | Allows web search attempt + fallback generation |

## User Stories

### As a Sailor

> "I want to check current sailing conditions for my region before heading out, so I can plan my route safely."

**Solution**: `GET /v1/sailing-briefs?regionCode=HR` returns latest brief with weather, routes, and safety tips.

### As an Admin

> "I want to review what sailing briefs were generated and regenerate with updated focus if needed."

**Solution**: Admin list endpoint shows history; regenerate endpoint allows custom AI prompts.

### As a Developer

> "I want to manually trigger sailing brief generation for testing or to fill gaps from scheduler downtime."

**Solution**: CLI command `sailing-brief:generate-now` enqueues generation for all enabled regions.

## Requirements

### Functional Requirements

| ID   | Requirement                                  | Status |
| ---- | -------------------------------------------- | ------ |
| FR-1 | Generate briefs 3× daily for enabled regions | ✅     |
| FR-2 | Support EN/PL languages independently        | ✅     |
| FR-3 | Consumer endpoint returns latest brief       | ✅     |
| FR-4 | Admin can view historical briefs             | ✅     |
| FR-5 | Admin can regenerate with custom prompt      | ✅     |
| FR-6 | CLI command for manual generation            | ✅     |
| FR-7 | Include safety disclaimer in all briefs      | ✅     |
| FR-8 | Timezone-aware slot detection                | ✅     |

### Non-Functional Requirements

| ID    | Requirement                              | Status |
| ----- | ---------------------------------------- | ------ |
| NFR-1 | API response < 200ms for cached brief    | ✅     |
| NFR-2 | Worker handles AI timeout gracefully     | ✅     |
| NFR-3 | Scheduler idempotent across restarts     | ✅     |
| NFR-4 | Failed jobs moved to DLQ after 3 retries | ✅     |
| NFR-5 | Sentry monitoring for all errors         | ✅     |
| NFR-6 | Database indexes optimize retrieval      | ✅     |

## API Contract

### GET /v1/sailing-briefs

**Authentication**: JWT required  
**Language**: Resolved from `Accept-Language` header (en/pl)

**Request**:

```http
GET /v1/sailing-briefs?regionCode=HR
Accept-Language: pl,en;q=0.9
```

**Response 200**:

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "regionCode": "HR",
  "language": "pl",
  "timeSlot": "morning",
  "localDate": "2026-02-11",
  "generatedAt": "2026-02-11T05:01:23Z",
  "expiresAt": "2026-02-11T12:00:00+01:00",
  "version": 1,
  "generationSource": "auto",
  "shortDescription": "Doskonałe warunki żeglarskie...",
  "fullDescription": "...",
  "weather": "...",
  "berth": "...",
  "route": "...",
  "tips": "...",
  "marina": "...",
  "food": "...",
  "place": "..."
}
```

**Errors**:

- `422`: Missing/invalid `regionCode` parameter
- `401`: Missing/invalid JWT
- `404`: No brief available for provided `regionCode` (including unknown/disabled regions)

### GET /v1/sailing-briefs/list

**Authentication**: JWT + Admin role  
**Language**: Resolved from `Accept-Language` (filters results)

**Query Parameters**:

- `regionCode` (optional)
- `timeSlot` (optional)
- `generationSource` (optional)
- `limit` (default 20, max 100)
- `offset` (default 0)

**Response 200**:

```json
{
  "data": [
    { ...SailingBriefResponse... }
  ],
  "meta": {
    "total": 42,
    "limit": 20,
    "offset": 0
  }
}
```

### POST /v1/sailing-briefs/regenerate

**Authentication**: JWT + Admin role  
**Response**: HTTP 202 Accepted

**Request**:

```json
{
  "regionCodes": ["HR", "GR"],
  "prompt": "Focus on family-friendly activities"
}
```

**Response 202**:

```json
{
  "jobs": [
    { "regionCode": "HR", "language": "en", "timeSlot": "morning" },
    { "regionCode": "HR", "language": "pl", "timeSlot": "morning" },
    { "regionCode": "GR", "language": "en", "timeSlot": "noon" },
    { "regionCode": "GR", "language": "pl", "timeSlot": "noon" }
  ]
}
```

**Errors**:

- `400`: Invalid region codes or disabled regions
- `401`: Missing/invalid JWT
- `403`: Non-admin user

## Rollout Plan

### Phase 1: MVP (Current)

**Regions**: HR (Croatia), GR (Greece)  
**Languages**: EN, PL  
**Generation**: Scheduled only (auto)

### Phase 2: Expansion (Future)

- Add more Mediterranean regions (IT, ES, FR)
- Add Baltic Sea regions (PL, DE, SE, DK)
- Enable manual admin regeneration for all regions

### Phase 3: Integration (Future)

- Integrate user-generated sailing reports
- Add personalization based on user preferences
- Push notifications for new briefs

## Success Metrics

### MVP Metrics

- ✅ Brief generation success rate > 95%
- ✅ API latency < 200ms (p95)
- ✅ Worker uptime > 99%
- ⏳ User engagement: brief views per active user (to be measured)

### Future Metrics

- Brief quality ratings from users
- Admin regeneration frequency
- User retention impact

## Implementation Timeline

- **2026-02-11**: MVP implementation complete
  - Database migration
  - AI integration with web search
  - Worker process
  - API endpoints
  - Unit and E2E tests
  - Documentation

## Risks and Mitigations

| Risk                   | Impact | Mitigation                                          | Status         |
| ---------------------- | ------ | --------------------------------------------------- | -------------- |
| OpenAI API rate limits | High   | Queue concurrency limit, exponential backoff        | ✅ Implemented |
| Web search timeout     | Medium | Request timeout + fallback to pure model generation | ✅ Implemented |
| Scheduler downtime     | Low    | CLI manual trigger available                        | ✅ Implemented |
| Duplicate generation   | Low    | Deterministic job IDs, database uniqueness          | ✅ Implemented |
| Worker crash           | Medium | BullMQ persistence, automatic restart               | ✅ Implemented |

## Dependencies

- **OpenAI API**: Required for content generation
- **Redis**: Required for BullMQ and distributed locks
- **PostgreSQL**: Required for brief storage
- **Luxon**: Required for timezone calculations

## Compliance

- **Data Privacy**: Briefs are public information (no personal data)
- **Content Safety**: Safety disclaimer included in all briefs
- **API Standards**: RFC 7807 for error responses
- **i18n**: EN/PL translations for all errors

## Related Documentation

- [Technical Architecture](../sailing-brief/index.md)
- [OpenAPI Specification](../openapi.yaml)
- [CHANGELOG](../../CHANGELOG.md)
- [Task Specification](../../tasks/sailing-brief.md)
