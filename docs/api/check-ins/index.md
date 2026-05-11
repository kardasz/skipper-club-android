# Check-ins (location presence)

## Overview

Authenticated users can publish their **latest location presence** as a single check-in per account: WGS84 coordinates, an optional human-readable label, and a **server-owned** `checkedInAt` timestamp. Other authenticated users can **discover nearby active check-ins** within a configurable radius.

There is **no history**: each user has at most one row. There is **no delete** endpoint in the current scope; reads only return **fresh** check-ins so presence does not remain visible indefinitely.

## Endpoints

| Method | Path            | Description                                     |
| ------ | --------------- | ----------------------------------------------- |
| `PUT`  | `/v1/check-ins` | Create or replace the caller’s latest check-in. |
| `GET`  | `/v1/check-ins` | List active check-ins near a search center.     |

Full schemas and examples: [`openapi.yaml`](../openapi.yaml) (paths `/check-ins`).

### Example: upsert

```http
PUT /v1/check-ins HTTP/1.1
Authorization: Bearer <access_token>
Accept-Language: en
Content-Type: application/json

{
  "lat": 54.352,
  "lng": 18.6466,
  "locationName": "Gdańsk Marina"
}
```

Omit `locationName` to let the backend attempt **reverse geocoding** (see below). Response is `200 OK` with the persisted check-in (not `201`).

### Example: list nearby

```http
GET /v1/check-ins?lat=54.35&lng=18.65&distance=10&limit=20&offset=0 HTTP/1.1
Authorization: Bearer <access_token>
```

Response shape matches other list endpoints: `{ "data": [...], "meta": { "total", "limit", "offset", "hasMore" } }`. Each item includes `distanceMeters` from the search center, sorted ascending by default.

## Freshness (TTL)

`GET /v1/check-ins` only returns check-ins whose `checkedInAt` is within a **freshness window**. Default window: **24 hours**. Override with environment variable:

- `CHECK_IN_FRESHNESS_TTL_HOURS` (positive number; invalid values fall back to 24).

## Visibility

By default, **any authenticated user** can list nearby active check-ins. The **current user** is included when their own check-in falls within the radius (clients may hide self in UI if needed).

## Geocoding behavior

- If `locationName` is sent (non-empty after trim), reverse geocoding is **skipped** and the provided label is stored; `google_place_id` and `location_types` are cleared.
- If `locationName` is omitted, the server calls the configured geocoder (`GeocoderModule` / Google Maps) with `Accept-Language` for localized labels.
- Result selection prefers place types useful for sailing context (`marina`, `harbor`, `port`, `point_of_interest`, `establishment`) when present, then falls back to the first formatted address.
- If geocoding fails or is not configured, coordinates are still saved and `locationName` may be `null` (writes remain usable).

## Client notes

- Send `Accept-Language` on `PUT` when relying on reverse geocoding.
- Use `distance` between **1 and 50** (converted to meters for PostGIS `ST_DWithin`).
- Coordinates in JSON are `{ lat, lng }`; storage uses GeoJSON order `[lng, lat]` internally.

## Related documentation

- [Geocoder module](../geocoder/index.md)
- [Error handling](../getting-started/errors.md) (RFC 7807)
