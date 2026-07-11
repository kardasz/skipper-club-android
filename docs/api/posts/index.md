# Posts

Posts are the public feed source of truth for regular social content, route
recommendations, user-created alert posts, and official imported navigation
warnings.

There is no post `type` field. A post stores a structured `content` JSON
object and exposes derived `contentKeys` for filtering. See
[Content Object Registry](./content-objects.md) for the full registry and
validation rules.

## Content Model

Every post has:

- `content.text` (required, 1-2200 chars; hashtag extraction source)
- optional content objects such as `content.route` or `content.alert`
- optional relational media via `post_media`
- optional `location` with `name`, representative `point`, and optional
  `area`
- derived `contentKeys`, never accepted from clients

`contentKeys` contains every content key except `text`, plus synthetic `media`
when media is attached. Examples:

| Post                        | `contentKeys`       |
| --------------------------- | ------------------- |
| Text note                   | `[]`                |
| Post with media             | `["media"]`         |
| Route with photos           | `["route","media"]` |
| Imported navigation warning | `["alert"]`         |

Alert posts are posts whose `content.alert` is present. Validity voting is
available only for alert posts.

## Create

`POST /v1/posts`

```json
{
  "content": {
    "text": "Nice 3-day route around the islands.",
    "route": {
      "stops": [
        { "name": "Split", "coordinates": { "lat": 43.5081, "lng": 16.4402 } },
        { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } }
      ],
      "durationDays": 3,
      "lengthNm": 82
    }
  },
  "location": {
    "name": "Central Dalmatia",
    "point": { "lat": 43.5081, "lng": 16.4402 }
  },
  "mediaIds": [],
  "taggedUserIds": [],
  "publishedAt": null,
  "expiresAt": null
}
```

Rules:

- Unknown `content` keys are rejected.
- `route` and `alert` cannot be combined.
- `contentKeys` is ignored/rejected from client input.
- Media is allowed on every post and required on none; maximum 10 items.
- `publishedAt` in the future schedules a post. It is hidden publicly until
  due, but visible to the author.
- `expiresAt` is optional for user posts.
- Alert posts require `location.point`; `location.area` is accepted only for
  alert posts and must be Polygon or MultiPolygon.

## List Feed

`GET /v1/posts`

Important query parameters:

| Parameter                       | Meaning                                                                                                |
| ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `contains`                      | CSV of `alert`, `media`, `route`, `note`; overlap semantics. `note` matches empty `contentKeys`.       |
| `q`                             | Full-text search over `content.text`, location name, hashtags, route stops, and alert external number. |
| `lat`, `lng`, `distance`        | Radius search using `location.point`.                                                                  |
| `hashtag`                       | Case-insensitive hashtag filter without `#`.                                                           |
| `fromDate`, `toDate`            | Filter by `publishedAt`.                                                                               |
| `beforePublishedAt`, `beforeId` | Keyset cursor for chronological feed.                                                                  |
| `sort`                          | `publishedAt`, `updatedAt`, or `distance`.                                                             |

Public feed visibility uses the shared post visibility predicate:

- `deletedAt IS NULL`
- `status = published`
- `publishedAt <= now()`
- `expiresAt IS NULL OR expiresAt > now()`

Authors can still view their own non-public states through author-specific
queries and status filters.

## Response Shape

```json
{
  "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
  "contentKeys": ["alert"],
  "status": "published",
  "user": null,
  "content": {
    "text": "Radio navigational warning...",
    "alert": {
      "category": "navigation_warning",
      "severity": "warning",
      "source": "hhi_rnw",
      "externalNumber": "161/2026"
    }
  },
  "location": {
    "name": "Central Adriatic",
    "point": { "lat": 43.508333, "lng": 15.775235 },
    "area": null
  },
  "hashtags": ["navigation", "warning"],
  "media": [],
  "taggedUsers": [],
  "commentsCount": 0,
  "reactions": { "total": 0, "byType": {}, "userReactions": [] },
  "permissions": {
    "edit": false,
    "delete": false,
    "archive": false,
    "resolve": false,
    "comment": true,
    "react": true,
    "bookmark": true,
    "report": true,
    "validityVote": true
  },
  "bookmarked": false,
  "validityVotes": {
    "confirmCount": 0,
    "invalidCount": 0,
    "userVote": null
  },
  "source": {
    "type": "alert",
    "id": "019ed686-8724-785f-9969-6445d5cfbac4"
  },
  "publishedAt": "2026-06-17T08:05:00.000Z",
  "expiresAt": null,
  "createdAt": "2026-06-17T08:05:00.000Z",
  "updatedAt": "2026-06-17T08:05:00.000Z"
}
```

`validityVotes` is present only for alert posts. `source` is present only for
system-generated posts, currently imported official alerts, which have no
author — their `user` is `null`. `permissions` reflects what the requesting
user may do with this specific post (management actions are always `false` on
system-generated posts).

## Update

`PUT /v1/posts/{postId}` (full replacement) and
`PATCH /v1/posts/{postId}` (partial update) accept the same fields as create,
plus an optional `status`. Only `published`, `archived`, and `resolved` may be
set by the author — `expired` and `deleted`, and any transition not allowed
from the post's current status, are rejected with
`/errors/invalid-status-transition`. Posts in a non-editable status
(`archived`, `expired`, `resolved`) reject other field edits but can still be
soft-deleted.

## Other Endpoints

The full endpoint surface (comments, reactions, bookmarks, validity votes,
reports, deletion) is defined in `api/openapi.yaml` under the `Posts` tag —
this page covers the content model and feed semantics, not every route.

## Imported Alerts

Official alerts remain in source tables for ingestion and deduplication. After
an alert upsert, the sync creates or updates one public post with:

- `source.type = "alert"`
- `source.id = alerts.id`
- `content.alert` populated from the public source-attributes projection
- no author (`user_id` is `NULL`, and `user` is `null` in responses)

The sync is idempotent by `(source_type, source_id)`. Known cancellations
resolve the linked post.

## Removed Fields

Posts no longer expose or accept `type`, `description`, `properties`,
`coordinates`, `regionCode`, or `crossRegionTypes`.
