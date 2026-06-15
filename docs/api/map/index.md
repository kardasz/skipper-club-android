# Map (Unified Map Items)

## Overview

The Map module exposes a single authenticated endpoint that lets mobile and web clients render every map-relevant SkipperClub object — posts, spots, check-ins, and navigation alerts — without calling each domain endpoint separately.

It returns a unified, lightweight representation of visible map objects (markers and, when needed, clusters), with type-specific attributes that the client uses to draw markers, badges, and labels.

> **Heads-up — default response change.** `navigation_alert` is included in the default `types` set. Existing `/map/items` consumers that omit `types` will start receiving alert items immediately. To opt out, send an explicit `types` parameter without `navigation_alert`.

## Endpoints

| Method | Path            | Description                                             |
| ------ | --------------- | ------------------------------------------------------- |
| `GET`  | `/v1/map/items` | List unified map items and clusters for a map viewport. |

Full schemas and examples: [`openapi.yaml`](../openapi.yaml) (path `/map/items`).

## Spatial Filtering

The endpoint accepts exactly one of two spatial modes:

### Viewport bounds (primary)

```http
GET /v1/map/items?north=54.49&south=54.39&east=18.63&west=18.50
```

- Used by every map screen that has a visible rectangle.
- All four edges must be provided together.
- `north > south` is required.
- Maximum span: `120°` per axis (after antimeridian split).
- **Antimeridian handling:** when `west > east`, the viewport is interpreted as crossing the date line. The server splits the query into two envelopes — `[west, 180]` and `[-180, east]` — and unions the results. Clients **must not** normalize longitudes or swap `east`/`west`.

### Center and radius (non-viewport)

```http
GET /v1/map/items?lat=54.441&lng=18.567&distance=20
```

- For "near me" buttons, deep links, and system shortcuts that arrive with `(lat, lng)` only.
- `lat`, `lng`, and `distance` must be provided together.
- `distance` is in kilometers, range `(0, 500]`.
- Each individual item carries `distanceMeters` in the response.

### Validation errors

