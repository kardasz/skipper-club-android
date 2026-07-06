# Regions

The public regions module has been removed.

The old region picker is no longer part of the mobile product model, and
`regionCode` is no longer accepted by posts, map items, cruises, or sailing
brief lookup. Location-based behavior is now driven by coordinates, radius,
viewport bounds, and technical area polygons.

Removed endpoints:

- `GET /v1/regions`
- `GET /v1/regions/tree`

Replacement models:

- Feed and map discovery use post `location.point`, `location.area`,
  `contains`, and `postContains`.
- Cruise discovery can use `lat`, `lng`, and `distance` against departure or
  arrival port coordinates.
- Sailing brief lookup uses `brief_areas`, selected by current coordinates.
