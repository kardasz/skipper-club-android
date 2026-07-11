# Post Content Object Registry

Posts carry a structured `content` JSONB object instead of a post type. The
key of each entry identifies a typed content object; the frontend renders each
object by key. There is **no `type` column and no `PostType` enum** — what a
post _is_ follows from what its `content` contains.

## Derived `content_keys`

`posts.content_keys text[]` is a derived, GIN-indexed projection of the
content used for filtering:

- every registry key present in `content` **except `text`** (present on every
  post, useless as a filter),
- plus a synthetic `media` key when the post has at least one attached media
  item (`post_media` rows).

Examples:

| Post                             | `content_keys`  |
| -------------------------------- | --------------- |
| Text-only note                   | `{}`            |
| Photo post                       | `{media}`       |
| Route recommendation with photos | `{route,media}` |
| Imported navigation warning      | `{alert}`       |

The column is always derived server-side through the shared
`computeContentKeys` function in `internal/posts/content.go`, used by create,
update, and alert sync paths. It is **never accepted from the client**.

"Is this an alert post" in code = `content.alert` present (helper
`isAlertContent`); in SQL: `content_keys @> '{alert}'`.

## Registry (v1)

| Key     | Shape                                                                                                                                       | Rules                                                                   |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `text`  | `string` (1–2200 chars, hashtag extraction source)                                                                                          | required on every post                                                  |
| `route` | `{ stops: [{name, coordinates:{lat,lng}}] (1–30), durationDays?: int ≥1, lengthNm?: number ≥0 }`                                            | optional; cannot be combined with `alert`                               |
| `alert` | `{ category, severity?, language?, source?, externalNumber?, externalPublishedAt?, externalUpdatedAt?, externalExpiresAt?, cancellation? }` | marks the post as an alert; requires a location point; excludes `route` |

(`media` is a synthetic `content_keys` entry derived from `post_media`, not a
content object — media stays relational.)

### Rules

- **Unknown content keys are rejected on write** by the posts HTTP validation
  layer, so the registry stays authoritative. Adding a new
  object type = new nested DTO + registry entry + docs, nothing else — no enum
  change, no new endpoint, no new filter parameter (`content_keys` and the
  `contains` filter pick the new key up automatically).
- `alert` and `route` cannot coexist in one post (alerts stay single-purpose).
- `content.alert.category` reuses the `AlertCategory` enum
  (`navigation_warning`, `navtex`, `notice_to_mariners`, `obstruction`,
  `works`, `regatta`, `diving`, `military_exercise`, `weather`, `other`).
- `content.alert.severity` is one of `info`, `warning`, `critical`.
- **Source-only fields** (`source`, `externalNumber`, `external*`,
  `cancellation`) are set exclusively by the alert-to-post sync, never
  accepted from user input. User alert posts may set only `category` and
  `severity`.
- An alert post additionally requires `location.point`; `location.area`
  (Polygon/MultiPolygon, validated like alert geometry: WGS84 ranges, closed
  rings, ≥ 4 positions) is accepted only when `content.alert` is present and
  requires `location.point` too.
- Validity voting is available exactly on posts containing `content.alert`.

## Filtering

Feed: `GET /posts?contains=<csv>`; map: `GET /map/items?postContains=<csv>`.
Accepted values: `alert`, `media`, `route`, and the special value `note`.

- Overlap semantics: a post matches when its `content_keys` overlaps the
  selected keys (`&&`).
- `note` matches posts with empty `content_keys` (plain text notes).
- `contains=alert` is the alerts feed; it is executed as the literal SQL
  predicate `content_keys @> '{alert}'` so the partial index
  `idx_posts_alert_feed` can match (a bind parameter would defeat partial
  index matching).

## Marker rendering precedence (map)

The client picks the marker style by precedence, driven by
`attributes.contentKeys` and `mediaPreview`:

1. `alert` — warning marker / area rendering,
2. `media` — photo pin using the `mediaPreview` thumbnail,
3. `route` — route pin,
4. otherwise — plain note pin.