Mixing both modes, providing one partially, exceeding span/radius limits, or going outside WGS84 ranges returns `422 application/problem+json`. See [Error types](#error-types) for the specific slugs.

### Client refetch cadence (soft contract)

Clients should refetch only when the camera **settles**, not on every pan/zoom frame:

- **iOS:** `onMapCameraChange(frequency: .onEnd)` + a 350 ms debounce.
- **Android:** `snapshotFlow { cameraPositionState.isMoving }.distinctUntilChanged()` + 300 ms debounce.

The `Cache-Control: private, max-age=15/60` response header is calibrated against this assumption.

## Non-Spatial Filters

| Parameter                     | Notes                                                                                                   |
| ----------------------------- | ------------------------------------------------------------------------------------------------------- |
| `types`                       | Subset of `post`, `spot`, `check_in`, `navigation_alert`. Default: all four. Empty value returns `422`. |
| `postTypes`                   | Narrow post items to specific `PostType` values. Ignored when `types` excludes `post`.                  |
| `regionCode`                  | Subtree match on post region. Same semantics as `GET /posts` (uses `getDescendantCodes`).               |
| `includeCrossRegionPostTypes` | Evergreen post types that bypass `regionCode`. Time-sensitive types are rejected.                       |
| `fromDate` / `toDate`         | ISO 8601 bounds applied to post `createdAt` and check-in `checkedInAt`. Spots untouched.                |
| `limit`                       | Maximum top-level entries (items + clusters together). Range `[1, 500]`, default `200`.                 |
| `detail`                      | `auto` (default), `markers`, or `summary`. See [Detail levels](#detail-levels).                         |

## Visibility Rules

The endpoint reuses the visibility predicates already enforced by `GET /posts`, `GET /spots`, and `GET /check-ins`:

- **Posts** — only `published`, not effectively expired, not deleted, with non-null `coordinates`. The current user's own archived/expired posts are **not** included in the map layer.
- **Spots** — only spots without a `deletedAt` value.
- **Check-ins** — only members of the freshness window (`CHECK_IN_FRESHNESS_TTL_HOURS`, default 24 hours). One per user.
- **Navigation alerts** — only alerts where `deletedAt IS NULL`, `geometry IS NOT NULL`, `anchor IS NOT NULL`, and that are **valid at request time** (`valid_from <= now()` and `valid_to >= now()`, treating a null bound as open-ended). Bounds-mode requests match alerts whose `geometry` intersects the viewport; radius-mode requests match alerts whose `anchor` lies within `distance` of the request center. Expired or not-yet-started alerts are hidden from the map but remain retrievable through `GET /alerts` (which exposes `validFrom` / `validTo` overlap filters).

The freshness window is exposed in `meta.checkInFreshnessHours` so clients can display the correct disclaimer.

## Response Shape

```json
{
  "data": [
    {
      "kind": "item",
      "type": "spot",
      "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
      "name": "Sopot Marina",
      "coordinates": { "lat": 54.441, "lng": 18.567 },
      "geometry": { "type": "Point", "coordinates": [18.567, 54.441] },
      "attributes": {
        "hasPhoneContacts": true,
        "hasRadioChannels": true,
        "phoneContactsCount": 2,
        "radioChannelsCount": 1
      }
    },
    {
      "kind": "cluster",
      "id": "cluster:u3wge:5",
      "name": "24 items",
      "coordinates": { "lat": 54.44, "lng": 18.56 },
      "geometry": { "type": "Point", "coordinates": [18.56, 54.44] },
      "count": 24,
      "types": { "post": 12, "spot": 8, "check_in": 4 },
      "bounds": { "north": 54.49, "south": 54.39, "east": 18.63, "west": 18.5 }
    }
  ],
  "meta": {
    "mode": "bounds",
    "detail": "auto",
    "totalItems": 248,
    "returnedItems": 248,
    "topLevelEntries": 42,
    "hasMoreDetail": true,
    "checkInFreshnessHours": 24,
    "appliedLimit": 200
  }
}
```

### Item contract

Every non-cluster item carries:

- `kind`: `item`
- `type`: `post`, `spot`, `check_in`, or `navigation_alert`
- `id`: the **source resource UUID** — the same identifier accepted by `GET /posts/{id}`, `GET /spots/{id}`, and the future `GET /check-ins/{id}` detail endpoints. Clients open the detail screen directly from the marker tap; no extra lookup needed.
- `name`: display label for the marker (see [Marker name rules](#marker-name-rules)).
- `coordinates`: anchor point `{ lat, lng }` used for marker placement, labels, distance display, and clustering.
- `geometry`: GeoJSON-compatible geometry (`Point`, `MultiPoint`, `Polygon`, or `MultiPolygon`). For point-based resources this duplicates `coordinates` in `[lng, lat]` order.
- `distanceMeters`: integer, rounded. Present only in center+radius mode (and in any other case where it is meaningful).
- `attributes`: lightweight, type-specific metadata. The full resource is **not** returned — clients fetch detail via the per-resource detail endpoint.

### Marker name rules

- **Post:** `locationName` when present; otherwise a short post-specific fallback suitable for a marker label.
- **Spot:** `spot.name`.
- **Check-in:** always the user's **`displayName`**, because identifying who is present is the primary purpose of a check-in marker. `locationName` is still returned under `attributes.locationName` for use as a subtitle. When the user has no `displayName`, a localized presence label is used (`"Sailor nearby"` / `"Żeglarz w pobliżu"`).
- **Navigation alert:** a short label derived from the alert category, localized via `nestjs-i18n` against the request `Accept-Language` header (`alerts.CATEGORY_LABEL_*`). For example, `category=weather` returns `"Weather alert"` for `en` and `"Ostrzeżenie pogodowe"` for `pl`. The full alert body lives in `attributes.content` — never inlined into `name`.

### Geometry

Area-based items are now in production via the `navigation_alert` source type — see [Area-based items: navigation alerts](#area-based-items-navigation-alerts).

- `Point`: `coordinates: [lng, lat]`.
- `MultiPoint`: `coordinates: [[lng, lat], ...]`.
- `Polygon`: `coordinates: [[[lng, lat], ...]]`.
- `MultiPolygon`: `coordinates: [[[[lng, lat], ...]]]`.

For polygon items, `coordinates` (on the parent item) is a representative anchor point used for labels, distance, and clustering; `geometry` holds the drawable area.

### Type-specific attributes

**Post**

```json
{
  "postType": "marina",
  "status": "published",
  "regionCode": "ADR-HR-CDAL",
  "author": { "id": "<uuid>", "displayName": "Jan K.", "avatarUrl": "…" },
  "createdAt": "2030-01-01T12:00:00Z",
  "expiresAt": "2030-01-08T12:00:00Z",
  "mediaPreview": {
    "id": "<uuid>",
    "kind": "image",
    "url": "…",
    "thumbnailUrl": null,
    "width": 1920,
    "height": 1080
  },
  "commentsCount": 4,
  "bookmarked": false
}
```

**Spot**

```json
{
  "hasPhoneContacts": true,
  "hasRadioChannels": true,
  "phoneContactsCount": 2,
  "radioChannelsCount": 1
}
```

**Check-in**

```json
{
  "user": { "id": "<uuid>", "displayName": "Jan K.", "avatarUrl": "…" },
  "checkedInAt": "2030-06-06T08:00:00Z",
  "locationName": "Sopot Pier"
}
```

**Navigation alert (user-owned)**

```json
{
  "category": "weather",
  "content": "Strong bora expected near Velebit channel.",
  "language": "en",
  "source": "user",
  "sourceId": "019dfd19-ddd8-7d23-a1f4-06b96c16a36e",
  "sourceAttributes": null,
  "user": {
    "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36e",
    "name": "Jan K.",
    "avatarUrl": "https://cdn.example/avatars/abc.jpg"
  }
}
```

The `user` author projection (`{ id, name, avatarUrl }`) is present **only**
for `source='user'` alerts so clients can render the author without a second
lookup. It is omitted entirely for official imports. See
[Navigation Alerts → Author attribute](../alerts/index.md#author-attribute-attributesuser).

**Navigation alert (official import, `source = 'hhi_rnw'`)**

```json
{
  "category": "navtex",
  "content": "MILITARY EXERCISES IN STUPICA MALA AND KABAL COVES…",
  "language": "en",
  "source": "hhi_rnw",
  "sourceId": null,
  "sourceAttributes": {
    "type": "hhi_rnw",
    "externalSourceName": "Hydrographic Institute of the Republic of Croatia",
    "externalSourceUrl": "https://www.hhi.hr/en/e-services/radio-navigational-warnings",
    "externalNumber": "121/2026",
    "externalPublishedAt": "2026-05-15T08:00:00Z",
    "externalUpdatedAt": "2026-05-15T08:00:00Z",
    "externalExpiresAt": "2026-05-23T09:10:01Z"
  }
}
```

The `attributes` object is a first-class typed DTO per source type — not a
free-form payload. Alert geometry (`Point`, `MultiPoint`, `Polygon`,
`MultiPolygon`) is carried on the top-level `geometry` field; `coordinates`
carries the representative anchor (`ST_PointOnSurface` for polygons and
multipoints, the point itself for `Point` alerts). `sourceId` is `null` for
official imports; `sourceAttributes` is `null` for user-owned alerts.

The endpoint never returns full post bodies, full media lists, full tagged-user lists, full reaction breakdowns, full phone contact / radio channel records, or full user objects. Use the per-resource detail endpoints for that.

## Clustering

The endpoint clusters automatically when any of the following holds:

1. The raw matching set exceeds `limit`.
2. Density exceeds ~40 items per ~30×30 px viewport cell at the implied zoom.
3. The viewport latitudinal span exceeds `5°`, or radius exceeds `100 km`.
4. The client requested `detail=summary`.

### Algorithm

A deterministic geohash grid is used. Precision is derived from the requested spatial extent:

| Latitudinal span (°) | Precision | Approx. cell size at equator |
| -------------------- | --------- | ---------------------------- |
| `> 40`               | 2         | ~1250 km                     |
| `> 10`               | 3         | ~156 km                      |
| `> 2`                | 4         | ~39 km                       |
| `> 0.5`              | 5         | ~5 km                        |
| `> 0.1`              | 6         | ~1.2 km                      |
| `≤ 0.1`              | 7         | ~150 m                       |

For radius mode an equivalent table is used (`> 200 km → 3`, `> 50 km → 4`, `> 10 km → 5`, `> 2 km → 6`, else `7`).

On the in-memory path, each map item is placed into exactly one cell at
the chosen precision. A cell with `≥ 2` items becomes a cluster; a cell
with `1` item returns that item directly. The DB-aggregated path returns
clusters only, so `count=1` clusters can appear there because individual
rows never leave the database.

### Cluster shape

```json
{
  "kind": "cluster",
  "id": "cluster:u3wge:5",
  "name": "24 items",
  "coordinates": { "lat": 54.44, "lng": 18.56 },
  "geometry": { "type": "Point", "coordinates": [18.56, 54.44] },
  "count": 24,
  "types": { "post": 12, "spot": 8, "check_in": 4 },
  "bounds": { "north": 54.49, "south": 54.39, "east": 18.63, "west": 18.5 }
}
```

- `id` is **deterministic** — `cluster:<geohash>:<precision>`. The same set of inputs at the same precision yields the same `id` across requests, which lets clients animate cluster transitions cleanly.
- `name` is localized via `Accept-Language` (EN/PL). When one type accounts for ≥ 80 % of the cluster, a typed label is used (e.g. `"24 spots"` / `"24 miejsca"`).
- `coordinates` is the weighted centroid of the member items.
- `bounds` is the bounding box of the member items.
- Cluster items **do not** include source item IDs — clients zoom in or request a smaller viewport to reveal individuals.

### `hasMoreDetail`

A top-level boolean. `true` when at least one entry is a cluster (i.e. zooming in would reveal individual items). Useful for showing a "zoom in for details" hint with one check.

### Mixed responses

A response may freely mix `item` and `cluster` entries when some cells contain a single item and others contain multiple at the chosen precision. The total never exceeds `limit`; if it would, the server escalates to the next coarser precision until it fits.

## Detail Levels

The `detail` query parameter is a deterministic switch over the clustering rules:

| Value     | Behavior                                                                                                                                                                                                                                                                                                                                      |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `auto`    | Apply the clustering rules above. Mixed item/cluster responses allowed. **Default.**                                                                                                                                                                                                                                                          |
| `markers` | Skip the density and area rules. Still respect `limit`. If the raw set exceeds `limit`, the server clusters and returns the response with `meta.degradedToClusters: true` and `meta.detail: "markers-degraded"`. When even the coarsest precision cannot satisfy `limit`, the server returns `400 markers-detail-not-satisfiable` (RFC 7807). |
| `summary` | Always cluster, even on sparse data. Singleton cells are returned as `count=1` clusters. Precision is one step coarser than what `auto` would pick. Useful for heatmap-style UIs.                                                                                                                                                             |

`meta.detail` echoes the resolved detail level — `auto`, `markers`, `summary`, or `markers-degraded`.

## Sorting and Pagination

The endpoint is **not** offset/cursor-paginated. It is a viewport-scoped query: clients narrow the area or adjust filters to drill in, not page through results. This differs from `GET /posts`, `GET /spots`, and `GET /check-ins`.

- `limit` is accepted; `offset`, `cursor`, and `page` are not.
- `meta.totalItems` — total source items matching filters in the spatial area (post-visibility, pre-clustering).
- `meta.returnedItems` — source items represented in the response. For unclustered responses this equals `data.length`; for clustered responses this equals the sum of cluster `count` values plus the number of items.

### Top-level entry order

| Mode                  | Order                                                                                                                                                                   |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Radius, unclustered   | `distanceMeters` ascending; ties broken by `id` ascending.                                                                                                              |
| Radius, clustered     | Clusters by distance from request center to cluster centroid; items by `distanceMeters`; merged in that order.                                                          |
| Viewport, unclustered | `navigation_alert` first, then `check_in` (most time-sensitive), then `post` by newest `createdAt`, then `spot` alphabetical by `name`. Ties broken by `id` per source. |
| Viewport, clustered   | Clusters by `count` descending (largest first), then by `id`. Items follow, ordered as in the unclustered case.                                                         |

If even the coarsest clustering precision cannot fit `limit`, the server truncates and sets `meta.truncated: true` plus `meta.truncatedItems`.

## Area-based items: navigation alerts

The schema's `geometry` field is now populated by a real source type:
`navigation_alert`. Alerts are the first object kind that supports
`Polygon` and `MultiPolygon` geometries in addition to points.

An alert map item looks like this:

```json
{
  "kind": "item",
  "type": "navigation_alert",
  "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
  "name": "Obstruction",
  "coordinates": { "lat": 54.45, "lng": 18.6 },
  "geometry": {
    "type": "Polygon",
    "coordinates": [
      [
        [18.59, 54.44],
        [18.61, 54.44],
        [18.61, 54.46],
        [18.59, 54.46],
        [18.59, 54.44]
      ]
    ]
  },
  "attributes": {
    "category": "obstruction",
    "content": "Underwater obstruction reported by crew at low tide.",
    "language": "en",
    "source": "user",
    "sourceId": "019dfd19-ddd8-7d23-a1f4-06b96c16a36e",
    "sourceAttributes": null,
    "user": {
      "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36e",
      "name": "Jan K.",
      "avatarUrl": "https://cdn.example/avatars/abc.jpg"
    }
  }
}
```

Clients render `geometry` directly without needing a type-specific
response schema. For polygons and multipolygons, `coordinates` carries the
server-computed anchor (`ST_PointOnSurface(geometry)`) so marker placement,
labels, distance display, and clustering work uniformly across geometry
types. Bounds-mode queries match by `geometry` intersect (so a polygon
alert whose anchor is outside the viewport still appears as long as the
area overlaps); radius mode uses the `anchor` with `ST_DWithin`.

See [Navigation Alerts](../alerts/index.md) for the full CRUD contract
that produces these items.

## Performance and Caching

- **Response time target:** p95 ≤ 400 ms for viewport queries against a 10 km × 10 km area at production data scale.
- **Cache headers:**
  - Unclustered: `Cache-Control: private, max-age=15`
  - Clustered: `Cache-Control: private, max-age=60`
  - Cache is **private** because `bookmarked` is per-user and check-ins expose user-specific freshness.
- **Spatial indexes:** GiST indexes on `post.coordinates`, `spot.coordinates`, `user_check_in.coordinates` must exist.
- **DB-level clustering for large or wide queries:** the endpoint runs a cheap pre-flight `COUNT(*)` per selected source type. When the **combined** total exceeds ~1000 matching rows, or — for `auto` / `summary` — when the viewport span is wider than 10° (or the radius is over 100 km), clustering is delegated to PostGIS via `GROUP BY ST_GeoHash(...)`. Individual rows never leave the database; cluster counts, centroids, and bounds are computed from aggregated rows. The trade-off is that this path returns only `cluster` entries (no individual `item` entries).
  - **`detail=markers` is exempt from the area/density triggers.** The DB path is taken only when the total exceeds the memory limit, in which case the response degrades to clusters with `meta.detail = "markers-degraded"` (or `400 markers-detail-not-satisfiable` if even the coarsest precision will not fit `limit`). A sparse but wide markers query stays on the in-memory path and returns individual items.
  - **Precision escalation, not truncation, on overflow.** When the cluster count at the chosen precision exceeds `limit`, the DB path re-aggregates the buckets in memory by truncating their geohash prefixes — no extra DB round trips — and retries at the next coarser precision. Truncation (`meta.truncated`) only occurs as a last-resort safety after reaching the coarsest precision.
  - **Radius mode sorts clusters by distance** from the request center to the cluster centroid (ascending; ties by `id`), matching the in-memory path's documented order.
- The endpoint participates in the global authenticated rate limit; no additional per-endpoint limit is configured.

## Error types

All errors are returned as `application/problem+json` (RFC 7807) and localized via `Accept-Language`.

| Status | `type` slug                        | Trigger                                                              |
| ------ | ---------------------------------- | -------------------------------------------------------------------- |
| 401    | `unauthorized`                     | Missing or invalid JWT.                                              |
| 422    | `invalid-spatial-mode`             | Neither/both/partial spatial mode supplied.                          |
| 422    | `spatial-bounds-out-of-range`      | Viewport span or radius exceeds documented limits.                   |
| 422    | `invalid-coordinate-range`         | `lat` / `lng` / `north` / `south` / `east` / `west` outside WGS84.   |
| 422    | `invalid-types`                    | Unknown or empty `types` value.                                      |
| 422    | `invalid-post-types`               | Unknown `postTypes` value.                                           |
| 422    | `cross-region-types-not-evergreen` | `includeCrossRegionPostTypes` contains time-sensitive types.         |
| 422    | `invalid-date-range`               | `fromDate > toDate` or malformed ISO date.                           |
| 422    | `invalid-limit`                    | `limit` outside `[1, 500]`.                                          |
| 422    | `invalid-detail`                   | `detail` not one of `auto`, `markers`, `summary`.                    |
| 400    | `markers-detail-not-satisfiable`   | `detail=markers` cannot be satisfied even after coarsest clustering. |

## Related documentation

- [Posts](../posts/index.md)
- [Spots Directory](../spots/index.md)
- [Check-ins](../check-ins/index.md)
- [Navigation Alerts](../alerts/index.md) — Full reference for the
  `navigation_alert` source type surfaced by this endpoint.
- [Alert Categories](../reference/enums/alert-categories.md) — Map marker
  labels per category, EN/PL.
- [Regions](../regions/index.md)
- [Error handling](../getting-started/errors.md) (RFC 7807)
- PRD: [PRD-014: Unified Map](../prd/PRD-014-map.md)
