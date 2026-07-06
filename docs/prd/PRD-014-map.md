# PRD-014: Unified Map

## Purpose

Define business requirements for a **single unified map experience** in SkipperClub that lets members visually discover community content — posts, spots, and other sailors' presence — on one map surface, without the client juggling several separate data sources.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Provide a single, fast, coherent map view across every location-aware feature of SkipperClub.
- Reduce friction in discovering sailing content (where to go, what's happening, who is nearby).
- Make spontaneous on-the-water discovery — marinas, recent posts, presence — feel as native as a navigation app.
- Keep map performance predictable on mobile clients by letting the server decide between individual markers and clusters.
- Future-proof the experience for area-based information such as navigation alerts.

## Scope

### In Scope

- One map data source that combines location-aware posts, the canonical spots directory, and active sailor check-ins.
- Selectable map layers — clients control which of posts / spots / check-ins are rendered.
- Server-side clustering when the visible area would otherwise be overwhelming.
- Anchoring every map marker to the corresponding detail screen (post, spot, check-in) via a stable identifier.
- Localized cluster labels (English / Polish).
- A "near me" mode for non-map entry points (deep links, system shortcuts, the "near me" button).
- Antimeridian-aware viewport queries so the Pacific view is not a special case for clients.
- A future-ready item shape that can carry **area** geometry (polygons / multi-polygons), enabling navigation alerts and other area-based content later.

### Out of Scope

- A new write path for any object type — map items are read-only projections of existing resources.
- Real-time push updates as objects appear or disappear from a viewport.
- Routing, distance calculations between two points, or turn-by-turn navigation.
- Public, unauthenticated map access.
- Friend-only or block-based filtering (community-wide visibility today; revisit when a blocks module exists).
- Historical or playback views of past presence.
- Custom user-defined map layers beyond the three product-defined source types.
- Multilingual marker names for posts and spots (single canonical name today).

## Personas and Roles

### Map Explorer

A community member who opens the map to see what is around — places to visit, recent posts, other sailors currently active.

Core business capabilities:

- Move and zoom the map and see relevant content update for the visible area.
- Toggle which content types appear on the map (e.g. marinas only, presence only, posts only, any combination).
- Tap a marker to open the matching detail screen.
- See a "zoom in for more" affordance when an area is too dense to render every marker.

### "Near Me" Discoverer

A community member who taps a "near me" button or opens a deep link into the app. They do not have a current viewport — only a coordinate.

Core business capabilities:

- Browse map content within a chosen radius around a coordinate.
- See each result ordered by proximity.

### Standard User (Default Role)

Any authenticated SkipperClub member. They can act as both Map Explorer and Near Me Discoverer at any moment.

### Administrator

Governance role responsible for product-level consistency, abuse prevention, and protecting the platform from runaway map queries.

Core business responsibilities:

- Oversee what the map exposes about user presence and respect the [Check-Ins](./PRD-012-check-ins.md) freshness window.
- Adopt friend-only / block-based filtering when such a module is introduced.
- Ensure cost and performance limits remain in effect as the dataset grows.

## Map Domain Model (Business View)

### 1. Unified Map Item

The map surfaces a **single, uniform shape** for every kind of object:

- Has a display **name** suitable for a marker label.
- Has an **anchor coordinate** used for marker placement, distance, and clustering.
- Has a **geometry** that the client can draw directly (today: a point; tomorrow: areas too).
- Carries **lightweight type-specific attributes** — only what is needed to draw the marker and choose between layers. Full content lives on the per-resource detail screens.

Each item carries the same identifier that the existing detail endpoints accept, so the client can move from a marker tap to the full screen without an extra lookup step.

### 2. Map Source Types

The map combines three current product source types:

- **Posts** — community-published location-aware content (marinas, places, photos, tips, time-sensitive warnings, etc.).
- **Spots** — the canonical community-curated directory of sailing places.
- **Check-ins** — recent presence signals from other sailors.

A fourth type — **area-based items** (navigation alerts) — is anticipated by the data model but not in this PRD's release scope.

### 3. Layer Selection

Clients can ask for any non-empty combination of source types. Examples the product must support:

- Spots only (marinas-focused planning view).
- Check-ins only (presence layer).
- Posts only (community-content layer).
- Any 2-of-3 combination.
- All three (default).

Layer choice is **always client-driven**; the server never decides which layers to show.

### 4. Spatial Modes

The map supports two ways of asking "what is in this area":

- **Viewport bounds** — the natural shape of every map screen. Primary mode for all interactive map experiences.
- **Center and radius** — used for "near me" buttons, system shortcuts, and deep links that arrive with a coordinate but no viewport. Not used as the main map-loading path.

A request must use exactly one of the two modes. Mixing them or providing one partially is rejected.

Viewport bounds may legally cross the antimeridian; clients are not required to special-case the Pacific view.

### 5. Visibility

The map honors each source type's existing visibility rules unchanged:

- Posts follow the same publish / expiration / soft-delete logic as the post feed.
- Spots respect soft deletion.
- Check-ins respect the freshness window (see [PRD-012](./PRD-012-check-ins.md)).

Posts without coordinates are never on the map. Authored posts in archived / expired / resolved states are not shown to their author on the map layer (this differs from author-only timelines).

### 6. Clusters

When a visible area would expose too many individual markers — by raw count, density, or sheer size — the map returns **clusters** instead of individual items. The product rules are:

- Cluster identifiers are **stable across requests** for the same area at the same precision, so the client can animate cluster transitions instead of flickering.
- A cluster shows: a representative position (the centroid of its members), the total count, a per-type breakdown (post / spot / check-in counts), and the bounding box of its members.
- A cluster's label is localized (English / Polish). When one type dominates (≥ 80 % of the members), the label is typed (e.g. "24 marinas").
- A cluster never reveals which specific items it contains — discovering individuals requires zooming in or narrowing the viewport.

### 7. Detail Preferences

Clients can hint at the map style they want:

- **Auto** (default) — server decides when to cluster based on the rules above.
- **Markers** — prefer individual markers; clusters appear only as a last resort to protect the response budget.
- **Summary** — always cluster (useful for heat-map style summaries at any zoom).

The product must surface back to the client whether their request was honored as-is or degraded (e.g. "you asked for markers but the area was too dense, so you got clusters").

### 8. Freshness Disclaimer

Because check-ins age out after the configured freshness window, the map response always communicates the current window (in hours) so clients can show the right "active in the last N hours" disclaimer next to check-in markers.

### 9. Per-Item Bookmarks

Every authenticated member sees their **own bookmark state** for each post directly on the map item, so the heart icon on a marker can be drawn without a second round trip.

### 10. Forward Compatibility

The unified map item shape is designed to accept area geometry (polygons and multi-polygons) so future content types — first and foremost navigation alerts — fit on the same map surface without a new schema or a new endpoint per type.

## Business Rules and Constraints

### Authentication

- The map is **authenticated**. There is no public, anonymous map view.
- This matches the authentication posture of the underlying sources (posts, spots, check-ins).

### Layer Filter Semantics

- An empty layer list is **invalid** — the request must specify which layers to render or omit the parameter to mean "all three".
- Post-only filters (e.g. narrowing posts by type or content) are silently ignored when posts are not part of the requested layers, instead of being treated as a contradiction.

### Spatial Limits

- The viewport has product-defined maximum spans, generous enough to accept any realistic mobile map zoom-out but small enough to reject globe-scale scans.
- Radius mode supports larger distances than `GET /spots` and `GET /check-ins` (because a "near me" map covers a wider area than a list of spots or sailors), but is still bounded to prevent abuse.

### Result Bounding

- The number of returned top-level entries is bounded by a configurable limit.
- When the area is too large or too dense to honor the limit with individual markers, the server escalates to clustering instead of truncating silently.
- Truncation is allowed only as a last-resort safety after the coarsest clustering precision; the response makes this explicit (so the client can show a "zoom in" hint instead of pretending the result is complete).

### Pagination

- The map is **not paginated** in the offset / cursor sense — it is viewport-scoped. Clients drill in by narrowing the area or layers, not by paging.

### Privacy

- Check-ins on the map follow the freshness window — they auto-age out. The map never extends the visibility of a check-in beyond what the check-ins module already allows.
- A future blocks module would extend to the map endpoint automatically; the product team owns adopting it when it ships.

### Localization

- Cluster labels are localized (English / Polish) based on the request's `Accept-Language`.
- The check-in marker's name falls back to a localized presence label when a member has no display name set.
- Item names (post location names, spot names) are not translated — they are stored as a single canonical value today.

## Acceptance Criteria

1. A single authenticated map endpoint exists that lets a client render every map-relevant SkipperClub object — posts, spots, check-ins — without calling each domain endpoint separately.
2. The endpoint supports viewport bounds (primary) and center+radius (non-viewport) spatial modes with documented limits and antimeridian handling.
3. Clients can choose any non-empty combination of source types (posts / spots / check-ins). Defaults to all three.
4. Existing visibility rules of posts, spots, and check-ins are preserved unchanged; the map endpoint does not introduce a new exposure path.
5. Every item carries the source resource identifier accepted by the corresponding detail endpoint, so a marker tap navigates directly to the detail screen.
6. Check-in markers use the sailor's display name as the primary label, with location name available as a secondary attribute.
7. The map clusters automatically when the area would otherwise be overwhelming, with stable cluster identifiers, type breakdowns, and bounding boxes.
8. Clients can express a detail preference (auto / markers / summary) and learn whether their request was honored or degraded.
9. The check-in freshness window is exposed in the response so clients display the correct "active in the last N hours" disclaimer.
10. Bookmark state for posts is included per-item so the marker UI does not need a second round trip.
11. The item shape supports `Point`, `Polygon`, and `MultiPolygon` geometry to host future area-based items such as navigation alerts without a new schema.
12. The map respects request localization (English / Polish) for cluster labels and presence fallbacks.

## Success Metrics

- Time-to-first-render for the map screen is consistently fast on production data (p95 ≤ 400 ms for a typical 10 × 10 km viewport).
- Mobile clients drop their previous per-domain (posts / spots / check-ins) map calls and use the unified endpoint exclusively for map screens.
- Holes in the corners of the visible map disappear from observable client behavior (no more bounds-to-radius conversions on the client side).
- Members report less friction discovering sailors and content around a chosen area than before the unified map.

## Related PRDs

- [PRD-002: Posts & Social Feed](./PRD-002-posts.md)
- [PRD-009: Location & Areas](./PRD-009-location.md)
- [PRD-012: Check-Ins (Location Presence)](./PRD-012-check-ins.md)
- [PRD-013: Spots Directory](./PRD-013-spots.md)
