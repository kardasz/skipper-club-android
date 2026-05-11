# Post Types

The `PostType` enum categorizes posts into specialized content types, each with specific field requirements and behaviors.

## Values

| Value                | Category       | Description                               |
| -------------------- | -------------- | ----------------------------------------- |
| `photo`              | Evergreen      | General sailing photos and visual content |
| `place`              | Evergreen      | Interesting locations worth visiting      |
| `food`               | Evergreen      | Restaurant and food recommendations       |
| `marina`             | Evergreen      | Marina reviews and information            |
| `tips`               | Evergreen      | Sailing advice and tips                   |
| `route`              | Evergreen      | Sailing route with waypoints              |
| `berth`              | Time-sensitive | Available berth offers                    |
| `weather`            | Time-sensitive | Weather alerts and conditions             |
| `navigation_warning` | Time-sensitive | Navigation hazards and warnings           |
| `help`               | Time-sensitive | Requests for assistance                   |

## Categories

### Evergreen Types

Content that remains relevant indefinitely. These posts never automatically expire.

```typescript
const EVERGREEN_TYPES = ['photo', 'place', 'food', 'marina', 'tips', 'route'];
```

### Time-Sensitive Types

Content with limited validity. These posts have automatic expiration calculated at creation time as `createdAt + duration`:

| Type                 | Expiration Duration |
| -------------------- | ------------------- |
| `berth`              | 6 hours             |
| `weather`            | 7 days              |
| `navigation_warning` | 7 days              |
| `help`               | 72 hours (3 days)   |

```typescript
const TIME_SENSITIVE_TYPES = ['berth', 'weather', 'navigation_warning', 'help'];
```

### Votable Types

Types that support community validity voting. Users can confirm the information is still accurate or report it as invalid.

**Note:** The `help` type does NOT support community voting. Help requests can only be resolved by the author.

```typescript
const VOTABLE_TYPES = ['berth', 'weather', 'navigation_warning'];
```

### Cross-Region Types

Only **evergreen types** can be used for cross-region filtering via the `crossRegionTypes` parameter. Time-sensitive types are always region-scoped.

```typescript
const CROSS_REGION_TYPES = [
  'photo',
  'place',
  'food',
  'marina',
  'tips',
  'route',
];
```

If `crossRegionTypes` contains any time-sensitive type, the API returns 422 validation error.

## Field Requirements

| Type                 | Media    | Description | Location | Coordinates |
| -------------------- | -------- | ----------- | -------- | ----------- |
| `photo`              | Required | Optional    | Optional | Optional    |
| `place`              | Optional | Required    | Required | Required    |
| `food`               | Optional | Required    | Required | Required    |
| `marina`             | Optional | Required    | Required | Required    |
| `tips`               | Optional | Required    | Optional | Optional    |
| `route`              | Optional | Required    | Required | Required    |
| `berth`              | Optional | Required    | Required | Required    |
| `weather`            | Optional | Required    | Required | Required    |
| `navigation_warning` | Optional | Required    | Required | Required    |
| `help`               | Optional | Required    | Required | Required    |

### Route-Specific Fields

The `route` type has additional fields:

| Field          | Type        | Required | Description                            |
| -------------- | ----------- | -------- | -------------------------------------- |
| `stops`        | RouteStop[] | Yes      | Array of waypoints (1-30 stops)        |
| `durationDays` | integer     | No       | Trip duration in days (min 1)          |
| `lengthNm`     | number      | No       | Route length in nautical miles (min 0) |

```typescript
interface RouteStop {
  name: string; // Stop name (1-255 chars)
  coordinates: {
    lat: number; // Latitude (-90 to 90)
    lng: number; // Longitude (-180 to 180)
  };
}
```

## Usage

### Creating Posts

```http
POST /v1/posts
Content-Type: application/json

{
  "type": "photo",
  "regionCode": "ADR-HR",
  "description": "Beautiful sunset!",
  "mediaIds": ["..."]
}
```

### Filtering by Type

```http
GET /v1/posts?type=photo&type=tips
```

### Cross-Region Filtering

Show photos and tips from all regions while filtering other types by `ADR-HR`:

```http
GET /v1/posts?regionCode=ADR-HR&crossRegionTypes=photo&crossRegionTypes=tips
```

## Related

- [Post Statuses](./post-statuses.md)
- [Reaction Types](./reaction-type.md)
- [Validity Vote Types](./validity-vote-type.md)
- [Posts API](../../posts/index.md)
