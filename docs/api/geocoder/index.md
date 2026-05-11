# Geocoder

The geocoder API provides location search, autocomplete, place details, and reverse geocoding capabilities using Google Maps.

## Overview

SkipperClub's geocoder enables users to search for locations by name/address, get autocomplete suggestions, retrieve place details, and convert GPS coordinates to human-readable addresses. This is essential for:

- **Post creation** — Populate `locationName` and `coordinates` fields when creating posts
- **Location search** — Find spots, harbors, and sailing destinations
- **Autocomplete** — Real-time suggestions as users type location names
- **Place details** — Get full coordinates and info for selected autocomplete predictions
- **Reverse geocoding** — Convert device GPS coordinates to formatted addresses

Key features include:

- **Google Maps integration** — Powered by Google Maps Places API (Autocomplete, Text Search, Place Details) and Geocoding API
- **Rate limiting** — 30 requests per minute per user
- **Caching** — 24-hour cache to reduce API costs and latency
- **Session tokens** — Billing optimization for autocomplete → place details flows
- **Localization** — Results in 6 languages via `Accept-Language` header
- **Provider abstraction** — Interface-based design for future provider flexibility

## Endpoints

| Method | Endpoint                        | Description                         |
| ------ | ------------------------------- | ----------------------------------- |
| GET    | `/v1/geocoder/search`           | Search locations by name or address |
| GET    | `/v1/geocoder/reverse`          | Convert coordinates to addresses    |
| GET    | `/v1/geocoder/autocomplete`     | Get place predictions as user types |
| GET    | `/v1/geocoder/places/{placeId}` | Get place details with coordinates  |

---

## Key Concepts

### Rate Limiting

Both endpoints are rate limited to **30 requests per minute** per authenticated user. Exceeding this limit returns HTTP 429 Too Many Requests.

### Caching

Results are cached for **24 hours** to reduce Google Maps API costs and improve response times. Cache keys include the language parameter, so requesting the same location in different languages creates separate cache entries.

**Caching rules by endpoint:**

| Endpoint            | Caching Behavior                                |
| ------------------- | ----------------------------------------------- |
| `/search`           | Always cached                                   |
| `/reverse`          | Always cached                                   |
| `/autocomplete`     | Cached only when `sessionToken` is NOT provided |
| `/places/{placeId}` | Always cached                                   |

### Session Tokens

Session tokens are an optional optimization for **autocomplete → place details** flows. They group multiple autocomplete requests and the final place details request into a single billing session.

**How it works:**

1. Client generates a UUID as `sessionToken`
2. Pass the same token to all autocomplete requests during a search session
3. Pass the same token to the final `/places/{placeId}` request after user selection
4. Generate a new token for the next search session

**Benefits:**

- Reduced API costs — Google bills autocomplete+place details as one session
- No caching when using tokens — Fresh results for each keystroke

**When to skip session tokens:**

- Simple one-off searches where caching is preferred
- Server-side batch operations

### Language Support

Results are localized based on the `Accept-Language` header. Supported languages:

| Language | Code | Example Result   |
| -------- | ---- | ---------------- |
| English  | `en` | Split, Croatia   |
| Polish   | `pl` | Split, Chorwacja |
| German   | `de` | Split, Kroatien  |
| French   | `fr` | Split, Croatie   |
| Spanish  | `es` | Split, Croacia   |
| Italian  | `it` | Split, Croazia   |

### Coordinate System

The API uses **WGS84** coordinate format:

- **Latitude**: -90 to 90 (negative = South, positive = North)
- **Longitude**: -180 to 180 (negative = West, positive = East)

---

## Search Locations

```http
GET /v1/geocoder/search
```

Search for locations by name, address, or place name. Returns matching locations with coordinates and address details.

### Headers

| Header            | Value                              | Description                                    |
| ----------------- | ---------------------------------- | ---------------------------------------------- |
| `Authorization`   | `Bearer <token>`                   | Required. JWT access token                     |
| `Accept-Language` | `en`, `pl`, `de`, `fr`, `es`, `it` | Optional. Language for results (default: `en`) |

### Query Parameters

| Parameter | Type    | Required | Default | Description                              |
| --------- | ------- | -------- | ------- | ---------------------------------------- |
| `query`   | string  | Yes      | —       | Address or place name (1-255 characters) |
| `limit`   | integer | No       | 10      | Maximum results (1-20)                   |

### Example Request

```http
GET /v1/geocoder/search?query=Split,Croatia&limit=5 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept-Language: en
```

### Response

**200 OK**

