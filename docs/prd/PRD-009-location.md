# PRD-009: Location & Areas

## Purpose

Define business requirements for how coordinate, place, viewport, and technical
area context help users create content, discover relevant posts and cruises,
and navigate the platform with confidence.

This document focuses on product behavior and business rules. It intentionally
avoids technical and API-level details.

## Business Objectives

- Increase discovery relevance through proximity, viewport, and place context.
- Reduce user effort in content creation through assisted location input.
- Improve trust in location data by supporting structured, consistent place
  context.
- Support multilingual usage so users can understand location context in their
  preferred language.
- Enable mobile-first scenarios where location can be inferred from device
  coordinates.

## Scope

### In Scope

- Coordinate, affected-area, and place context for posts, map discovery,
  cruises, spots, and check-ins.
- Place discovery assistance for user-generated content: search, suggestions,
  place confirmation, and coordinate-to-address support.
- Proximity and viewport-based post, map, and cruise discovery.
- Technical sailing brief areas used by the backend to choose the right brief
  from current user coordinates.
- Localization of user-readable place context for multilingual users.
- Business rules for location consistency assumptions.
- **Current presence (check-in)**: a single latest "where I am now" signal per
  user for optional discovery by other members within a bounded area, with
  freshness rules so presence does not remain visible indefinitely without an
  active update.

### Out of Scope

- A user-facing sailing region picker.
- Public region hierarchy browsing or region-scoped feed/cruise filters.
- Technical implementation details, provider contracts, and API behavior.
- Transport-level validation and error semantics.
- UI wireframes and visual interaction design.
- Geospatial engine internals and cache architecture.

## Personas and Roles

### Standard User

A community member who browses posts, map content, and cruises and expects
location context to improve relevance and trust.

Core business capabilities:

- Discover content through current position, viewport bounds, proximity, and
  place context.
- Discover posts and cruises by place name, route-related context, and nearby
  coordinates.

### Content Author

A standard user acting as an owner of posts with optional location context.

Core business capabilities:

- Add optional place and representative coordinate context to posts.
- Provide representative coordinates for alert posts.
- Use assisted location input to reduce typing and improve place precision.

### Cruise Organizer

A user who publishes cruises and depends on location context for matching with
participants.

Core business capabilities:

- Describe route context using departure and arrival place information.
- Benefit from location-based and keyword-based discovery by prospective
  participants.

### Prospective Participant

A user evaluating cruises before joining.

Core business capabilities:

- Find cruises by route-related location context and proximity.
- Use location filters to identify matching trips.

### Administrator and Moderator

Governance roles responsible for product consistency and trust outcomes.

Core business responsibilities:

- Ensure location behavior remains predictable across modules.
- Oversee policy-level consistency for multilingual location presentation.

## Location Domain Model (Business View)

The location domain combines four complementary context layers.

### 1. Coordinate and Area Context

- Posts, cruises, spots, and check-ins use WGS84 coordinates for discovery.
- Posts may include a representative point; alert posts may also include an
  affected area.
- Technical brief areas are backend geometry records used for sailing brief
  lookup, not a user-facing region picker.

### 2. Place Context

- Users can attach real-world place information to content.
- Place context includes user-readable location names and map position context.
- Assisted place selection improves consistency compared to free-text-only
  entry.

### 3. Proximity and Viewport Context

- Users can discover content near a selected geographic point.
- Map and feed discovery can use viewport bounds and radius filters.
- Location-based discovery is particularly valuable for on-the-water and mobile
  scenarios.

### 4. Language Context

- Place information should be understandable in the user's preferred language.
- Multilingual location context improves trust and adoption across
  international sailing communities.

## Core Business Rules

### Coordinate and Area Discovery Rules

- Feed and map discovery are driven by radius, viewport bounds, and affected
  area intersection.
- Map rendering uses representative points for markers and clustering.
- Area posts match a viewport when their affected area intersects it.

### Post Location Rules

- Regular posts may omit location.
- Alert posts require representative coordinates and may include affected-area
  geometry.
- Content relevance depends on current position, viewport bounds, content keys,
  and publication or validity state.

### Cruise Location Rules

- Cruise discovery relies on departure/arrival coordinates and route-related
  place context.
- Users should be able to combine keyword and location intent when searching
  for cruises.
- Location context should improve matching quality between organizers and
  prospective participants.

### Sailing Brief Area Rules

- The mobile product selects a sailing brief from the user's current
  coordinates.
- The backend resolves the most specific enabled technical brief area that
  contains those coordinates.
- Users do not browse or choose technical brief areas as a region picker.

### Assisted Input Rules

- Users can refine location selection through incremental suggestions while
  typing.
- Users can confirm selected places before saving location context.
- Users can transform current coordinates into readable place context when
  creating content from mobile context.

### Location Trust and Consistency Rules

- Location context should remain understandable and comparable across
  languages.
- Product messaging should remain clear when location assistance is temporarily
  unavailable.

## End-to-End User Journeys

### Journey A: Add Accurate Location to a Post

1. Author creates a post through the unified posting flow.
2. Author searches or types a place and receives assisted suggestions.
3. Author confirms place context and map position when location is relevant.
4. Post is published with structured location context.
5. Other users discover the post by place name, content keys, map viewport, or
   proximity.

### Journey B: Create a Post from Current Position

1. Author starts post creation on mobile.
2. Product uses current coordinates to suggest human-readable location context.
3. Author confirms or adjusts suggested place.
4. Post is published with location context that improves discoverability.

