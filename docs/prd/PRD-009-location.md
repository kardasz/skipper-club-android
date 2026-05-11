# PRD-009: Location & Regions

## Purpose

Define business requirements for how location context and sailing regions help users create content, discover relevant posts and cruises, and navigate the platform with confidence.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Increase discovery relevance by combining region hierarchy and place-level context.
- Reduce user effort in content creation through assisted location input.
- Improve trust in location data by supporting structured, consistent place context.
- Support multilingual usage so users can interact with location context in their preferred language.
- Enable mobile-first scenarios where location can be inferred from device coordinates.

## Scope

### In Scope

- Sailing region taxonomy and user-facing region selection patterns.
- Place discovery assistance for user-generated content (search, suggestions, place confirmation, coordinate-to-address support).
- Region and place context in post creation and feed discovery.
- Region and location context in cruise discovery.
- Localization of region and place context for multilingual users.
- Business rules for region hierarchy behavior and location consistency assumptions.
- **Current presence (check-in)**: a single latest “where I am now” signal per user for optional discovery by other members within a bounded area, with freshness rules so presence does not remain visible indefinitely without an active update.

### Out of Scope

- Technical implementation details, provider contracts, and API behavior.
- Transport-level validation and error semantics.
- UI wireframes and visual interaction design.
- Geospatial engine internals and cache architecture.

## Personas and Roles

### Standard User

A community member who browses posts and cruises and expects location context to improve relevance and trust.

Core business capabilities:

- Explore sailing regions by broad or specific context.
- Search and select places when creating location-aware content.
- Discover posts and cruises by region, place name, and proximity.

### Content Author

A standard user acting as an owner of posts with location context.

Core business capabilities:

- Assign a region to each post.
- Provide place and coordinate context when required by post type.
- Use assisted location input to reduce typing and improve place precision.

### Cruise Organizer

A user who publishes cruises and depends on location context for matching with participants.

Core business capabilities:

- Classify cruises by region.
- Describe route context using departure/arrival place information.
- Benefit from region-based and keyword-based discovery by prospective participants.

### Prospective Participant

A user evaluating cruises before joining.

Core business capabilities:

- Find cruises by region and route-related location context.
- Use location filters to identify matching trips.

### Administrator and Moderator

Governance roles responsible for product consistency and trust outcomes.

Core business responsibilities:

- Ensure location and region behavior remains predictable across modules.
- Oversee policy-level consistency for multilingual location presentation.

## Location Domain Model (Business View)

The location domain combines four complementary context layers:

### 1. Region Context

- Regions are structured hierarchically (broad sailing areas, countries, and sub-regions).
- Users can discover and select regions either by exploration (hierarchical view) or quick lookup (flat view).
- Region hierarchy supports semantic discovery: selecting a parent region includes its child regions for matching use cases.

### 2. Place Context

- Users can attach real-world place information to content.
- Place context includes user-readable location names and map position context.
- Assisted place selection improves consistency compared to free-text-only entry.

### 3. Proximity Context

- Users can discover content near a selected geographic point.
- Proximity-based discovery complements region filters when users need nearby results.
- Location-based discovery is particularly valuable for on-the-water and mobile scenarios.

### 4. Language Context

- Region and place information should be understandable in the user’s preferred language.
- Multilingual location context improves trust and adoption across international sailing communities.

## Core Business Rules

### Region Hierarchy and Discovery Rules

- Region structure must support both broad planning and local decision-making.
- Parent-level selection includes child-level content in discovery flows.
- Region discovery should surface popular destinations prominently for faster decisions.
- Region browsing must be available without requiring account authentication.

### Post Location Rules

- Every post is associated with a sailing region.
- Location-sensitive post categories require explicit place context.
- Some post categories can optionally include place context but are not blocked if place context is absent.
- Content relevance depends on both selected region and post type characteristics.

### Cross-Region Discovery Rules

- Evergreen content can be discoverable beyond the currently selected region to support inspiration and long-tail value.
- Time-sensitive content remains region-constrained to protect local relevance and safety.

### Cruise Location Rules

- Cruise discovery relies on consistent region assignment and route-related place context.
- Users should be able to combine region and keyword/location intent when searching for cruises.
- Location context should improve matching quality between organizers and prospective participants.

### Assisted Input Rules

- Users can refine location selection through incremental suggestions while typing.
- Users can confirm selected places before saving location context.
- Users can transform current coordinates into readable place context when creating content from mobile context.

### Location Trust and Consistency Rules

- The product should minimize mismatches between selected region and selected place context.
- Location context should remain understandable and comparable across languages.
- Product messaging should remain clear when location assistance is temporarily unavailable.

## End-to-End User Journeys

### Journey A: Add Accurate Location to a Post

1. Author chooses post type and region context.
2. Author searches or types a place and receives assisted suggestions.
3. Author confirms place context and map position.
4. Post is published with structured location context.
5. Other users discover the post by region, place name, or proximity.

### Journey B: Create a Post from Current Position

1. Author starts post creation on mobile.
2. Product uses current coordinates to suggest human-readable location context.
3. Author confirms or adjusts suggested place.
4. Post is published with location context that improves discoverability.

### Journey C: Discover Posts in and Around a Sailing Area