```json
{
  "data": [
    {
      "formattedAddress": "Split, Croatia",
      "coordinates": {
        "lat": 43.5081,
        "lng": 16.4402
      },
      "placeId": "ChIJLYsKc_FPahQRq0aE88E8eUE",
      "types": ["locality", "political"],
      "addressComponents": [
        {
          "longName": "Split",
          "shortName": "Split",
          "types": ["locality", "political"]
        },
        {
          "longName": "Split-Dalmatia County",
          "shortName": "Split-Dalmatia County",
          "types": ["administrative_area_level_1", "political"]
        },
        {
          "longName": "Croatia",
          "shortName": "HR",
          "types": ["country", "political"]
        }
      ],
      "name": "Split",
      "rating": 4.5,
      "userRatingsTotal": 1250
    }
  ]
}
```

### Response Fields

| Field               | Type     | Description                                        |
| ------------------- | -------- | -------------------------------------------------- |
| `formattedAddress`  | string   | Full formatted address string                      |
| `coordinates.lat`   | number   | Latitude in WGS84 format                           |
| `coordinates.lng`   | number   | Longitude in WGS84 format                          |
| `placeId`           | string   | Google Place ID for further lookups (optional)     |
| `types`             | string[] | Address type classifications (optional)            |
| `addressComponents` | array    | Detailed address breakdown (optional)              |
| `name`              | string   | Place or business name (optional, only for search) |
| `rating`            | number   | Place rating from 1.0 to 5.0 (optional)            |
| `userRatingsTotal`  | integer  | Total number of user ratings (optional)            |

---

## Reverse Geocode

```http
GET /v1/geocoder/reverse
```

Convert geographic coordinates to human-readable addresses. Returns multiple address options with varying levels of detail.

### Headers

| Header            | Value                              | Description                                    |
| ----------------- | ---------------------------------- | ---------------------------------------------- |
| `Authorization`   | `Bearer <token>`                   | Required. JWT access token                     |
| `Accept-Language` | `en`, `pl`, `de`, `fr`, `es`, `it` | Optional. Language for results (default: `en`) |

### Query Parameters

| Parameter | Type    | Required | Default | Description             |
| --------- | ------- | -------- | ------- | ----------------------- |
| `lat`     | number  | Yes      | —       | Latitude (-90 to 90)    |
| `lng`     | number  | Yes      | —       | Longitude (-180 to 180) |
| `limit`   | integer | No       | 10      | Maximum results (1-20)  |

### Example Request

```http
GET /v1/geocoder/reverse?lat=43.5081&lng=16.4402&limit=3 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept-Language: pl
```

### Response

**200 OK**

```json
{
  "data": [
    {
      "formattedAddress": "Riva 1, 21000, Split, Chorwacja",
      "coordinates": {
        "lat": 43.5081,
        "lng": 16.4402
      },
      "placeId": "ChIJLYsKc_FPahQRq0aE88E8eUE",
      "types": ["street_address"],
      "addressComponents": [
        {
          "longName": "1",
          "shortName": "1",
          "types": ["street_number"]
        },
        {
          "longName": "Riva",
          "shortName": "Riva",
          "types": ["route"]
        },
        {
          "longName": "Split",
          "shortName": "Split",
          "types": ["locality", "political"]
        },
        {
          "longName": "Chorwacja",
          "shortName": "HR",
          "types": ["country", "political"]
        }
      ]
    },
    {
      "formattedAddress": "Split, Chorwacja",
      "coordinates": {
        "lat": 43.5081,
        "lng": 16.4402
      },
      "placeId": "ChIJ...",
      "types": ["locality", "political"]
    }
  ]
}
```

---

## Autocomplete

```http
GET /v1/geocoder/autocomplete
```

Get real-time place predictions as the user types. Ideal for search-as-you-type interfaces.

### Headers

| Header            | Value                              | Description                                    |
| ----------------- | ---------------------------------- | ---------------------------------------------- |
| `Authorization`   | `Bearer <token>`                   | Required. JWT access token                     |
| `Accept-Language` | `en`, `pl`, `de`, `fr`, `es`, `it` | Optional. Language for results (default: `en`) |

### Query Parameters

| Parameter      | Type   | Required | Default | Description                                           |
| -------------- | ------ | -------- | ------- | ----------------------------------------------------- |
| `input`        | string | Yes      | —       | Search text (1-255 characters)                        |
| `sessionToken` | string | No       | —       | UUID for billing optimization                         |
| `types`        | string | No       | —       | Place type filter (e.g., `(cities)`, `establishment`) |

### Example Request

```http
GET /v1/geocoder/autocomplete?input=Marina Split&sessionToken=550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept-Language: en
```

### Response

**200 OK**

