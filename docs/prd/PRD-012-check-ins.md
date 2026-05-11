# PRD-012: Check-Ins (Location Presence)

## Purpose

Define business requirements for how members signal their **current sailing location** to the SkipperClub community and how other members **discover nearby active sailors** in real time.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Increase spontaneous, on-the-water interactions between sailors who are physically close.
- Lower the barrier to meeting other community members in marinas, harbors, and anchorages.
- Strengthen the perception of SkipperClub as a living, real-time community rather than a static directory.
- Drive cruise organizer / participant discovery beyond pre-planned recruitment flows.
- Provide a privacy-conscious presence model with built-in expiration so location signals never persist indefinitely.

## Scope

### In Scope

- Publishing the caller's **latest** geographic presence as a single, replaceable signal per user.
- Optional human-readable place label, either user-provided or assisted by reverse geocoding.
- Localized place names aligned with the user's preferred language.
- Discovery of nearby active sailors within a bounded search radius.
- Time-based expiration ("freshness window") so stale presence signals stop appearing in discovery.
- Inclusion of the caller's own check-in in nearby results when within the requested radius.

### Out of Scope

- Historical trail of past locations or movement tracking.
- Friend-only or selective audience visibility (current scope is community-wide).
- Push notifications for nearby sailors.
- Group / cruise-level presence (only individual presence).
- Manual deletion of an existing check-in (presence ages out via the freshness window).
- Background or continuous location tracking from the device.
- Public presence visible to non-authenticated visitors.

## Personas and Roles

### Active Sailor

A community member who is on the water, in a marina, or traveling with sailing intent and wants to make their presence visible to nearby sailors.

Core business capabilities:

- Publish or refresh their current location with one action.
- Choose between a custom location label or an assisted, geocoder-derived label.
- Trust that their presence will automatically stop being shown after the freshness window.

### Nearby Discoverer

A community member who wants to see which other sailors are currently active around a chosen point on the map.

Core business capabilities:

- Search for active sailors near a coordinate of interest.
- Adjust the search radius within product-defined bounds.
- See each result ordered by proximity.

### Standard User (Default Role)

Any authenticated SkipperClub member, who can act as both Active Sailor and Nearby Discoverer at any moment.

### Administrator

Governance role responsible for product-level consistency and abuse prevention.

Core business responsibilities:

- Oversee freshness policy and ensure presence does not become a tracking surface.
- Adjust freshness window configuration when product needs evolve.

## Check-In Domain Model (Business View)

### 1. Latest Presence Signal

- Each member has **at most one** active check-in at any time.
- A new check-in **replaces** the previous one entirely (no history is preserved at the business level).
- The signal carries:
  - Geographic position.
  - Optional human-readable label.
  - A server-controlled "checked-in at" timestamp that drives freshness.

### 2. Freshness Window

- A check-in is considered **active** for a bounded period after it is published.
- After the freshness window elapses, the check-in is **no longer surfaced** in discovery, even if the underlying record still exists.
- The freshness window is a product-level setting, not user-controlled.

### 3. Place Labeling

- A user may attach a human-readable label to their check-in (e.g. "Gdańsk Marina").
- If no label is provided, the platform attempts to derive one automatically from the coordinates using the configured geocoder.
- Place labels relevant to sailing context (marina, harbor, port, point of interest, establishment) are preferred when multiple options exist.
- If automatic labeling is unavailable or fails, the check-in is still saved without a label.

### 4. Proximity Discovery

