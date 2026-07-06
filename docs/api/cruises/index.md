# Cruises

Cruises keep explicit departure and arrival port coordinates and optional
route stops. Region assignment has been removed from the public model.

## Create and Update

Cruise create/update payloads include:

- title and description
- departure date and departure port `{ name, coordinates }`
- arrival date and arrival port `{ name, coordinates }`
- optional stops with coordinates
- cost, currency, capacity, vessel details, rules, and type

`regionCode` is no longer accepted, resolved, stored, or returned.

## Response

Cruise responses include port and stop coordinates directly:

```json
{
  "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
  "title": "Central Dalmatia Week",
  "description": "Week-long sailing trip...",
  "departurePort": {
    "name": "Split",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalPort": {
    "name": "Dubrovnik",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } }
  ]
}
```

There is no `region` object in the response.

## List Filtering

`GET /v1/cruises` supports the existing date, search, vessel, participant,
scope, rules, type, limit, offset, sort, and order filters.

Location filtering uses:

```http
GET /v1/cruises?lat=43.5081&lng=16.4402&distance=50
```

The initial semantics match cruises whose departure or arrival port is within
the radius. Stops and route-geometry matching are intentionally deferred.

## AI Draft

The AI draft endpoint still turns natural-language descriptions into cruise
draft fields, but draft responses no longer include `regionCode`.
