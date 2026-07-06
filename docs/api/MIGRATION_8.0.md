# API Migration Guide — v8.0.0 (Posts, Feed, Map, Alerts & Location Model)

This document describes the **API contract changes** introduced in **API
version 8.0.0** by the "Rebuild Posts, Feed, Map, Alerts and Location Model"
work. It is written for the mobile application team migrating the app to the new
API. See the [CHANGELOG](CHANGELOG.md#800---2026-07-05) entry for `8.0.0` for the
high-level summary.

> **Scope & compatibility.** `8.0.0` is a **major**, hard, non-backwards-compatible
> cut. There is **no data migration** and **no dual support** for the old
> contracts. The old endpoints/fields are gone, not deprecated. Plan a single
> coordinated release of the app against the new (8.0.0) API.

---

## 1. TL;DR — What changed

| Area               | Change                                                                                                                      |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| **Post model**     | No more post `type`. A post carries a structured `content` object; what it "is" is derived into `contentKeys`.              |
| **Post create**    | Single creation flow. One `POST /posts` body for everything (no per-type payloads, no discriminator).                       |
| **Post fields**    | `description`, `type`, `regionCode`, flat route fields, `coordinates`, `locationName` → replaced by `content` + `location`. |
| **Feed filters**   | `type`, `regionCode`, `crossRegionTypes` removed. New `contains`, `q` (full-text), keyset pagination.                       |
| **Alerts**         | `/alerts` CRUD **removed**. Navigation warnings are now regular **posts** with a `content.alert` object.                    |
| **Regions**        | `/regions` and `/regions/tree` **removed**. `regionCode` removed from posts, cruises and sailing briefs.                    |
| **Map**            | `postTypes` → `postContains`; `navigation_alert` item type removed (alerts are `post` items now); region params removed.    |
| **Sailing briefs** | Selected by `lat`/`lng` only (no `regionCode`). Response reshaped (`area` + `content` object).                              |
| **Cruises**        | `regionCode` removed (filter, create/update, `region` in response). New `lat`/`lng`/`distance` spatial filter.              |

---

## 2. Removed endpoints (return 404 now)

Remove all calls to these — they no longer exist:

- `GET/POST /alerts`
- `GET/PUT/DELETE /alerts/{alertId}`
- `GET /regions`
- `GET /regions/tree`

Alert content is now delivered through the posts feed and map (see §4 and §6).
Region selection is gone entirely; the app must switch to coordinate-based
discovery (see §7 and §8).

---

## 3. Posts — the content model (the big change)

### 3.1 There is no `type` anymore

The old model had 10 post types (`photo`, `place`, `food`, `marina`, `tips`,
`route`, `berth`, `weather`, `navigation_warning`, `help`), each with its own
create payload and rules. **All of that is gone.**

A post now has:

- `content` — a structured object with typed sub-objects (`text`, optional
  `route`, optional `alert`).
- `contentKeys` — a **server-derived** array describing what the post contains.
  Possible values: `alert`, `media`, `route`. (`text` is on every post, so it is
  never a key; `media` is present when the post has attached media.)

Examples of `contentKeys`:

| Post                               | `contentKeys`       |
| ---------------------------------- | ------------------- |
| Text-only note                     | `[]`                |
| Photo post                         | `["media"]`         |
| Route recommendation with photos   | `["route","media"]` |
| Imported / user navigation warning | `["alert"]`         |

`contentKeys` is **read-only** — never send it on create/update. The client
picks rendering based on it (or by inspecting `content` directly).

### 3.2 New Post response body

```jsonc
{
  "id": "019dfd19-...",
  "user": { "id": "...", "displayName": "Jan K.", "avatarUrl": null },
  "contentKeys": ["route"],
  "status": "published",
  "content": {
    "text": "Nice 3-day route around the islands.",
    "route": {
      "stops": [
        { "name": "Split", "coordinates": { "lat": 43.5081, "lng": 16.4402 } },
        { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
      ],
      "durationDays": 3,
      "lengthNm": 82,
    },
    // "alert": { ... }   // present only on alert posts
  },
  "location": {
    "name": "Central Dalmatia",
    "point": { "lat": 43.5081, "lng": 16.4402 },
    "area": null, // GeoJSON Polygon/MultiPolygon on alert posts, else null
  },
  "hashtags": ["route", "dalmatia"],
  "media": [
    {
      "id": "...",
      "url": "https://...",
      "type": "image",
      "orderIndex": 0,
      "width": 1920,
      "height": 1080,
      "size": 512000,
      "status": "ready",
    },
  ],
  "taggedUsers": [
    /* User[] */
  ],
  "commentsCount": 4,
  "reactions": { "total": 12, "byType": { "anchor": 8 }, "userReactions": [] },
  "bookmarked": false,
  "validityVotes": { "confirmCount": 6, "invalidCount": 0, "userVote": null }, // alert posts only
  "permissions": {
    "edit": true,
    "delete": true,
    "archive": true,
    "resolve": true,
    "comment": true,
    "react": true,
    "bookmark": true,
    "report": true,
    "validityVote": true,
  },
  "source": { "type": "alert", "id": "..." }, // present only on system-generated posts
  "publishedAt": "2026-06-17T08:05:00.000Z",
  "expiresAt": null,
  "resolvedAt": null,
  "archivedAt": null,
  "deletedAt": null,
  "createdAt": "2026-06-17T08:05:00.000Z",
  "updatedAt": "2026-06-17T08:05:00.000Z",
}
```

**Field mapping — old → new:**

| Old field                                  | New field                                                    |
| ------------------------------------------ | ------------------------------------------------------------ |
| `type`                                     | _removed_ → derive UI from `contentKeys` / `content`         |
| `description`                              | `content.text` (required, 1–2200 chars, on every post)       |
| `stops`, `durationDays`, `lengthNm` (flat) | `content.route.stops` / `.durationDays` / `.lengthNm`        |
| `regionCode`                               | _removed_                                                    |
| `locationName`                             | `location.name`                                              |
| `coordinates`                              | `location.point` (`{ lat, lng }`)                            |
| _(new)_                                    | `location.area` — GeoJSON Polygon/MultiPolygon (alert posts) |
| `media` (`Media[]`)                        | `media` (`PostMedia[]` — adds `orderIndex`, `status`)        |
| _(new)_                                    | `contentKeys`, `content.alert`, `source`, `publishedAt`      |

Notes:

- `validityVotes` is present **only** on posts whose `content` contains `alert`
  (previously: berth/weather/navigation_warning types).
- `source` is present **only** on system-generated posts (imported alerts).
- `publishedAt` is a real publication timestamp (see §3.6). The chronological
  feed sorts by it, not by `createdAt`.

### 3.3 Creating a post (`POST /posts`)

One body for everything. There is **no `type` field** and no discriminator.
Unknown top-level keys are rejected (`additionalProperties: false`).

```jsonc
{
  "content": {
    "text": "Beautiful anchorage, calm night and good holding.",   // required, 1–2200
    "route": { "stops": [ ... ], "durationDays": 3, "lengthNm": 82 }, // optional
    "alert": { "category": "navigation_warning", "severity": "warning" } // optional (see §4.2)
  },
  "location": {
    "name": "Sopot",
    "point": { "lat": 54.441, "lng": 18.567 },
    "area": { "type": "Polygon", "coordinates": [ ... ] }   // only allowed for alert posts
  },
  "mediaIds": ["019dfd19-..."],     // optional, max 10, ordered as given
  "taggedUserIds": [],              // optional, max 20
  "publishedAt": null,              // optional; future value = scheduled (see §3.6)
  "expiresAt": null                 // optional
}
```

Validation rules the app must respect:

- `content.text` is **required** on every post (1–2200 chars).
- `content.route` and `content.alert` **cannot be combined** in one post
  (`422`, `route and alert content cannot be combined`).
- Unknown content keys are rejected (e.g. `content.foo` → `422`,
  `property foo should not exist`).
- Media is **optional on every post** and **never required** (max 10 items).
- `location.area` is accepted **only** when `content.alert` is present.
- On alert posts, `location.point` is required.
- `contentKeys` is derived — never send it. Alert source-only fields
  (`source`, `externalNumber`, `external*`, `cancellation`, `language`) are
  rejected from user input; users may only set `category` and `severity`.

`PUT /posts/{postId}` uses the same body shape (full replace). `PATCH`
(`partiallyUpdatePost`) accepts the same fields, all optional; a provided
`content` fully replaces the stored content object.

### 3.4 Feed query (`GET /posts`) — parameter changes

**Removed parameters:** `type`, `regionCode`, `crossRegionTypes`.

**New / changed parameters:**

| Param                            | Notes                                                                                                                                    |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| `contains`                       | CSV of `alert`, `media`, `route`, `note`. Overlap match. `note` = posts with empty `contentKeys`. `contains=alert` is the "alerts feed". |
| `q`                              | Full-text search (max 200 chars) over text, location, hashtags, route stop names, alert external number.                                 |
| `beforePublishedAt` + `beforeId` | **Keyset cursor** for the chronological feed (see §3.5).                                                                                 |
| `sort`                           | `publishedAt` (default) \| `updatedAt` \| `distance`. (Old `type`-based / `createdAt` sorts gone.)                                       |
| `fromDate` / `toDate`            | Now filter on **`publishedAt`** (previously `createdAt`).                                                                                |

**Unchanged:** `status`, `userId`, `locationName`, `hashtag`, `lat`, `lng`,
`distance` (1–100 km), `limit` (max 100), `offset`, `order`.

Location filtering: pass `lat` + `lng` + `distance` **together** (all or none,
else `422`). `sort=distance` requires location params.

### 3.5 Pagination — keyset for the chronological feed

- Default chronological feed (`sort=publishedAt desc`) should move to **keyset
  pagination**: read the last item's `publishedAt`/`id`, then request the next
  page with `beforePublishedAt` + `beforeId`. Backed by an index for stable
  walks under concurrent inserts.
- `sort=distance` **cannot** be keyset-paginated — it stays on `limit`/`offset`.
- Offset pagination still works for the chronological feed too, but keyset is the
  target for the mobile app.

The response `meta` remains `{ total, limit, offset, hasMore }`.

### 3.6 Scheduled publication

- `publishedAt` in the **future** = scheduled post: visible only to the author
  until it is due, then it appears in the public feed automatically.
- Public feed visibility now requires `status = published` **AND**
  `publishedAt <= now()`. This applies uniformly to the feed **and** the map.

### 3.7 Validity voting is alert-only

`POST /posts/{postId}/validity-vote` is now allowed **only** on posts whose
`content` contains `alert`. Voting on a non-alert post returns an error
(`Post content does not support validity voting`). Voting is only allowed on
`published`, non-expired posts. Gate the vote UI on `permissions.validityVote` /
presence of `validityVotes`.

---

## 4. Alerts are now posts

### 4.1 Reading alerts

Imported official navigation warnings are created as **posts** authored by a
seeded system user ("SkipperClub Alerts"). They:

- have `contentKeys: ["alert"]` and a populated `content.alert` object
  (`category`, `severity`, `language`, `source`, `externalNumber`,
  `external*` timestamps, optional `cancellation`);
- carry `location.point` and usually `location.area` (the affected polygon);
- expose `source: { type: "alert", id }`;
- are readable, commentable, reactable, bookmarkable, and support validity voting
  like any other post;
- appear on the map as `post` items (see §6).

To fetch the alerts feed: `GET /posts?contains=alert` (optionally with
`lat`/`lng`/`distance`). System-authored posts return
`permissions.edit/delete/archive/resolve = false` for everyone.

### 4.2 Creating a user alert post

Instead of `POST /alerts`, users create an alert post via `POST /posts` with a
`content.alert` object:

```jsonc
{
  "content": {
    "text": "Floating debris reported near the harbour entrance.",
    "alert": { "category": "obstruction", "severity": "warning" }
  },
  "location": {
    "name": "Harbour entrance",
    "point": { "lat": 43.5, "lng": 16.44 },
    "area": { "type": "Polygon", "coordinates": [ ... ] }   // optional
  }
}
```

- `content.alert.category` uses the `AlertCategory` enum: `navigation_warning`,
  `navtex`, `notice_to_mariners`, `obstruction`, `works`, `regatta`, `diving`,
  `military_exercise`, `weather`, `other`.
- `severity` (user-settable): `info` | `warning` | `critical`.
- `location.point` is **required**; `location.area` is optional.
- Source-only fields are rejected from user input.

---

## 5. Regions removed

`GET /regions` and `GET /regions/tree` are gone. Remove any region picker and any
`regionCode` you were sending or reading. All region-based state is replaced by
coordinate/viewport-based behavior:

- Feed: `lat`/`lng`/`distance`.
- Map: viewport bounds or radius.
- Sailing brief: `lat`/`lng` (see §7).
- Cruises: `lat`/`lng`/`distance` (see §8).

---

## 6. Map (`GET /map/items`)

**Removed parameters:** `regionCode`, `includeCrossRegionPostTypes`, `postTypes`.

**Changes:**

- `types` valid set shrinks to `post | spot | check_in`. The
  **`navigation_alert` item type is removed** — alerts now arrive as `post`
  items with `attributes.contentKeys` containing `alert`.
- New `postContains` param (replaces `postTypes`): CSV of `alert`, `media`,
  `route`, `note`; overlap match, `note` = empty `contentKeys`. Lets the map
  toggle alerts / photos / routes / notes independently.
- Map item `type` enum is `post | spot | check_in`.
- `MapPostAttributes` now carries: `contentKeys`, optional `alertCategory`,
  `status`, `author`, `publishedAt`, `expiresAt`, `mediaPreview`,
  `commentsCount`, `bookmarked`.

Post map projection (unchanged mechanics, new source fields):

- `coordinates` = representative point (`location.point`) — used for marker
  placement, distance sorting, clustering.
- `geometry` = `location.area` when present (alert posts), otherwise a GeoJSON
  Point from `location.point`.
- Bounds mode matches posts whose `area` intersects the viewport OR whose
  `point` is inside it; radius mode uses the point.

**Marker rendering precedence** (client picks style from
`attributes.contentKeys` + `mediaPreview`):
`alert` (warning marker / area) > `media` (photo pin using `mediaPreview`) >
`route` > plain note pin.

`detail=markers|summary|auto`, limits, and antimeridian handling are unchanged.

---

## 7. Sailing briefs (`GET /sailing-briefs`)

**Request:** the `regionCode` parameter is **removed**. Select a brief purely by
current location:

```http
GET /v1/sailing-briefs?lat=43.5081&lng=16.4402
```

The backend resolves the most specific enabled technical area containing the
point (with parent-area fallback). If none matches, the existing "not available"
problem is returned.

**Response reshaped:**

- `regionCode` → `area: { id, code, name }` (an internal technical area code the
  user never chooses).
- The nine flat text fields (`shortDescription`, `fullDescription`, `weather`,
  `berth`, `route`, `tips`, `marina`, `food`, `place`) move into a single
  `content` object.
- `version` and `generationSource` are no longer in the public response.

```jsonc
{
  "id": "019dfd19-...",
  "area": {
    "id": "...",
    "code": "HR-DALMATIA-CENTRAL-03",
    "name": "Central Dalmatia Chart 03",
  },
  "language": "pl",
  "timeSlot": "morning",
  "localDate": "2026-06-17",
  "generatedAt": "2026-06-17T05:00:00.000Z",
  "expiresAt": "2026-06-17T12:00:00.000Z",
  "content": {
    "shortDescription": "...",
    "fullDescription": "...",
    "weather": "...",
    "berth": "...",
    "route": "...",
    "tips": "...",
    "marina": "...",
    "food": "...",
    "place": "...",
  },
}
```

Admin endpoints (`GET /sailing-briefs/list`, `POST /sailing-briefs/regenerate`)
switch from region codes to area codes.

---

## 8. Cruises (`GET /cruises`, create/update)

- **Removed:** the `regionCode` list filter, region auto-resolution on
  create/update, and the `region` object in cruise responses.
- **Added:** spatial filtering — `lat` + `lng` + `distance` matches cruises whose
  **departure or arrival port** is within the radius.
- Cruise create/update no longer accept `regionCode`; they rely on the existing
  `departurePort` / `arrivalPort` geodata.

Remove `regionCode` from cruise create/update bodies and stop reading
`cruise.region` from responses.

---

## 9. Migration checklist (mobile)

- [ ] Remove the region picker and every `regionCode` read/write (posts, cruises,
      sailing briefs, map).
- [ ] Remove all `/alerts` and `/regions*` calls.
- [ ] Replace the 10 per-type post creation forms with a single form producing the
      `content` object (`text` required; optional `route`; optional `alert`).
- [ ] Stop reading `type`, `description`, flat route fields, `coordinates`,
      `locationName`; read `content`, `contentKeys`, `location.{name,point,area}`,
      `media[].orderIndex` instead.
- [ ] Drive post UI/rendering from `contentKeys` (`alert`/`media`/`route`) rather
      than a type field.
- [ ] Switch feed filters: `type`→`contains`, add `q` search; move date filters to
      `publishedAt` semantics.
- [ ] Adopt keyset pagination (`beforePublishedAt` + `beforeId`) for the
      chronological feed; keep offset only for `sort=distance`.
- [ ] Render imported/user alerts as posts (`contains=alert`); gate validity voting
      on alert posts only.
- [ ] Update the map: `postTypes`→`postContains`, drop `navigation_alert`, read
      alerts as `post` items, apply the marker precedence rules.
- [ ] Sailing brief: send `lat`/`lng` only; read `area` + `content` object.
- [ ] Cruises: drop `regionCode`; add `lat`/`lng`/`distance` filtering; stop reading
      `region`.
- [ ] Handle scheduled posts (`publishedAt` in future = author-only until due).

---

_Source of truth: `docs/openapi.yaml` on this branch. Diff it against `main` for
exact field-level detail._
