# Alert Categories

The `AlertCategory` enum classifies a navigation alert by what kind of
situation it describes. It is backed by the PostgreSQL `alert_category` enum
type and exposed on every alert response and on `MapAlertAttributes` map
items.

## Values

| Value                | Description                                                          |
| -------------------- | -------------------------------------------------------------------- |
| `navigation_warning` | Generic navigation hazard or advisory (e.g. floating debris, light). |
| `navtex`             | NAVTEX-style message.                                                |
| `notice_to_mariners` | Notice to Mariners-style message.                                    |
| `obstruction`        | A specific physical obstruction (wreck, submerged object, net).      |
| `works`              | Construction, dredging, cable laying, or other ongoing works.        |
| `regatta`            | Sailing race or regatta restricting an area.                         |
| `diving`             | Diving activity area — keep clear, slow speed.                       |
| `military_exercise`  | Live or training military exercise area.                             |
| `weather`            | Localized weather warning (bora, fog, squall, etc.).                 |
| `other`              | Anything that doesn't fit the categories above.                      |

```typescript
const ALERT_CATEGORIES = [
  'navigation_warning',
  'navtex',
  'notice_to_mariners',
  'obstruction',
  'works',
  'regatta',
  'diving',
  'military_exercise',
  'weather',
  'other',
];
```

## Marker Name Localization

On `/v1/map/items`, the alert item's `name` field is a short label derived
from the category and localized via `nestjs-i18n` against the request
`Accept-Language` header. Translation keys live under
`src/i18n/<lang>/alerts.json` (`CATEGORY_LABEL_<CATEGORY>`). English and
Polish translations are bundled for every category.

| Category             | English (`en`)     | Polski (`pl`)           |
| -------------------- | ------------------ | ----------------------- |
| `navigation_warning` | Navigation warning | Ostrzeżenie nawigacyjne |
| `navtex`             | NAVTEX             | NAVTEX                  |
| `notice_to_mariners` | Notice to mariners | Notice to mariners      |
| `obstruction`        | Obstruction        | Przeszkoda              |
| `works`              | Works              | Prace                   |
| `regatta`            | Regatta            | Regaty                  |
| `diving`             | Diving             | Nurkowanie              |
| `military_exercise`  | Military exercise  | Ćwiczenia wojskowe      |
| `weather`            | Weather alert      | Ostrzeżenie pogodowe    |
| `other`              | Alert              | Ostrzeżenie             |

The full alert body lives in `MapAlertAttributes.content` and is never
inlined into `name`.

## Filtering

`GET /v1/alerts` accepts a repeated `category` query parameter to narrow
results to one or more categories. Comma-separated values are not supported
— use the repeated form.

```http
GET /v1/alerts?category=weather&category=obstruction
```

There is no `category` filter on `/v1/map/items`. Use `types=navigation_alert`
to limit the unified map response to alert items only.

## Related

- [Alerts API](../../alerts/index.md)
- [Map API](../../map/index.md)
