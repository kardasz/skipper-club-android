# Sailing Briefs

Sailing briefs are selected by the user's current coordinates, not by a public
region picker. The lookup resolves the most specific enabled technical
`brief_area` containing the point and then returns the latest brief for that
area, language, date, and time slot.

## Mobile Lookup

```http
GET /v1/sailing-briefs?lat=43.5081&lng=16.4402
```

`lat` and `lng` are required and must be a valid WGS84 coordinate pair.
`regionCode` is no longer accepted.

Response:

```json
{
  "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
  "area": {
    "id": "019dfd19-aabb-7d23-a1f4-06b96c16a36d",
    "code": "HR-DALMATIA-CENTRAL-03",
    "name": "Central Dalmatia Chart 03"
  },
  "language": "pl",
  "timeSlot": "morning",
  "localDate": "2026-06-17",
  "generatedAt": "2026-06-17T05:00:00.000Z",
  "expiresAt": "2026-06-17T12:00:00.000Z",
  "content": {
    "shortDescription": "Good sailing conditions.",
    "fullDescription": "...",
    "weather": "...",
    "berth": "...",
    "route": "...",
    "tips": "...",
    "marina": "...",
    "food": "...",
    "place": "..."
  }
}
```

If the matched area has no current brief, lookup falls back through the parent
area chain. If no enabled area or suitable brief exists, the existing
not-available problem response is returned.

## Technical Areas

`brief_areas` are backend technical geometry records:

- `code`
- `name`
- optional parent
- MultiPolygon geometry
- timezone
- enabled flag
- priority

They replace the deleted `regions` and `region_i18n` tables for sailing brief
selection. They are seeded through the CLI seeder, not through region data
migrations.

## Admin and Generation

Admin listing and regeneration use area codes internally instead of region
codes. The generation processor still produces the same nine content fields
and stores them in the `sailing_briefs.content` JSON object.

The scheduler iterates enabled brief areas, resolves the due local time slot
from each area's timezone, and enqueues generation per supported language.

### Admin Endpoints

`GET /v1/sailing-briefs/list` (admin role required) — paginated history of
generated briefs.

| Parameter          | Description                               |
| ------------------ | ----------------------------------------- |
| `areaCode`         | Filter by technical brief area code       |
| `timeSlot`         | Filter by `morning`, `noon`, or `evening` |
| `generationSource` | Filter by `auto`, `manual`, or `edited`   |
| `limit`, `offset`  | Pagination (`limit` 1-100, default 20)    |

Response: `{ data: SailingBrief[], meta: { total, limit, offset } }`.

`POST /v1/sailing-briefs/regenerate` (admin role required) — enqueues
regeneration jobs with a custom prompt.

```json
{
  "prompt": "Focus on family-friendly activities and safety",
  "areaCodes": ["HR-DALMATIA-CENTRAL-03"]
}
```

Returns `202` with `{ jobs: [{ areaCode, language, timeSlot }, ...] }`, one
entry per enqueued (area × supported language × current time slot)
combination.