- Discovery is anchored at a user-supplied coordinate (typically the device's current position or a chosen map center).
- Results are limited to active check-ins within a configurable radius bounded by product policy.
- Results are presented ordered by distance ascending.

## Core Business Rules

### Presence Lifecycle Rules

- Publishing a check-in always replaces the caller's previous one; previous coordinates and label are discarded.
- The "checked-in at" timestamp is owned by the platform and cannot be set by the user.
- Once outside the freshness window, a check-in stops appearing to discoverers; the user must publish a new one to be visible again.
- There is no end-user action to delete a check-in in the current phase. Privacy is maintained by the freshness window and by re-publishing only when the user wants to be visible.

### Visibility Rules

- Discovery is restricted to authenticated members.
- Any authenticated member can see any other member's active check-in within the search radius.
- The caller's own check-in is **not excluded** from results when it falls within the radius. The client may choose to hide it visually if desired.
- Non-authenticated visitors cannot access presence information.

### Place Labeling Rules

- A non-empty user-provided label is always preferred over geocoder output.
- If the user clears or omits the label, the platform may replace it with a geocoded label, prioritizing sailing-relevant place types.
- Place labels are localized to the requesting member's preferred language whenever the geocoder supports it.
- A check-in with only coordinates (no label) is still valid and discoverable.

### Search Rules

- A discovery query must specify both a center point and a search radius.
- The search radius is constrained to a product-defined minimum and maximum to prevent both meaningless and global queries.
- Discovery results are paginated and sorted by distance ascending.

### Trust and Safety Rules

- The freshness window must be short enough to avoid becoming a passive tracking surface.
- The product never exposes check-in history to other members.
- Users must understand that publishing a check-in is a public-to-community action.

## Cross-Domain Business Impact

- **Friends:** an active check-in offers an ad-hoc reason to message a known friend who happens to be nearby.
- **Messages:** discovery may lead directly into a private conversation with another nearby sailor.
- **Cruises:** an organizer at the marina can spot nearby sailors who might fill an open spot.
- **Spots Directory:** check-ins are typically anchored at sailing places (marinas, harbors), reinforcing the value of a curated spots dataset.
- **Location & Regions:** check-ins are a real-time complement to the broader location and region context of the platform.

## End-to-End User Journeys

### Journey A: Announce Arrival at a Marina

1. Sailor docks in a marina and opens SkipperClub.
2. Sailor publishes a check-in using their current coordinates.
3. The platform proposes a localized marina label automatically.
4. Sailor confirms (or replaces) the label and the check-in becomes active.
5. Other members nearby start to see the sailor in their discovery view.

### Journey B: Discover Other Sailors Nearby

1. Member opens the discovery view at their current map position.
2. Member adjusts the search radius to a comfortable range.
3. Member sees a list of active nearby sailors ordered by distance.
4. Member optionally taps through to a profile or starts a conversation.

### Journey C: Refresh Presence on the Move

1. Sailor moves from one marina to another during a multi-day trip.
2. Sailor publishes a new check-in at the new location.
3. The previous location is replaced; only the latest position is visible to others.

### Journey D: Presence Naturally Expires

1. Sailor checks in at a marina and goes offline for a long period.
2. After the freshness window elapses, the check-in stops appearing in discovery.
3. To become visible again, the sailor publishes a new check-in.

## Functional Requirements

### FR-001 Latest Presence Publication

The product enables a member to publish their current geographic presence as a single, latest signal that replaces any previous presence.

### FR-002 Server-Owned Freshness Timestamp

The platform owns the timestamp that determines when a presence signal was published, ensuring freshness rules cannot be manipulated by clients.

### FR-003 Optional User-Provided Label

The product supports an optional, user-provided human-readable label associated with the presence signal.

### FR-004 Assisted Place Labeling

When the user does not provide a label, the platform attempts to derive a localized, sailing-relevant label from coordinates.

### FR-005 Localized Place Context

Derived place labels are presented in the member's preferred language whenever supported by the geocoder.

### FR-006 Bounded Proximity Discovery

The platform supports proximity-based discovery anchored at a user-chosen point with a bounded radius.

### FR-007 Distance-Ordered Results

Discovery results are ordered by distance ascending so that closest sailors are easiest to identify.

### FR-008 Time-Bounded Visibility

Presence signals are surfaced in discovery only while within the freshness window; expired signals do not appear.

### FR-009 Authenticated Audience

Only authenticated members can publish or discover check-ins.

### FR-010 Self-Inclusion Visibility

The caller's own check-in is not excluded from discovery when it falls within the radius, allowing clients to choose how to render it.

### FR-011 Resilience to Geocoder Unavailability

A check-in remains valid and persistable even when assisted labeling is unavailable.

## User Stories

### US-066: Publishing My Current Location

As an active sailor, I want to publish my current location with a single action so that nearby community members can see I am around.

Acceptance criteria:

1. I can publish a check-in with only my coordinates and have a place label suggested for me.
2. I can override the suggested label with a custom name that better reflects where I am.
3. Publishing a new check-in always replaces my previous one.
4. The "checked-in at" time is set by the platform, not by me.
5. After publishing I appear in proximity-based discovery for other members.

### US-067: Discovering Nearby Sailors

As a community member, I want to find other sailors who are currently around a chosen point so that I can connect with them in real life or via chat.

Acceptance criteria:

1. I can specify a center coordinate and a search radius within product-defined bounds.
2. I receive a list of active nearby sailors ordered by distance ascending.
3. Each result includes the distance to the search center.
4. Only sailors with a presence inside the freshness window are included.
5. Results are paginated for clarity and performance.

### US-068: Localized Place Labels

As a community member, I want place labels on check-ins to be presented in my preferred language so that I can recognize them quickly.

Acceptance criteria:

1. Auto-generated labels are localized to my language preference when the geocoder supports it.
2. Sailing-relevant place types (marina, harbor, port, point of interest) are preferred over generic addresses.
3. If localization is unavailable, the label still renders in a useful, recognizable form.

### US-069: Time-Bounded Presence

As a privacy-conscious sailor, I want my check-in to stop being visible after a reasonable period so that my location is not exposed indefinitely.

Acceptance criteria:

1. After the freshness window elapses, my check-in no longer appears in nearby discovery.
2. To be visible again, I must publish a new check-in.
3. The freshness window is consistent and predictable for all users.

### US-070: Presence Without Geocoding

As a sailor in an area with limited geocoding coverage, I want my check-in to still be saved if automatic labeling fails so that nearby members can still see me.

Acceptance criteria:

1. My check-in is saved even when the geocoder is unavailable or returns no result.
2. My presence is discoverable by coordinates with or without a label.
3. I can always provide my own label as a fallback.

## Success Metrics

- Number of unique members publishing at least one check-in per week.
- Median number of nearby discovery queries per active session.
- Conversion rate from "discovery result" to "profile view" or "private message".
- Share of check-ins that successfully receive a localized label automatically.
- Median time between consecutive check-ins per active sailor (engagement signal).
- User-reported satisfaction with privacy and freshness behavior.

## Risks and Mitigations

### Risk: Stale Presence Misleads Other Sailors

Users may treat an old check-in as if it were live.

Mitigation:

- Strict freshness window so stale signals stop appearing in discovery.
- Clear product communication that check-ins represent recent, not necessarily current, presence.

### Risk: Privacy Concerns Around Location Sharing

Users may worry about exposing their location to the entire authenticated community.

Mitigation:

- Single-latest-only model (no history).
- Automatic expiration via the freshness window.
- Authenticated-only audience and explicit user-initiated publication.

### Risk: Geocoder Costs or Rate Limits

Heavy reverse geocoding usage may impact platform cost or availability.

Mitigation:

- Geocoding skipped when a user provides their own label.
- Graceful fallback to coordinate-only persistence when geocoder is unavailable.

### Risk: Misuse for Tracking

Repeated discovery queries against a known coordinate could be used to monitor a friend's movements.

Mitigation:

- Freshness window keeps the visibility horizon short.
- No history exposed.
- Future iterations may introduce friend-only or opt-out audiences (see Open Product Decisions).

## Open Product Decisions

1. **Audience scoping**  
   Should presence visibility evolve from "all authenticated members" toward friend-only or opt-in audiences for higher privacy comfort?

2. **Manual presence removal**  
   Should users be able to explicitly clear an active check-in before the freshness window elapses?

3. **Freshness window duration policy**  
   Confirm the canonical freshness window value and whether it should differ per region, season, or device capability.

4. **Search radius bounds**  
   Confirm minimum and maximum allowed search radius from a product trust/usability perspective.

5. **Cross-feature surfacing**  
   Define whether check-ins should also be surfaced in adjacent contexts (cruise pages, friend profiles, sailing brief) and under what visibility rules.

6. **Notifications policy**  
   Decide whether nearby check-ins should trigger optional, opt-in notifications (e.g. friend nearby).

## Related

- `PRD-001-users.md` for identity and authenticated audience assumptions.
- `PRD-006-friends.md` for trust context that may inform future audience scoping.
- `PRD-009-location.md` for the broader location, region, and place context model.
- `PRD-013-spots.md` for the curated sailing places that frequently anchor check-ins.
