# Cruise Lifecycle

This document covers creating, updating, and deleting cruises in the SkipperClub API.

## Overview

The cruise lifecycle includes:

1. **Creation** — Organizer creates a cruise with details
2. **Discovery** — Users search and filter available cruises
3. **Updates** — Organizer modifies cruise details
4. **Deletion** — Organizer removes the cruise
5. **Post-cruise** — After the cruise ends, participants are automatically reminded to review their fellow crew members via a daily cron job (`CRUISE_REVIEW_REMINDER`). The reminder is sent 1 day after the arrival date through WebSocket, push notification, and email. Users who have already reviewed all other participants are skipped.

## Create Cruise

```http
POST /cruises
```

Creates a new cruise with the authenticated user as the organizer.

> **💡 Tip:** Use the [AI Draft endpoint](./ai-draft.md) to quickly generate a cruise proposal from a text description before creating the final cruise.

### Request Body

| Field             | Type     | Required | Description                                                                                                                    |
| ----------------- | -------- | -------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `title`           | string   | Yes      | Cruise title (3-255 chars)                                                                                                     |
| `description`     | string   | Yes      | Detailed description (10-2000 chars). Can contain hashtags (e.g., `#sailing`, `#adventure`) which are automatically extracted. |
| `departureDate`   | date     | Yes      | Departure date (ISO 8601)                                                                                                      |
| `departurePort`   | object   | Yes      | Starting port (`{ name, coordinates: { lat, lng } }`)                                                                          |
| `arrivalDate`     | date     | Yes      | Arrival date (ISO 8601)                                                                                                        |
| `arrivalPort`     | object   | Yes      | Destination port (`{ name, coordinates: { lat, lng } }`)                                                                       |
| `costPerPerson`   | number   | Yes      | Cost per participant (0-100000, 2 decimals)                                                                                    |
| `currency`        | enum     | Yes      | `PLN`, `EUR`, or `USD`                                                                                                         |
| `maxParticipants` | integer  | Yes      | Maximum participants (1-20)                                                                                                    |
| `private`         | boolean  | Yes      | Whether cruise is private                                                                                                      |
| `vessel`          | string   | Yes      | Vessel name/description (5-255 chars)                                                                                          |
| `vesselType`      | enum     | Yes      | See [Vessel Types](#vessel-types)                                                                                              |
| `stops`           | object[] | No       | Intermediate stops (max 20, each `{ name, coordinates: { lat, lng } }`)                                                        |
| `requiredSkills`  | string   | No       | Required sailing skills (5-1000 chars)                                                                                         |
| `mediaIds`        | uuid[]   | No       | Media attachment IDs (1-10, must exist)                                                                                        |
| `vesselBrand`     | string   | No       | Vessel manufacturer (2-100 chars)                                                                                              |
| `vesselModel`     | string   | No       | Vessel model (2-100 chars)                                                                                                     |
| `vesselYear`      | integer  | No       | Build year (1950-2030)                                                                                                         |
| `vesselLength`    | number   | No       | Length in feet (15-200)                                                                                                        |
| `vesselCabins`    | integer  | No       | Number of cabins (1-20)                                                                                                        |
| `regionCode`      | string   | No       | Sailing region code (max 50 chars). Must reference an existing region.                                                         |
| `type`            | enum     | No       | See [Cruise Types](#cruise-types)                                                                                              |
| `smokingAllowed`  | boolean  | No       | Smoking policy                                                                                                                 |
| `alcoholAllowed`  | boolean  | No       | Alcohol policy                                                                                                                 |
| `petsAllowed`     | boolean  | No       | Pets policy                                                                                                                    |
| `childrenAllowed` | boolean  | No       | Children policy                                                                                                                |

### Example Request

```http
POST /v1/cruises HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Croatian Coast Adventure",
  "description": "Week-long sailing trip exploring the beautiful Dalmatian coast. We will visit Split, Hvar, Korčula, and finish in Dubrovnik. Perfect for intermediate sailors looking to build experience.",
  "departureDate": "2025-07-15",
  "departurePort": { "name": "Split, Croatia", "coordinates": { "lat": 43.5081, "lng": 16.4402 } },
  "arrivalDate": "2025-07-22",
  "arrivalPort": { "name": "Dubrovnik, Croatia", "coordinates": { "lat": 42.6507, "lng": 18.0944 } },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
    { "name": "Korčula", "coordinates": { "lat": 42.9597, "lng": 17.1364 } },
    { "name": "Mljet", "coordinates": { "lat": 42.7442, "lng": 17.5431 } }
  ],
  "requiredSkills": "Basic sailing experience preferred but not required",
  "costPerPerson": 850,
  "currency": "EUR",
  "maxParticipants": 6,
  "private": false,
  "vessel": "Bavaria Cruiser 46",
  "vesselType": "SAILING_YACHT",
  "vesselBrand": "Bavaria",
  "vesselModel": "Cruiser 46",
  "vesselYear": 2020,
  "vesselLength": 46,
  "vesselCabins": 4,
  "regionCode": "MED.ADR.CRO",
  "type": "RELAX",
  "smokingAllowed": false,
  "alcoholAllowed": true,
  "childrenAllowed": true
}
```

### Response

**201 Created**

```json
{
  "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
  "organizerId": "018fa2e4-1111-7b2e-8e3b-7b2e8e3b7b00",
  "title": "Croatian Coast Adventure",
  "description": "Week-long sailing trip exploring the beautiful Dalmatian coast...",
  "departureDate": "2025-07-15",
  "departurePort": {
    "name": "Split, Croatia",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalDate": "2025-07-22",
  "arrivalPort": {
    "name": "Dubrovnik, Croatia",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "stops": [
    { "name": "Hvar", "coordinates": { "lat": 43.1729, "lng": 16.4411 } },
    { "name": "Korčula", "coordinates": { "lat": 42.9597, "lng": 17.1364 } },
    { "name": "Mljet", "coordinates": { "lat": 42.7442, "lng": 17.5431 } }
  ],
  "requiredSkills": "Basic sailing experience preferred but not required",
  "costPerPerson": 850,
  "currency": "EUR",
  "maxParticipants": 6,
  "participantsCount": 0,
  "private": false,
  "vessel": "Bavaria Cruiser 46",
  "vesselType": "SAILING_YACHT",
  "vesselBrand": "Bavaria",
  "vesselModel": "Cruiser 46",
  "vesselYear": 2020,
  "vesselLength": 46,
  "vesselCabins": 4,
  "type": "RELAX",
  "smokingAllowed": false,
  "alcoholAllowed": true,
  "petsAllowed": null,
  "childrenAllowed": true,
  "createdAt": "2025-01-15T10:30:00.000Z",
  "updatedAt": "2025-01-15T10:30:00.000Z",
  "organizer": {
    "id": "018fa2e4-1111-7b2e-8e3b-7b2e8e3b7b00",
    "name": "Captain Jack",
    "avatarUrl": "https://cdn.example.com/avatars/captain-jack.jpg"
  },
  "media": [],
  "participants": [],
  "region": {
    "code": "MED.ADR.CRO",
    "name": "Croatia",
    "localizedName": "Croatia",
    "localizedParents": ["Mediterranean Sea", "Adriatic Sea"]
  },
  "currentUserRole": "organizer",
  "currentUserParticipation": null
}
```

The `Location` header contains the URI of the created cruise.

### Side Effects

When a cruise is created:

1. A **group chat** is automatically created for the cruise
2. The organizer is added as the first participant in the group chat
3. A `CruiseCreatedEvent` is published for async processing

### Errors

| Status | Type                              | Description                            |
| ------ | --------------------------------- | -------------------------------------- |
| 400    | Bad Request                       | Invalid dates or media files not found |
| 422    | `/errors/cruise-region-not-found` | Provided `regionCode` does not exist   |
| 422    | Validation Error                  | Request body fails validation          |

---

## Get Cruise

```http
GET /cruises/{cruiseId}
```

Retrieves details of a specific cruise.

### Path Parameters

| Parameter  | Type | Description    |
| ---------- | ---- | -------------- |
| `cruiseId` | uuid | Cruise UUID v7 |

### Example Request

```http
GET /v1/cruises/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

### Response

**200 OK**

Returns the cruise object with user context:

- `currentUserRole` — User's relationship to the cruise (`organizer`, `participant`, or `none`)
- `currentUserParticipation` — Participant record if user is involved (with state)

### Errors

| Status | Type                       | Description           |
| ------ | -------------------------- | --------------------- |
| 404    | `/errors/cruise-not-found` | Cruise does not exist |

---

## List Cruises

```http
GET /cruises
```

Returns a paginated list of cruises with optional filtering.

### Query Parameters

#### Pagination

| Parameter | Type    | Default         | Description                |
| --------- | ------- | --------------- | -------------------------- |
| `limit`   | integer | 20              | Results per page (1-100)   |
| `offset`  | integer | 0               | Results to skip            |
| `order`   | string  | `desc`          | Sort order (`asc`, `desc`) |
| `sort`    | string  | `departureDate` | Sort field                 |

**Sort fields**: `createdAt`, `departureDate`, `title`, `costPerPerson`, `vesselLength`, `vesselType`, `vesselYear`

#### Scope Filters

| Parameter | Type | Description                                                                |
| --------- | ---- | -------------------------------------------------------------------------- |
| `scope`   | enum | Filter by user relationship                                                |
| `state`   | enum | Filter by participation state (with `scope=mine` or `scope=participating`) |

**Scope values**:

| Value           | Description                                        |
| --------------- | -------------------------------------------------- |
| `all`           | Public cruises only (default)                      |
| `mine`          | All cruises where user is organizer or participant |
| `organized`     | Cruises organized by the user                      |
| `participating` | Cruises where user is a participant                |

#### Date Filters

| Parameter  | Type | Description                      |
| ---------- | ---- | -------------------------------- |
| `fromDate` | date | Departure date from (inclusive)  |
| `toDate`   | date | Departure date until (inclusive) |

#### Search & Location

| Parameter    | Type   | Description                             |
| ------------ | ------ | --------------------------------------- |
| `search`     | string | Search in title, description, ports     |
| `hashtag`    | string | Filter by hashtag (without #)           |
| `regionCode` | string | Filter by region (includes descendants) |

#### Vessel Filters

| Parameter         | Type    | Description                     |
| ----------------- | ------- | ------------------------------- |
| `vesselType`      | enum    | Filter by vessel type           |
| `vesselBrand`     | string  | Filter by brand (2-100 chars)   |
| `vesselModel`     | string  | Filter by model (2-100 chars)   |
| `vesselCabins`    | integer | Filter by cabin count (1-20)    |
| `vesselLengthMin` | number  | Minimum length in feet (15-200) |
| `vesselLengthMax` | number  | Maximum length in feet (15-200) |

#### Cruise Type & Rules

| Parameter         | Type    | Description               |
| ----------------- | ------- | ------------------------- |
| `type`            | enum    | Filter by cruise type     |
| `smokingAllowed`  | boolean | Filter by smoking policy  |
| `alcoholAllowed`  | boolean | Filter by alcohol policy  |
| `petsAllowed`     | boolean | Filter by pets policy     |
| `childrenAllowed` | boolean | Filter by children policy |

### Example Requests

**Search public cruises in the Mediterranean:**

```http
GET /v1/cruises?regionCode=MED&fromDate=2025-06-01&toDate=2025-09-30 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

**Get user's organized cruises:**

```http
GET /v1/cruises?scope=organized HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

**Find family-friendly cruises:**

```http
GET /v1/cruises?type=FAMILY&childrenAllowed=true&smokingAllowed=false HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

**Search by vessel:**

```http
GET /v1/cruises?vesselType=CATAMARAN&vesselLengthMin=40&vesselLengthMax=50 HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

### Response

**200 OK**

```json
{
  "cruises": [
    {
      "id": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e",
      "organizerId": "018fa2e4-1111-7b2e-8e3b-7b2e8e3b7b00",
      "title": "Croatian Coast Adventure",
      "departureDate": "2025-07-15",
      "departurePort": {
        "name": "Split, Croatia",
        "coordinates": { "lat": 43.5081, "lng": 16.4402 }
      },
      "arrivalDate": "2025-07-22",
      "arrivalPort": {
        "name": "Dubrovnik, Croatia",
        "coordinates": { "lat": 42.6507, "lng": 18.0944 }
      },
      "costPerPerson": 850,
      "currency": "EUR",
      "maxParticipants": 6,
      "participantsCount": 3,
      "private": false,
      "vesselType": "SAILING_YACHT",
      "type": "RELAX",
      "organizer": {
        "id": "018fa2e4-1111-7b2e-8e3b-7b2e8e3b7b00",
        "name": "Captain Jack"
      },
      "currentUserRole": "none"
    }
  ],
  "total": 42,
  "limit": 20,
  "offset": 0
}
```

---

## Update Cruise

```http
PUT /cruises/{cruiseId}
```

Fully updates a cruise. Only the organizer can update their cruise.

### Request Body

All fields from [Create Cruise](#create-cruise) except `mediaIds` behavior differs:

- Provided `mediaIds` replace all existing media associations
- Omitting `mediaIds` removes all media

### Example Request

```http
PUT /v1/cruises/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "title": "Croatian Coast Adventure - Updated",
  "description": "Updated description with more details...",
  "departureDate": "2025-07-15",
  "departurePort": { "name": "Split, Croatia", "coordinates": { "lat": 43.5081, "lng": 16.4402 } },
  "arrivalDate": "2025-07-22",
  "arrivalPort": { "name": "Dubrovnik, Croatia", "coordinates": { "lat": 42.6507, "lng": 18.0944 } },
  "costPerPerson": 900,
  "currency": "EUR",
  "maxParticipants": 8,
  "private": false,
  "vessel": "Bavaria Cruiser 46",
  "vesselType": "SAILING_YACHT"
}
```

### Response

**200 OK** — Returns the updated cruise object.

### Side Effects

When a cruise is updated:

- All **accepted participants** receive a `CRUISE_DETAILS_CHANGED` notification
- The organizer does not receive a notification for their own update

### Errors

| Status | Type                              | Description                          |
| ------ | --------------------------------- | ------------------------------------ |
| 403    | `/errors/cruise-forbidden`        | User is not the organizer            |
| 404    | `/errors/cruise-not-found`        | Cruise does not exist                |
| 422    | `/errors/cruise-region-not-found` | Provided `regionCode` does not exist |

---

## Partial Update Cruise

```http
PATCH /cruises/{cruiseId}
```

Partially updates a cruise. Only provided fields are updated.

### Example Request

```http
PATCH /v1/cruises/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "costPerPerson": 950,
  "maxParticipants": 7
}
```

### Response

**200 OK** — Returns the updated cruise object.

### Errors

Same as [Update Cruise](#update-cruise).

### Side Effects

Same as [Update Cruise](#update-cruise).

---

## Delete Cruise

```http
DELETE /cruises/{cruiseId}
```

Deletes a cruise. Only the organizer can delete their cruise.

### Example Request

```http
DELETE /v1/cruises/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b2e HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
```

### Response

**204 No Content**

### Side Effects

When a cruise is deleted:

- All participant records are removed (cascade)
- Associated media links are removed
- **Group chat persists** (historical messages are preserved)

### Errors

| Status | Type                       | Description               |
| ------ | -------------------------- | ------------------------- |
| 403    | `/errors/cruise-forbidden` | User is not the organizer |
| 404    | `/errors/cruise-not-found` | Cruise does not exist     |

---

## AI Draft Generation

```http
POST /cruises/ai-draft
```

Generates a structured cruise draft from a natural language description using AI.

### Request Body

| Field         | Type   | Required | Description                         |
| ------------- | ------ | -------- | ----------------------------------- |
| `description` | string | Yes      | Natural language cruise description |

### Example Request

```http
POST /v1/cruises/ai-draft HTTP/1.1
Host: api.skipperclub.app
Authorization: Bearer <token>
Content-Type: application/json