```json
{
  "data": [
    {
      "description": "Marina Kaštela, Put Divulja, Kaštel Gomilica, Croatia",
      "placeId": "ChIJLYsKc_FPahQRq0aE88E8eUE",
      "structuredFormatting": {
        "mainText": "Marina Kaštela",
        "mainTextMatchedSubstrings": [{ "offset": 0, "length": 6 }],
        "secondaryText": "Put Divulja, Kaštel Gomilica, Croatia"
      },
      "types": ["marina", "point_of_interest", "establishment"]
    },
    {
      "description": "ACI Marina Split, Uvala Baluni, Split, Croatia",
      "placeId": "ChIJ...",
      "structuredFormatting": {
        "mainText": "ACI Marina Split",
        "mainTextMatchedSubstrings": [{ "offset": 4, "length": 6 }],
        "secondaryText": "Uvala Baluni, Split, Croatia"
      },
      "types": ["marina", "point_of_interest", "establishment"]
    }
  ]
}
```

### Response Fields

| Field                                            | Type     | Description                                          |
| ------------------------------------------------ | -------- | ---------------------------------------------------- |
| `description`                                    | string   | Full prediction text                                 |
| `placeId`                                        | string   | Google Place ID for `/places/{placeId}` lookup       |
| `structuredFormatting.mainText`                  | string   | Main place name                                      |
| `structuredFormatting.mainTextMatchedSubstrings` | array    | Matched portions for highlighting                    |
| `structuredFormatting.secondaryText`             | string   | Secondary text (address, region)                     |
| `types`                                          | string[] | Place type classifications                           |
| `distanceMeters`                                 | integer  | Distance in meters from the search origin (optional) |

---

## Place Details

```http
GET /v1/geocoder/places/{placeId}
```

Get detailed place information including coordinates. Use this after user selects an autocomplete prediction.

### Headers

| Header            | Value                              | Description                                    |
| ----------------- | ---------------------------------- | ---------------------------------------------- |
| `Authorization`   | `Bearer <token>`                   | Required. JWT access token                     |
| `Accept-Language` | `en`, `pl`, `de`, `fr`, `es`, `it` | Optional. Language for results (default: `en`) |

### Path Parameters

| Parameter | Type   | Required | Description                       |
| --------- | ------ | -------- | --------------------------------- |
| `placeId` | string | Yes      | Google Place ID from autocomplete |

### Query Parameters

| Parameter      | Type   | Required | Default | Description                             |
| -------------- | ------ | -------- | ------- | --------------------------------------- |
| `sessionToken` | string | No       | —       | Same UUID used in autocomplete requests |

### Example Request

```http
GET /v1/geocoder/places/ChIJLYsKc_FPahQRq0aE88E8eUE?sessionToken=550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
Accept-Language: en
```

### Response

**200 OK**

```json
{
  "placeId": "ChIJLYsKc_FPahQRq0aE88E8eUE",
  "name": "ACI Marina Split",
  "formattedAddress": "Uvala Baluni bb, 21000, Split, Croatia",
  "coordinates": {
    "lat": 43.5023,
    "lng": 16.4318
  },
  "types": ["marina", "point_of_interest", "establishment"],
  "addressComponents": [
    {
      "longName": "Uvala Baluni bb",
      "shortName": "Uvala Baluni bb",
      "types": ["route"]
    },
    {
      "longName": "Split",
      "shortName": "Split",
      "types": ["locality", "political"]
    },
    {
      "longName": "Croatia",
      "shortName": "HR",
      "types": ["country", "political"]
    }
  ],
  "formattedPhoneNumber": "+385 21 398 548",
  "internationalPhoneNumber": "+385 21 398 548",
  "website": "https://www.aci-marinas.com/aci_marina/aci-marina-split/",
  "rating": 4.3,
  "userRatingsTotal": 245,
  "openingHours": {
    "openNow": true,
    "weekdayText": [
      "Monday: 8:00 AM – 8:00 PM",
      "Tuesday: 8:00 AM – 8:00 PM",
      "..."
    ]
  }
}
```

### Response Fields

| Field                      | Type     | Description                           |
| -------------------------- | -------- | ------------------------------------- |
| `placeId`                  | string   | Google Place ID                       |
| `name`                     | string   | Place name                            |
| `formattedAddress`         | string   | Full formatted address                |
| `coordinates.lat`          | number   | Latitude in WGS84 format              |
| `coordinates.lng`          | number   | Longitude in WGS84 format             |
| `types`                    | string[] | Place type classifications            |
| `addressComponents`        | array    | Detailed address breakdown            |
| `formattedPhoneNumber`     | string   | Local phone number (optional)         |
| `internationalPhoneNumber` | string   | International phone format (optional) |
| `website`                  | string   | Official website URL (optional)       |
| `rating`                   | number   | Rating from 1.0 to 5.0 (optional)     |
| `userRatingsTotal`         | integer  | Total number of ratings (optional)    |
| `openingHours`             | object   | Opening hours info (optional)         |

---

