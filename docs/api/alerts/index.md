# Alerts

Alerts are no longer a public CRUD product surface. Public discovery,
comments, reactions, bookmarks, reports, validity voting, and map rendering
all happen through posts whose `content.alert` object is present.

## Public API

The following endpoints have been removed and should return 404:

- `GET /v1/alerts`
- `GET /v1/alerts/{alertId}`
- `POST /v1/alerts`
- `PUT /v1/alerts/{alertId}`
- `DELETE /v1/alerts/{alertId}`

Users create alert posts with `POST /v1/posts`:

```json
{
  "content": {
    "text": "Unlit buoy near the harbor entrance.",
    "alert": {
      "category": "obstruction",
      "severity": "warning"
    }
  },
  "location": {
    "name": "Harbor entrance",
    "point": { "lat": 43.5081, "lng": 16.4402 }
  }
}
```

User-writable alert content is limited to `category` and optional `severity`.
Source-only fields such as `source`, `externalNumber`, `externalPublishedAt`,
and `cancellation` are set only by the import sync.

## Ingestion Model

The `alerts`, `alert_source_records`, and `alert_source_runs` tables remain as
source data for official imports. The only live source today is Croatia's HHI
(Hrvatski hidrografski institut) Radio Navigational Warnings feed, scoped to
`country = "HR"`; only its `LOCAL` and `COASTAL - NAVTEX` categories are
surfaced. The HHI RNW pipeline still normalizes, deduplicates, validates
geometry, and upserts source alerts, running on a twice-daily schedule
(05:00 and 17:00) with a 14-day lookback window for prior-year records.

When a source alert has no explicit expiry, `expiresAt` falls back to a
per-category default window measured from `publishedAt`: 24h for `weather`;
7 days for `navtex`, `regatta`, `diving`, and `military_exercise`; 30 days for
`navigation_warning`, `notice_to_mariners`, `works`, and `other`; 90 days for
`obstruction`.

After each official alert write, the alert-to-post sync creates or updates the
linked post:

- `posts.source_type = "alert"`
- `posts.source_id = alerts.id`
- `posts.user_id =` the seeded system user "SkipperClub Alerts"
- `content.text = alerts.content`
- `content.alert` contains the public source projection
- `location.point = alerts.anchor`
- `location.area = alerts.geometry` for area warnings
- `publishedAt` and `expiresAt` come from source validity metadata

The sync is idempotent by the unique `(source_type, source_id)` pair. Re-imports
update the same post. Known cancellations set the linked post to `resolved`.

### Lifecycle: cancellation is terminal

Cancellation is a one-way transition: `published -> resolved`. There is no
reverse transition. Once a re-import has resolved a post, a later re-import
that refreshes content without cancellation metadata (e.g. a feed hiccup
that dropped the field) does **not** revive it back to `published` —
official cancellations should not be undone by data noise. Content
(`content`, `hashtags`, `location`, `publishedAt`, `expiresAt`) still
refreshes on every re-import regardless of status; only the status/
`resolvedAt` transition is one-way.

## Geometry

Alert source geometry may be Point, MultiPoint, Polygon, or MultiPolygon.
Public post location uses:

- `location.point` as the representative point for feed distance sorting,
  map marker placement, and clustering
- `location.area` for affected-area rendering and map viewport intersection

User-created alert post areas accept Polygon and MultiPolygon only.

## Related Docs

- [Posts](../posts/index.md)
- [Post Content Object Registry](../posts/content-objects.md)
- [Map](../map/index.md)
