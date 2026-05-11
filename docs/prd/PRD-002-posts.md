# PRD-002: Posts & Social Feed

## Purpose

Define business requirements for how users create, discover, evaluate, and govern sailing posts in SkipperClub.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Help sailors share practical, regional knowledge and trip experiences.
- Keep feed content relevant by separating evergreen and time-sensitive information.
- Strengthen trust through transparent post lifecycle, community validation, and reporting.
- Support meaningful social engagement through comments, reactions, and bookmarking.
- Improve discovery with region-aware and location-aware feed navigation.

## Scope

### In Scope

- Post creation across all supported post types.
- Region and location context for posts.
- Post lifecycle management (published, archived, expired, resolved, deleted).
- Feed discovery and filtering by business-relevant dimensions.
- Social interactions on posts (comments, reactions, bookmarks).
- Community trust and safety controls (validity voting and reporting).
- Role-based behavior for standard users, post authors, moderators, and administrators.

### Out of Scope

- Technical implementation details and service architecture.
- API contracts, endpoint behavior, or transport-level error handling.
- UI mockups and visual design decisions.
- Internal tooling details for moderation operations.

## Personas and Roles

### Standard User

A community member who consumes content, engages socially, and contributes trust signals.

Core business capabilities:

- Browse and filter feed content by relevance.
- React, comment, bookmark, vote on validity (where allowed), and report content.
- Create posts and manage own post visibility through lifecycle actions.

### Post Author

A standard user acting as owner of specific posts.

Core business capabilities:

- Edit own posts while they are actively published.
- Archive or delete own posts.
- Resolve own time-sensitive posts when an issue is addressed.
- View own non-public post states (archived, expired, resolved).

### Moderator

A trusted operator responsible for reviewing safety reports.

Core business capabilities:

- Review submitted reports.
- Mark reports as reviewed or dismissed according to moderation policy.

### Administrator

A governance role with platform-wide oversight.

Core business capabilities:

- All standard user capabilities.
- Oversight of moderation outcomes and content safety governance.

## Post Domain Model (Business View)

The post domain is designed to support both experience sharing and operational sailing information.

### Post Type Groups

- **Evergreen types**: `photo`, `place`, `food`, `marina`, `tips`, `route`  
  Business intent: long-term discovery and inspiration.
- **Time-sensitive types**: `berth`, `weather`, `navigation_warning`, `help`  
  Business intent: short-lived operational information.

### Post Content Components

A post can include:

- Core classification: post type and associated region.
- Narrative context: description and extracted hashtags.
- Social context: tagged users.
- Place context: location name and coordinates (required for location-driven post types).
- Media context: photo/video assets (required for photo posts).
- Route context: route stops and optional route summary details (for route posts).

### Time Relevance

- Evergreen posts remain relevant without automatic expiration.
- Time-sensitive posts have built-in validity windows and can become outdated quickly.
- Community mechanisms can resolve time-sensitive information early when it is no longer valid.

## Post Lifecycle and Visibility Rules

Posts follow a controlled lifecycle:

- `published`: visible to everyone and open to normal engagement.
- `archived`: hidden from public feed, visible only to the author.
- `expired`: automatically time-closed for time-sensitive content, visible only to the author.
- `resolved`: manually or community-resolved time-sensitive issue, visible only to the author.
- `deleted`: inaccessible to everyone.

```mermaid
flowchart TD
  published[Published] --> archived[Archived]
  published --> resolved[Resolved]
  published --> expired[Expired]
  published --> deleted[Deleted]
  archived --> deleted
  resolved --> deleted
  expired --> deleted
```

### Lifecycle Business Policies

- All newly created posts start as `published`.
- Only `published` posts are editable.
- Authors can archive any of their published posts.
- Authors can resolve their own time-sensitive posts.
- Time-sensitive posts auto-expire after their configured duration.
- Authors can delete their own posts from any non-deleted state.

### Effective Expiration

A post can be operationally treated as expired even while still marked as published when its validity window has already passed.

Business implications:

- Non-authors no longer see such content in normal access paths.
- Authors can still view their content history.
- Social and trust interactions are blocked once effective expiration is reached.

## Regional Relevance and Location Discovery

### Region Association

- Every post must be linked to a sailing region.
- Region-based discovery follows region hierarchy, so selecting a broader region includes its sub-regions.

### Cross-Region Discovery

- Evergreen post types can be surfaced across regions to support broad inspiration and discovery.
- Time-sensitive post types remain region-scoped to preserve local relevance and safety.

### Location Context and Geocoder-Assisted Input

Location input supports practical post creation and discovery:

- Location-based post types require location name and coordinates.
- `photo` and `tips` may include location context but do not require it.
- Users can provide location through assisted search flows (autocomplete, search, reverse lookup) to improve accuracy.

Business value:

- Better relevance for local sailors.
- Stronger discoverability by place name and proximity.
- More consistent place metadata in community content.

## Social Interactions

### Comments

- Users can discuss published, currently valid posts.
- Comment author and post author can remove a comment.

### Reactions

- Users can express sentiment with a curated set of 20 reactions (standard and sailing-themed).
- Users can apply multiple different reaction types to the same post.
- Feed experience highlights reaction totals by type and the current user's own reactions.

### Bookmarks

- Users can privately save posts for later.
- Bookmark lists include only currently accessible posts.

### Interaction Guardrails

Comments, reactions, bookmarks, reports, and validity voting are only available for published posts that are still currently valid.

## Trust, Safety, and Content Governance

### Validity Voting

Validity voting keeps time-sensitive information trustworthy.