{
  "description": "I want to organize a week-long family sailing trip in Croatia in July 2025, starting from Split and ending in Dubrovnik. Budget around 800 EUR per person, on a comfortable yacht with 4 cabins."
}
```

### Response

**200 OK**

Returns a `CreateCruiseDto` object that can be used directly to create a cruise:

```json
{
  "title": "Family Sailing Adventure - Croatian Coast",
  "description": "Week-long family sailing trip exploring the beautiful Croatian coastline from Split to Dubrovnik...",
  "departureDate": "2025-07-15",
  "departurePort": {
    "name": "Split, Croatia",
    "coordinates": { "lat": 43.5081, "lng": 16.4402 }
  },
  "arrivalDate": "2025-07-22",
  "arrivalPort": {
    "name": "Dubrovnik, Croatia",
    "coordinates": { "lat": 42.6507, "lng": 18.0944 }
  },
  "costPerPerson": 800,
  "currency": "EUR",
  "maxParticipants": 8,
  "private": false,
  "vessel": "Sailing Yacht",
  "vesselType": "SAILING_YACHT",
  "vesselCabins": 4,
  "type": "FAMILY",
  "childrenAllowed": true
}
```

### Errors

| Status | Type                                 | Description                   |
| ------ | ------------------------------------ | ----------------------------- |
| 422    | `/errors/ai-draft-validation-failed` | AI response failed validation |
| 503    | `/errors/ai-service-unavailable`     | AI service is unavailable     |

---

## Reference

### Vessel Types

| Value           | Description                        |
| --------------- | ---------------------------------- |
| `SAILING_YACHT` | Traditional monohull sailing yacht |
| `CATAMARAN`     | Twin-hull sailing vessel           |
| `MOTORBOAT`     | Motor-powered vessel               |
| `TRIMARAN`      | Three-hull sailing vessel          |
| `GULET`         | Traditional wooden sailing vessel  |
| `SCHOONER`      | Multi-masted sailing vessel        |

### Cruise Types

| Category         | Values                                                                                        |
| ---------------- | --------------------------------------------------------------------------------------------- |
| **Skill Level**  | `BEGINNER_INTRO`, `TRAINING`, `MILEBUILDING`, `ADVANCED`, `SPORT_REGATTA`                     |
| **Demographics** | `FAMILY`, `SINGLES`, `COUPLES`, `SENIORS`, `WOMEN_ONLY`, `MEN_ONLY`                           |
| **Activity**     | `PARTY`, `RELAX`, `SURVIVAL`, `PHOTOGRAPHY`, `CULINARY`, `CULTURAL_HISTORICAL`, `EXPLORATION` |

### Currencies

| Value | Description  |
| ----- | ------------ |
| `PLN` | Polish Zloty |
| `EUR` | Euro         |
| `USD` | US Dollar    |

---

## Related

- [Participants](./participants.md) — Managing cruise participants
- [Invitations](./invitations.md) — Invitation and join request flows
- [Chats](./chats.md) — Cruise communication
- [Notifications](../notifications/index.md) — Cruise-related notifications
