# Cruise Lifecycle

Cruises are created, updated, listed, joined, and reviewed without region
assignment. The public cruise model stores explicit departure and arrival port
coordinates plus optional stops.

## Create

Required create fields include:

- `title`
- `description`
- `departureDate`
- `departurePort: { name, coordinates }`
- `arrivalDate`
- `arrivalPort: { name, coordinates }`
- `costPerPerson`
- `currency`
- `maxParticipants`
- `private`
- `vessel`
- `vesselType`

Optional fields include stops, required skills, media, vessel details, cruise
type, and rule flags.

`regionCode` is removed. The API does not resolve or return a `region` object.

## List

`GET /v1/cruises` supports date, search, vessel, participant, scope, rule,
type, limit, offset, sort, and order filters.

Location filtering:

```http
GET /v1/cruises?lat=43.5081&lng=16.4402&distance=50
```

The current semantics match cruises whose departure or arrival port is within
the radius.

## Update

`PUT /v1/cruises/{cruiseId}` fully replaces editable cruise details.
`PATCH /v1/cruises/{cruiseId}` partially updates them. Neither endpoint
accepts `regionCode`.

## AI Draft

Use [AI Draft](./ai-draft.md) to create a structured draft from free text.