## Use Case: Post Creation with Autocomplete

The recommended flow for location selection uses autocomplete with session tokens:

```mermaid
sequenceDiagram
    participant User
    participant App
    participant API

    User->>App: Start typing "Marina Sp"
    App->>App: Generate sessionToken (UUID)
    App->>API: GET /autocomplete?input=Marina Sp&sessionToken=xxx
    API-->>App: Predictions list
    App->>User: Show suggestions dropdown

    User->>App: Continue typing "Marina Split"
    App->>API: GET /autocomplete?input=Marina Split&sessionToken=xxx
    API-->>App: Updated predictions
    App->>User: Update suggestions

    User->>App: Select "ACI Marina Split"
    App->>API: GET /places/{placeId}?sessionToken=xxx
    API-->>App: Full place details with coordinates
    App->>App: Fill locationName + coordinates

    User->>App: Submit post
    App->>API: POST /v1/posts { locationName, coordinates, ... }
```

---

## Use Case: Post Creation

When creating a post, the mobile/web app can use the geocoder to help users fill in location information:

```mermaid
sequenceDiagram
    participant User
    participant App
    participant API

    User->>App: Start creating post

    alt User has GPS location
        App->>App: Get device coordinates
        App->>API: GET /v1/geocoder/reverse?lat=43.5&lng=16.4
        API-->>App: Address suggestions
        App->>App: Auto-fill locationName
    else User types location
        User->>App: Type "Split marina"
        App->>API: GET /v1/geocoder/search?query=Split marina
        API-->>App: Location results
        User->>App: Select location
        App->>App: Fill locationName + coordinates
    end

    User->>App: Submit post
    App->>API: POST /v1/posts { locationName, coordinates, ... }
```

---

## Error Handling

All errors follow RFC 7807 Problem Details format.

### Error Responses

| Status | Type                                   | Description                                   |
| ------ | -------------------------------------- | --------------------------------------------- |
| 401    | `/errors/unauthorized`                 | Missing or invalid authentication token       |
| 404    | `/errors/place-not-found`              | Place ID not found (for `/places/{placeId}`)  |
| 422    | `/errors/validation`                   | Invalid query parameters                      |
| 429    | `/errors/too-many-requests`            | Rate limit exceeded (30 req/min)              |
| 503    | `/errors/geocoder-service-unavailable` | Google Maps API unavailable or not configured |

### Example Error Response

**503 Service Unavailable**

```json
{
  "type": "/errors/geocoder-service-unavailable",
  "title": "Geocoding Service Unavailable",
  "status": 503,
  "detail": "The geocoding service is temporarily unavailable. Please try again later."
}
```

### Example Error Response (404)

**404 Not Found**

```json
{
  "type": "/errors/place-not-found",
  "title": "Place Not Found",
  "status": 404,
  "detail": "Place with ID 'invalid-place-id' was not found"
}
```

### Localized Errors

Error messages support localization via `Accept-Language` header:

| Language | Error               | Title                           | Detail                                                                     |
| -------- | ------------------- | ------------------------------- | -------------------------------------------------------------------------- |
| `en`     | Service Unavailable | Geocoding Service Unavailable   | The geocoding service is temporarily unavailable. Please try again later.  |
| `pl`     | Service Unavailable | Usługa geokodowania niedostępna | Usługa geokodowania jest tymczasowo niedostępna. Spróbuj ponownie później. |
| `en`     | Place Not Found     | Place Not Found                 | Place with ID '{placeId}' was not found                                    |
| `pl`     | Place Not Found     | Miejsce nie znalezione          | Miejsce o ID '{placeId}' nie zostało znalezione                            |

---

## Best Practices

1. **Use autocomplete for user input** — Prefer `/autocomplete` + `/places/{placeId}` over `/search` for user-facing location fields
2. **Implement debouncing** — Wait 300-500ms after user stops typing before calling autocomplete
3. **Use session tokens** — Generate a UUID per search session to optimize Google billing
4. **Cache results client-side** — Reduce API calls by caching location searches locally
5. **Use reverse geocoding sparingly** — Only call when user location changes significantly
6. **Handle errors gracefully** — Show fallback UI for 503 (service unavailable) and helpful message for 404 (place not found)
7. **Respect rate limits** — 30 requests/minute limit applies to all endpoints combined
8. **Pass user's language** — Always include `Accept-Language` for localized results
9. **Highlight matched text** — Use `mainTextMatchedSubstrings` to highlight matching portions in autocomplete dropdown

---

## Related

- [Posts](../posts/index.md) — Use geocoder to populate `locationName` and `coordinates` when creating posts
- [OpenAPI Specification](../openapi.yaml) — Machine-readable API contract
- [Error Handling](../getting-started/errors.md) — RFC 7807 error format details
