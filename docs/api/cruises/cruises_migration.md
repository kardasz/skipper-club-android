# Cruise Geolocation Migration Guide

This document helps client teams migrate to the new cruise geolocation data model. The backend now stores port locations as structured objects with GPS coordinates instead of plain-text strings, and replaces `waypoints` with a richer `stops` array.

---

## Breaking Changes Summary

| Change                                            | Impact                                                                          |
| ------------------------------------------------- | ------------------------------------------------------------------------------- |
| `departurePort` changed from `string` to `object` | All create, update, patch requests and response parsing must use the new format |
| `arrivalPort` changed from `string` to `object`   | Same as above                                                                   |
| `waypoints` (string array) removed                | Replaced by `stops` (object array with coordinates)                             |
| `stops` added (object array)                      | New field for intermediate stops with name + coordinates                        |

---

## Field Mapping

| Old Field       | New Field             | Type Change                                           | Notes                                                                    |
| --------------- | --------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------ |
| `departurePort` | `departurePort`       | `string` -> `{ name, coordinates: { lat, lng } }`     | Name preserves the original string value                                 |
| `arrivalPort`   | `arrivalPort`         | `string` -> `{ name, coordinates: { lat, lng } }`     | Name preserves the original string value                                 |
| `waypoints`     | `stops`               | `string[]` -> `{ name, coordinates: { lat, lng } }[]` | Each waypoint string becomes a stop `name`; coordinates are now required |
| _(none)_        | `stops[].coordinates` | _(new)_                                               | GPS coordinates (`lat`/`lng`) for each stop                              |

---

## Before / After Examples

### Create Cruise

**Before:**

```json
{
  "title": "Croatian Coast Adventure",
  "departurePort": "Split, Croatia",
  "arrivalPort": "Dubrovnik, Croatia",
  "waypoints": ["Hvar", "Korcula", "Mljet"]
}
```

**After:**

```json
{
  "title": "Croatian Coast Adventure",
  "departurePort": {
    "name": "Split, Croatia",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalPort": {
    "name": "Dubrovnik, Croatia",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
    { "name": "Korcula", "coordinates": { "lat": 42.9597, "lng": 17.1364 } },
    { "name": "Mljet", "coordinates": { "lat": 42.7442, "lng": 17.5431 } }
  ]
}
```

### Update Cruise (PUT)

**Before:**

```json
{
  "departurePort": "Split, Croatia",
  "arrivalPort": "Dubrovnik, Croatia"
}
```

**After:**

```json
{
  "departurePort": {
    "name": "Split, Croatia",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalPort": {
    "name": "Dubrovnik, Croatia",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  }
}
```

### Partial Update (PATCH)

**Before:**

```json
{
  "departurePort": "Athens, Greece"
}
```

**After:**

```json
{
  "departurePort": {
    "name": "Athens, Greece",
    "coordinates": { "lat": 37.9364, "lng": 23.6475 }
  }
}
```

### Response (GET / List)

**Before:**

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "departurePort": "Split, Croatia",
  "arrivalPort": "Dubrovnik, Croatia",
  "waypoints": ["Hvar", "Korcula"]
}
```

**After:**

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "departurePort": {
    "name": "Split, Croatia",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalPort": {
    "name": "Dubrovnik, Croatia",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
    { "name": "Korcula", "coordinates": { "lat": 42.9597, "lng": 17.1364 } }
  ]
}
```

### AI Draft

**Before:**

```json
{
  "departurePort": "Split",
  "arrivalPort": "Dubrovnik",
  "waypoints": ["Hvar", "Korcula"]
}
```

**After:**

```json
{
  "departurePort": {
    "name": "Split",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalPort": {
    "name": "Dubrovnik",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
    { "name": "Korcula", "coordinates": { "lat": 42.9597, "lng": 17.1364 } }
  ]
}
```

---

## Port Object Schema

```typescript
interface CruisePort {
  name: string; // Human-readable port name (e.g. "Split, Croatia")
  coordinates: {
    lat: number; // Latitude (-90 to 90)
    lng: number; // Longitude (-180 to 180)
  };
}
```

Both `departurePort` and `arrivalPort` use this schema. Each entry in the `stops` array uses the same shape.

---

## Rollout Strategy

1. **Backend deploys first** -- The new API is fully backward-incompatible. Old clients sending string ports will receive 422 validation errors.
2. **Client update required** -- All clients must update their cruise create/update forms to send port objects with coordinates before users can interact with cruises.
3. **Existing data migrated automatically** -- All existing cruises have been backfilled with default coordinates (Split, Croatia: `43.5081, 16.4402`). The `waypoints` column has been migrated to `stops` with the same default coordinates.

### Expected Errors During Transition

If an old client sends the previous string format:

```json
{
  "type": "/errors/validation",
  "title": "Validation Failed",
  "status": 422,
  "detail": "The request contains invalid data",
  "violations": [
    {
      "propertyPath": "departurePort",
      "message": "departurePort must be a valid port object with name and coordinates"
    }
  ]
}
```

If a client sends the removed `waypoints` field, it will be silently ignored (no error).

---

## QA Checklist

Client teams should verify the following after updating:

- [ ] **Create cruise** -- Form sends `departurePort` and `arrivalPort` as objects with `name` and `coordinates`
- [ ] **Create cruise with stops** -- Stops are sent as objects with `name` and `coordinates` (not plain strings)
- [ ] **Update cruise (PUT)** -- Full update sends port objects, not strings
- [ ] **Partial update (PATCH)** -- Partial update with port fields sends objects
- [ ] **List cruises** -- Response parser handles port objects (not strings) in list view
- [ ] **Cruise detail** -- Response parser handles port objects and `stops` array in detail view
- [ ] **AI draft** -- Draft response parser handles port objects and `stops` array
- [ ] **Map integration** -- Coordinates from port objects render correctly on maps
- [ ] **No `waypoints` references** -- All client code referencing `waypoints` has been updated to use `stops`
- [ ] **Error handling** -- Validation errors for malformed port objects are displayed to users
- [ ] **Existing cruises** -- Backfilled cruises with default coordinates display correctly

---

## Related

- [Cruise Lifecycle](./lifecycle.md) -- Create, update, and delete cruise documentation
- [AI Draft](./ai-draft.md) -- AI-generated cruise drafts with port coordinates
- [OpenAPI Specification](../../api/openapi.yaml) -- Full API schema reference