1. User selects a region of interest.
2. Product includes relevant child regions in discovery scope.
3. User optionally adds place name or proximity criteria.
4. User receives feed results aligned with both regional and local intent.

### Journey D: Discover Cruises by Region and Route Context

1. User opens cruise discovery with target area in mind.
2. User applies region and location-oriented filters.
3. User compares cruises with relevant route context and profile details.
4. User narrows to cruises that fit travel plans and participation preferences.

## Functional Requirements

### FR-001 Region Browsing Model

The platform supports both exploratory and quick-selection region browsing to serve novice and experienced sailors.

### FR-002 Region Localization

Region context is presented in localized form to support multilingual user journeys.

### FR-003 Location-Assisted Input

The platform supports assisted place selection to reduce entry effort and improve location precision in user content.

### FR-004 Coordinate-to-Place Support

The platform supports translating current geographic context into readable place context for mobile and real-time scenarios.

### FR-005 Region-Aware Content Discovery

Post and cruise discovery support region hierarchy semantics so broader selections include relevant sub-regions.

### FR-006 Proximity-Aware Discovery

The platform supports location-nearby discovery to help users find relevant content around a selected point.

### FR-007 Cross-Region Content Policy

The product differentiates evergreen and time-sensitive content in cross-region visibility to preserve relevance and safety.

### FR-008 Location Consistency Governance

The product enforces business-level consistency expectations between region context and place context.

## User Stories

### US-058: Sailing Region Selection

As a user, I want to browse and select sailing regions so I can focus on the right geographic context for posts and cruises.

Acceptance criteria:

1. I can navigate regions through both hierarchy and quick-selection experiences.
2. Region naming is shown in my preferred language context.
3. Popular destinations are easy to access in region selection.
4. Parent-region selection broadens discovery to relevant sub-regions.
5. Region exploration is available before sign-in.

### US-059: GPS-Based Region and Place Assistance

As a mobile user, I want my current geographic context to help prefill region and place information so I can create content faster.

Acceptance criteria:

1. The system can identify region context from my current coordinates.
2. When multiple regions apply, the most specific region is preferred for prefill.
3. I can review and adjust suggested region or place before publishing.
4. Suggested context can be reused in both post creation and discovery filters.

### US-060: Place Search for Content Creation

As a content author, I want to search for marinas, harbors, and sailing destinations so I can attach accurate place context to posts.

Acceptance criteria:

1. I can search by place name or address-like query.
2. Search results provide enough context to distinguish similar places.
3. Place results are localized for my language context.
4. I can select a result and use it directly in post creation.

### US-061: Real-Time Place Suggestions

As a user, I want location suggestions while typing so I can select the correct place quickly with minimal effort.

Acceptance criteria:

1. Suggestions update while I type.
2. Suggestions clearly separate primary place name from secondary context.
3. I can pick one suggestion and continue with confirmed place context.
4. The typing-to-selection flow feels fast enough for mobile and web usage.

### US-062: Coordinate-to-Address Conversion

As a user, I want to convert coordinates into human-readable addresses so location-based content remains understandable.

Acceptance criteria:

1. I can use coordinate context to receive readable place options.
2. I can choose from options with different levels of detail.
3. Returned place context is localized for my language preference.
4. I can apply selected result directly to content creation.

### US-063: Region and Proximity Feed Discovery

As a user, I want to combine region and nearby-place intent so I can find the most relevant posts around where I sail.

Acceptance criteria:

1. I can start from region context and optionally refine with proximity intent.
2. Discovery results respect hierarchy semantics for region scope.
3. Evergreen content can appear across broader contexts where policy allows.
4. Time-sensitive content remains locally relevant and region-scoped.

### US-065: Cruise Discovery by Location Intent

As a prospective participant, I want to find cruises by region and route context so I can shortlist trips aligned with my travel plans.

Acceptance criteria:

1. I can filter cruises by target region.
2. I can use location-oriented search intent to narrow results.
3. Region hierarchy improves matching for broad destination searches.
4. Cruise location context is clear enough to compare options without contacting organizers first.

## Success Metrics

- Share of created posts with complete, structured location context.
- Reduction in post creation drop-off when location context is required.
- Increase in discovery interactions using region and location filters.
- Engagement uplift on location-aware post discovery journeys.
- Cruise search-to-view conversion for region-filtered discovery sessions.
- User-reported confidence in location relevance of discovered content.

## Business Assumptions and Open Product Decisions

The following areas require explicit product decisions to keep business documentation fully consistent:

1. **Region-place consistency policy**  
   Define whether location/place context must always be geographically consistent with selected region context, and how mismatches are handled in user journeys.

2. **Location assistance fallback behavior**  
   Define expected user experience when assisted place discovery is temporarily unavailable, especially for content types requiring location context.

3. **Canonical region naming policy**  
   Confirm one canonical naming and coding policy for top-level, country-level, and sub-region references across all product-facing documentation.

4. **Localization persistence rules**  
   Define how place names selected in different languages should be persisted and displayed across multilingual user journeys.

5. **Popularity governance model**  
   Define how region popularity is governed from a product perspective to ensure fair representation of emerging destinations.

6. **Location privacy expectations**  
   Confirm product-level guidance for precision and visibility of user-provided location context in public content.
