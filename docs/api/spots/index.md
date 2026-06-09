# Spots Directory

The Spots Directory is a community-driven registry of sailing spots. Any authenticated user may propose additions and changes; only administrators can approve and publish them.

## Data Model

```
Spot
├── name, nameNormalized, coordinateKey
├── coordinates (PostGIS geometry Point SRID 4326)
├── deletedAt (soft-delete)
├── MarinaPhoneContact[] (label, phone, extension, phoneIdentity)
└── MarinaRadioChannel[] (name, channelKind, vhfChannel/frequencyMhz, isPrimary)
```

`MarinaChangeRequest` stores proposed changes with a discriminated `type` and `payload` (JSONB), plus an admin-controlled `status` lifecycle.

## Normalization Rules

| Field               | Normalisation                                                       |
| ------------------- | ------------------------------------------------------------------- |
| `nameNormalized`    | trim → collapse whitespace → lowercase                              |
| `coordinateKey`     | `${lat.toFixed(6)},${lng.toFixed(6)}`                               |
| `phoneNormalized`   | strip spaces, dashes, parentheses (keeps leading `+`)               |
| `phoneIdentity`     | `${phoneNormalized}` or `${phoneNormalized}x${extensionNormalized}` |
| `channelNormalized` | `vhf:N` or `mhz:NNN.NNN`                                            |

## Deduplication Rules

| Constraint                         | Scope            | HTTP |
| ---------------------------------- | ---------------- | ---- |
| Same `nameNormalized` within 100 m | Active spots     | 409  |
| Same `phoneIdentity`               | Per spot, active | 409  |
| Same `channelNormalized`           | Per spot, active | 409  |
| `isPrimary = true`                 | One per spot     | 409  |

## Admin Workflows

### Direct CRUD

| Method   | Endpoint        | Description                                  |
| -------- | --------------- | -------------------------------------------- |
| `POST`   | `/v1/spots`     | Create spot with optional contacts/channels  |
| `PATCH`  | `/v1/spots/:id` | Update name, coordinates, contacts, channels |
| `DELETE` | `/v1/spots/:id` | Soft-delete spot and all its sub-records     |

### Change Request Approval

1. Admin calls `PATCH /v1/spot-change-requests` with `action: "approve"` or `"reject"`.
2. On **approve**, the handler re-validates the payload against live data before applying it (prevents stale conflicts).
3. On **reject**, the canonical data is unchanged; `rejectionReason` is stored.
4. All 9 change request types are applied transactionally; failures return a per-item `error` in the batch result.

## User Workflows

### Proposing Changes

Any authenticated user can call `POST /v1/spot-change-requests`:

```json
{
  "type": "SPOT_CREATE",
  "payload": {
    "name": "Neptun Spot",
    "coordinates": { "lat": 54.352, "lng": 18.653 }
  }
}
```

Delete-type requests (`*_DELETE_REQUEST`) require a `comment` explaining the reason.

### Listing Own Requests

`GET /v1/spot-change-requests` — returns only the caller's own requests (all statuses).

### Cancelling a Pending Request

```json
PATCH /v1/spot-change-requests
{ "ids": ["<crId>"], "action": "cancel" }
```

Users may only cancel their own pending requests. Attempting to cancel another user's request returns a per-item `403` in the batch result.

## Geo Search

`GET /v1/spots?lat=54.35&lng=18.65&distance=5` returns spots within 5 km of the given point, sorted by `distanceMeters` ascending. The `lat`, `lng`, and `distance` parameters must all be provided together.

For **map rendering** (viewport-scoped queries with optional clustering), use the unified map endpoint instead — see [Map](../map/index.md). It returns lightweight spot map items alongside posts and check-ins in a single request.

## API Endpoints

| Method   | Path                       | Auth       | Summary                            |
| -------- | -------------------------- | ---------- | ---------------------------------- |
| `GET`    | `/v1/spots`                | User       | List spots (+ optional geo search) |
| `GET`    | `/v1/spots/:id`            | User       | Get spot by ID                     |
| `POST`   | `/v1/spots`                | Admin      | Create spot                        |
| `PATCH`  | `/v1/spots/:id`            | Admin      | Update spot aggregate              |
| `DELETE` | `/v1/spots/:id`            | Admin      | Soft-delete spot                   |
| `POST`   | `/v1/spot-change-requests` | User       | Propose a change                   |
| `GET`    | `/v1/spot-change-requests` | User/Admin | List change requests               |
| `PATCH`  | `/v1/spot-change-requests` | User/Admin | Batch approve/reject/cancel        |

See [`docs/openapi.yaml`](../openapi.yaml) for the full specification.