### Journey C: Discover Posts in and Around the Current View

1. User opens the feed or map around their current position or a selected
   viewport.
2. Product returns relevant posts inside the viewport or near the selected
   point.
3. User optionally filters by content keys such as media, route, or alert.
4. User receives feed and map results aligned with local intent and content
   state.

### Journey D: Discover Cruises by Route Context

1. User opens cruise discovery with a target area or route in mind.
2. User applies location-oriented or keyword filters.
3. User compares cruises with relevant departure, arrival, and route context.
4. User narrows to cruises that fit travel plans and participation preferences.

### Journey E: Read a Sailing Brief from Current Coordinates

1. User opens the sailing brief screen on mobile.
2. Product uses current coordinates to select the matching technical brief
   area.
3. User receives the most specific enabled brief available for that location.
4. Product communicates clearly when no active brief covers the current
   coordinates.

## Functional Requirements

### FR-001 Structured Location Context

The platform supports representative coordinates, affected-area geometry for
alert posts, and user-readable place names where relevant.

### FR-002 Place Localization

Place context is presented in localized form where provider data and product
state allow it.

### FR-003 Location-Assisted Input

The platform supports assisted place selection to reduce entry effort and
improve location precision in user content.

### FR-004 Coordinate-to-Place Support

The platform supports translating current geographic context into readable
place context for mobile and real-time scenarios.

### FR-005 Viewport and Proximity Discovery

Post, map, cruise, spot, and check-in discovery support viewport and proximity
semantics instead of public region hierarchy semantics.

### FR-006 Sailing Brief Area Resolution

Sailing brief selection resolves from current coordinates to backend-managed
technical brief areas.

### FR-007 Alert Area Policy

Alert posts with affected areas remain locally relevant through area
intersection and representative point behavior.

### FR-008 Location Consistency Governance

The product enforces business-level consistency expectations between place
context, coordinates, affected areas, and user-visible discovery behavior.

## User Stories

### US-058: Viewport-Based Content Discovery

As a user, I want the feed and map to show content around my current view so I
can focus on what is relevant where I am sailing.

Acceptance criteria:

1. I can discover posts using my current position or a selected viewport.
2. I can refine post discovery by content keys such as media, route, or alert.
3. Alert areas appear when they intersect the current map view.
4. Location exploration is available before sign-in where the product permits
   public discovery.

### US-059: GPS-Based Place Assistance

As a mobile user, I want my current geographic context to help prefill place
information so I can create content faster.

Acceptance criteria:

1. The system can use my current coordinates to suggest readable place context.
2. I can review and adjust suggested place context before publishing.
3. Suggested place context can be reused in post creation and discovery
   filters.
4. Technical brief area resolution happens behind the scenes when I request a
   sailing brief.

### US-060: Place Search for Content Creation

As a content author, I want to search for marinas, harbors, and sailing
destinations so I can attach accurate place context to posts.

Acceptance criteria:

1. I can search by place name or address-like query.
2. Search results provide enough context to distinguish similar places.
3. Place results are localized for my language context where available.
4. I can select a result and use it directly in post creation.

### US-061: Real-Time Place Suggestions

As a user, I want location suggestions while typing so I can select the correct
place quickly with minimal effort.

Acceptance criteria:

1. Suggestions update while I type.
2. Suggestions clearly separate primary place name from secondary context.
3. I can pick one suggestion and continue with confirmed place context.
4. The typing-to-selection flow feels fast enough for mobile and web usage.

### US-062: Coordinate-to-Address Conversion

As a user, I want to convert coordinates into human-readable addresses so
location-based content remains understandable.

Acceptance criteria:

1. I can use coordinate context to receive readable place options.
2. I can choose from options with different levels of detail.
3. Returned place context is localized for my language preference where
   available.
4. I can apply selected result directly to content creation.

### US-063: Proximity Feed Discovery

As a user, I want nearby-place intent to help me find the most relevant posts
around where I sail.

Acceptance criteria:

1. I can start from current coordinates, a selected point, or a viewport.
2. Discovery results respect radius and viewport semantics.
3. Content key filters refine results without requiring post type categories.
4. Time-sensitive content remains locally relevant and state-aware.

### US-065: Cruise Discovery by Location Intent

As a prospective participant, I want to find cruises by route context so I can
shortlist trips aligned with my travel plans.

Acceptance criteria:

1. I can use location-oriented search intent to narrow results.
2. Departure and arrival place context help me compare options.
3. Route-related coordinates improve matching for broad destination searches.
4. Cruise location context is clear enough to compare options without
   contacting organizers first.

## Success Metrics

- Share of created posts with complete, structured location context.
- Reduction in post creation drop-off when alert location context is required.
- Increase in discovery interactions using proximity, viewport, and place
  filters.
- Engagement uplift on location-aware post discovery journeys.
- Cruise search-to-view conversion for location-filtered discovery sessions.
- User-reported confidence in location relevance of discovered content.

## Business Assumptions and Open Product Decisions

The following areas require explicit product decisions to keep business
documentation fully consistent:

1. **Location assistance fallback behavior**  
   Define expected user experience when assisted place discovery is temporarily
   unavailable, especially for content requiring location context.

2. **Localization persistence rules**  
   Define how place names selected in different languages should be persisted
   and displayed across multilingual user journeys.

3. **Location privacy expectations**  
   Confirm product-level guidance for precision and visibility of user-provided
   location context in public content.