- Applicable only to `berth`, `weather`, and `navigation_warning`.
- Vote options communicate whether information remains valid or has become invalid.
- One user can submit one immutable vote per post.
- Repeated identical input is treated as no additional change.
- Reaching the invalid-vote threshold (3 or more) automatically resolves the post.

### Reporting

Reporting enables community-led safety enforcement.

- Users can report harmful or policy-violating posts using standardized reasons:
  `spam`, `scam`, `offensive`, `misinformation`, `danger`, `other`.
- Multiple reports can be submitted on the same post and are treated as separate moderation signals.
- Self-reporting is allowed.
- Reports progress through moderation states: `pending`, `reviewed`, `dismissed`.

### Governance Intent

- Combine proactive community signals (validity voting) with reactive moderation (reporting).
- Preserve user trust through predictable, auditable content state changes.

## Business Rules and Constraints

- Post type is fixed after creation.
- Description supports concise-to-detailed narrative text (business range: 1-2200 characters where required).
- Comment length supports short discussion format (business range: 1-500 characters).
- Report details support concise context (business maximum: 500 characters).
- A post can tag up to 20 users.
- Photo posts require media; non-photo post types rely on structured textual/location context.
- Route posts require at least one stop and support route metadata (duration and length).
- Time-sensitive expiration windows:
  - `berth`: 6 hours
  - `weather`: 7 days
  - `navigation_warning`: 7 days
  - `help`: 72 hours

## User Stories

### US-013: Creating Posts

As a user I want to share sailing-related content to contribute to the community and help other sailors.

Acceptance criteria:

1. I can choose from 10 post types based on content purpose: photo, place, food, marina, tips, route, berth, weather, navigation warning, or help request.
2. I can add relevant post content depending on post type (media, description, and location context).
3. I can tag up to 20 other users in my post.
4. I must associate my post with a sailing region.
5. Time-sensitive posts automatically receive a finite validity window.
6. Route posts support route-specific planning context through stops and optional trip metadata.

### US-020: Browsing the Post Feed

As a user I want to browse posts to discover content relevant to my sailing interests.

Acceptance criteria:

1. I can view a feed of posts with pagination.
2. I can filter by post type, region, status, author, location name, hashtags, and date range.
3. I can discover posts near a selected location using proximity context.
4. Evergreen posts can be discovered across broader regions.
5. Time-sensitive posts remain region-specific.
6. I can sort feed results by recency and relevance context.
7. Hashtag filtering is case-insensitive.

### US-021: Post Comments

As a user I want to add comments to posts to participate in the discussion.

Acceptance criteria:

1. I can add a comment and see it under the post.
2. The comment author can edit their own comment.
3. The comment author or the post author can delete the comment.
4. Commenting is available only on currently valid published posts.

### US-022: Post Reactions

As a user I want to react to posts with emojis to express my feelings about the content.

Acceptance criteria:

1. I can react to published posts that are currently valid.
2. 20 reaction types are available: 10 standard reactions and 10 sailing-themed reactions.
3. I can add multiple different reactions to the same post.
4. I can remove reactions I added.
5. Reaction totals by type are visible on each post.
6. I can see which reactions I personally added.

### US-050: Post Lifecycle Management

As a post author I want to manage my posts throughout their lifecycle.

Acceptance criteria:

1. I can archive my published posts to hide them from public feed while keeping them visible to me.
2. I can manually resolve my time-sensitive posts when the issue is addressed.
3. Time-sensitive posts automatically expire after their duration.
4. Non-public statuses (archived, expired, resolved) are visible only to the author.
5. I can delete my posts, making them inaccessible to everyone.
6. Status changes follow controlled lifecycle transitions.

### US-051: Bookmarking Posts

As a user I want to save posts for later reference.

Acceptance criteria:

1. I can bookmark published posts that are currently valid.
2. I can view all my bookmarked posts in a dedicated private list.
3. I can remove bookmarks when no longer needed.
4. Bookmarks are visible only to me.
5. Inaccessible posts are no longer shown in bookmark results.

### US-052: Community Validity Voting

As a user I want to help maintain the quality of time-sensitive information by voting on post validity.

Acceptance criteria:

1. I can vote on berth, weather, and navigation warning posts as valid or invalid.
2. Posts with 3 or more invalid votes are automatically resolved.
3. Each user can cast one immutable vote per post.
4. Repeating the same vote does not create additional impact.
5. Voting helps the community keep time-sensitive content accurate.

### US-053: Reporting Posts

As a user I want to report inappropriate or harmful content to maintain community standards.

Acceptance criteria:

1. I can report published, currently valid posts for spam, scam, offensive content, misinformation, danger, or other violations.
2. I can submit multiple reports on the same post, and each report is treated as a separate moderation signal.
3. Reports enter a moderation workflow with clear status progression.
4. I can see confirmation that my report was submitted.
5. Reporting helps keep the platform safe and trustworthy.

## Business Assumptions and Open Product Decisions

The following areas require explicit product decisions to keep business documentation fully consistent:

1. **Region code canonical policy**  
   Confirm one canonical region code convention for all product documents (sea-level, country-level, and sub-region notation).

2. **Geocoder dependency fallback**  
   Define expected user experience when location assistance is temporarily unavailable for post types that require location.

3. **Region-location consistency policy**  
   Define whether selected location must always be geographically consistent with the selected region, and how mismatches are handled.

4. **Location localization persistence**  
   Confirm how localized place names should be persisted when users create posts in different languages.

5. **Moderation SLA and escalation**  
   Define expected review timelines and escalation rules for high-risk reports.

6. **Moderator vs administrator boundaries**  
   Confirm where moderation responsibility ends and administrator intervention begins.
