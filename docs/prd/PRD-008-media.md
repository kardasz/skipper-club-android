# PRD-008: Media

## Product Purpose

Media is a core part of the SkipperClub experience. It enables users to:

- build trust and identity through profile photos,
- tell better stories in social posts,
- present cruises in a way that increases discovery and participation interest.

The product goal is to make visual content easy to add, safe to use, and consistently high quality across profile, post, and cruise experiences.

## Business Value

The media capability should drive measurable outcomes:

- higher content engagement (more views, reactions, and comments on media-rich posts),
- stronger cruise conversion (more profile views to join intent for cruises with visual context),
- better user trust (clear profile identity and credible trip presentation),
- improved retention (users return to browse and publish visual content).

## Personas and Jobs To Be Done

### Persona A: Social Contributor

A user who regularly shares sailing moments and local knowledge with the community.

**Job to be done:** "Help me quickly publish compelling visual posts so my experience is easier to understand and more engaging."

### Persona B: Cruise Organizer

A user publishing cruise offers and looking for participants.

**Job to be done:** "Help me showcase my cruise visually so people can assess credibility and decide faster."

### Persona C: Community Member

A user focused on profile quality and trusted interactions.

**Job to be done:** "Help me maintain a clear profile image so other users can recognize and trust me."

## Scope

This PRD covers business requirements for media used in:

- user profile avatar,
- social posts,
- cruise presentation and updates.

It includes content constraints, ownership and visibility rules, user-facing quality states, and product success metrics.

## Key User Journeys

### Journey 1: Set or update profile avatar

1. User selects a profile image.
2. System checks that the image meets platform constraints.
3. User sees the new avatar in profile and community contexts.

**Outcome:** stronger identity and recognizability across the product.

### Journey 2: Publish a media-rich post

1. User uploads one or more visuals.
2. System validates quality and safety constraints.
3. User attaches approved media to a post and publishes.

**Outcome:** more expressive posts and higher community engagement.

### Journey 3: Present a cruise with visuals

1. Organizer uploads visuals that explain vessel, route, and atmosphere.
2. Organizer attaches media to cruise details.
3. During updates, organizer can replace or clear the cruise media set.

**Outcome:** improved cruise transparency and stronger participation intent.

## Functional Requirements (Business Level)

### FR-01 Media Support

- The platform supports image and video uploads for user-generated content.
- Supported content types and size limits are defined to balance quality, performance, and storage cost.
- The platform supports both quick upload and resilient upload experiences, depending on user context and device constraints.

### FR-02 Quality and Validation Lifecycle

- Every uploaded media item moves through clear quality states before it becomes usable.
- Only validated media is eligible for user-facing placement in profile, posts, and cruises.
- When validation fails, users receive understandable guidance and can retry.

### FR-03 Post Integration Rules

- Users can attach media to posts within defined quantity limits.
- Photo-focused posts require media to preserve content integrity.
- Other post categories may include media as optional enhancement.

### FR-04 Cruise Integration Rules

- Cruise organizers can attach a curated set of media to improve cruise discoverability.
- Cruise media follows the same quality and ownership constraints as the rest of the platform.
- Cruise updates can replace or clear previously attached media to keep content accurate.

### FR-05 Profile Integration Rules

- Users can maintain one active profile avatar for identity consistency.
- Avatar experience is image-focused and optimized for trust and recognizability.

### FR-06 Ownership, Access, and Visibility

- Media remains tied to the user who uploaded it.
- Media visibility follows the visibility of the parent context:
  - profile context,
  - post lifecycle and visibility,
  - cruise privacy model (public vs private).
- The product prevents misuse of media that a user is not allowed to use.

### FR-07 Content Metadata Use

- The platform may store media context (for example dimensions or capture context) to improve presentation quality and future discovery features.
- Metadata is treated as supporting context, not as mandatory publishing input for users.

## Business Rules and Constraints

### Content Limits

- Image formats: JPEG, PNG, HEIC.
- Video format: MP4.
- Maximum image size: 10 MB.
- Maximum video size: 50 MB.

### Attachment Limits

- Post attachments: minimum 1 and maximum 10 media items when media is required by post type.
- Cruise attachments: maximum 10 media items.

### Publishing Readiness

- Unvalidated media cannot be used in user-facing published contexts.
- Users must have a clear retry path when uploads or validation fail.

### Data Integrity Expectations

- Removing a parent entity removes its media association in that context.
- Cruise media association changes must remain predictable during updates.

## User Stories

### US-054: Media Upload

As a user, I want to upload photos and videos so I can share visual content with the community.

Acceptance criteria:

1. I can upload supported image and video formats within defined size limits.
2. I have a reliable upload experience regardless of device and connection quality.
3. I can use my approved uploads in profile, posts, and cruises where relevant.
4. If upload fails, I receive clear guidance and can retry without confusion.

### US-055: Media Validation

As a user, I want uploaded media to be validated so platform content stays safe and high quality.

Acceptance criteria:

1. The platform validates media before public use.
2. I can understand whether my media is ready to use.
3. If validation fails, I get actionable feedback.
4. After successful validation, media is immediately available for allowed contexts.

### US-056: Post Storytelling with Media

As a user, I want to enrich my posts with visuals so others can better understand my sailing experience.

Acceptance criteria:

1. I can attach media to post categories that support visual storytelling.
2. For photo-oriented posts, media is required to keep content meaningful.
3. I can publish only with media that meets platform quality standards.

### US-057: Cruise Presentation with Media

As a cruise organizer, I want to add and manage cruise visuals so potential participants can better evaluate my offer.

Acceptance criteria:

1. I can attach a curated media set to a cruise.
2. I can replace or clear cruise media during updates.
3. Media visibility respects cruise privacy and participant access expectations.

### US-058: Trusted Identity via Avatar

As a user, I want to maintain a clear profile avatar so I am recognizable across social and cruise interactions.

Acceptance criteria:

1. I can set and update my active avatar.
2. My avatar appears consistently across key community touchpoints.
3. Unsupported or invalid avatar content is clearly rejected with guidance.

## Success Metrics (KPIs)

- Media upload completion rate.
- Validation success rate.
- Percentage of new posts published with media.
- Percentage of new cruises published with media.
- Engagement lift for media-rich posts versus text-only posts.
- Cruise join intent lift for cruises with media versus without media.
- User-facing media error rate and retry success rate.

## Risks and Mitigations

### Risk: Low-quality or invalid media reduces trust

- Mitigation: enforce validation before user-facing placement and provide clear retry guidance.

### Risk: Privacy mismatch between media and cruise visibility

- Mitigation: always apply parent visibility rules (especially private cruise contexts).

### Risk: Drop-off during upload flow

- Mitigation: keep upload journeys simple, resilient, and transparent about progress and outcomes.

### Risk: Content ambiguity when media is removed during updates

- Mitigation: make replacement and removal effects predictable and explicit in user experience.

## Out of Scope (Current Version)

- Advanced media editing (filters, cropping studio, video trimming).
- AI-based automatic moderation and classification.
- Rich media albums beyond current post/cruise context.
- Creator monetization features tied directly to media.

## Dependencies

- Authentication and identity management.
- Storage and delivery infrastructure for reliable media availability.
- Post and cruise domain workflows that consume validated media.

## Release Readiness Criteria

This PRD is considered satisfied when:

1. Users can reliably add media in profile, posts, and cruises within product constraints.
2. Quality validation protects user-facing experiences without blocking legitimate content.
3. Ownership and visibility rules are consistent with trust and privacy expectations.
4. KPI tracking is available to measure adoption, quality, and business impact.
