# Map Items

`GET /v1/map/items` returns lightweight map projections for posts, spots, and
check-ins. Alert content reaches the map as posts with `contentKeys` containing
`alert`; there is no `navigation_alert` source type.

## Spatial Modes

Use exactly one spatial mode.

Viewport bounds:

```http
GET /v1/map/items?north=44&south=43&east=17&west=16
```

Radius:

```http
GET /v1/map/items?lat=43.5081&lng=16.4402&distance=25
```

Bounds mode matches post areas that intersect the viewport or post points that
fall inside it. Radius mode uses `location.point` for matching and distance.

## Query Parameters

| Parameter            | Meaning                                                               |
| -------------------- | --------------------------------------------------------------------- |
| `types`              | CSV subset of `post`, `spot`, `check_in`. Defaults to all three.      |
| `postContains`       | CSV of `alert`, `media`, `route`, `note`; applied only to post items. |
| `fromDate`, `toDate` | Published-date window for posts.                                      |
| `detail`             | `markers`, `summary`, or `auto`.                                      |
| `limit`              | Maximum returned items or clusters.                                   |

Removed parameters: `regionCode`, `includeCrossRegionPostTypes`, and
`postTypes`.

## Post Projection

Posts are projected using the representative point and optional area:

```json
{
  "kind": "item",
  "type": "post",
  "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
  "name": "Navigation warning",
  "coordinates": { "lat": 43.508333, "lng": 15.775235 },
  "geometry": { "type": "Point", "coordinates": [15.775235, 43.508333] },
  "attributes": {
    "contentKeys": ["alert"],
    "alertCategory": "navigation_warning",
    "status": "published",
    "author": null,
    "publishedAt": "2026-06-17T08:05:00.000Z",
    "expiresAt": "2026-07-17T08:05:00.000Z",
    "mediaPreview": null,
    "commentsCount": 0,
    "bookmarked": false
  }
}
```

For area alert posts, `coordinates` stays the representative
`location.point`, while `geometry` is the Polygon or MultiPolygon from
`location.area`.

## Visibility

Every post map path, including DB aggregation, uses the shared post visibility
predicate:

- published status
- not deleted
- not archived/resolved/expired
- `publishedAt <= now()`
- `expiresAt` absent or in the future

Clustering uses `location.point` for all posts, including area alert posts.

## Marker Taxonomy

Clients choose marker styling by `attributes.contentKeys`:

1. `alert`
2. `media`
3. `route`
4. plain note (`contentKeys` empty)

## Error Handling

| Status | Type                                     | Description                                                                               |
| ------ | ---------------------------------------- | ----------------------------------------------------------------------------------------- |
| 422    | `/errors/invalid-spatial-mode`           | Provide either viewport bounds or center+radius — not both, neither, or partial           |
| 422    | `/errors/spatial-bounds-out-of-range`    | Viewport span exceeds 120° per axis, or radius exceeds 500 km                             |
| 422    | `/errors/invalid-coordinate-range`       | Latitude/longitude out of range, or `north` not greater than `south`                      |
| 422    | `/errors/invalid-types`                  | `types` must be a non-empty subset of `post`, `spot`, `check_in`                          |
| 422    | `/errors/invalid-post-contains`          | `postContains` must only contain `alert`, `media`, `route`, `note`                        |
| 422    | `/errors/invalid-date-range`             | `fromDate`/`toDate` must be valid ISO 8601, `fromDate` <= `toDate`                        |
| 422    | `/errors/invalid-limit`                  | `limit` must be an integer in [1, 500]                                                    |
| 422    | `/errors/invalid-detail`                 | `detail` must be one of `auto`, `markers`, `summary`                                      |
| 400    | `/errors/markers-detail-not-satisfiable` | `detail=markers` can't be satisfied for the requested area even after coarsest clustering |
