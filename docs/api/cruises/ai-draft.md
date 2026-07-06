# Cruise AI Draft

`POST /v1/cruises/ai-draft` turns a natural-language cruise description into
a structured cruise draft. The endpoint still returns a 200 response with
best-effort defaults when AI extraction is incomplete.

Request:

```json
{
  "description": "A week-long sailing trip in Croatia starting from Split on July 15th, 2026. Bavaria 46 Cruiser, 1500 EUR per person."
}
```

Response fields include title, description, dates, departure and arrival ports
with coordinates, optional stops, cost, currency, capacity, vessel details,
cruise type, and rule flags.

`regionCode` is no longer produced or accepted. Location context is represented
by port and stop coordinates.

Validation errors still return 422 for invalid request descriptions.
