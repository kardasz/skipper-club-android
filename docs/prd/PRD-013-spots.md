# PRD-013: Spots Directory

## Purpose

Define business requirements for the **Spots Directory**, a community-curated registry of sailing places (marinas, harbors, anchorages, ports) that serves as the canonical, trusted reference dataset for location-aware features in SkipperClub.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Provide sailors with a high-quality, trustworthy directory of sailing places enriched with practical contact information.
- Mobilize the community to grow and maintain the directory through user-submitted change proposals.
- Maintain editorial control through administrator approval to keep data quality, consistency, and accuracy high.
- Reduce duplicates and conflicting entries via clear deduplication rules.
- Improve discoverability of cruises, posts, and check-ins by anchoring them to consistent, well-known places.

## Scope

### In Scope

- Maintaining a canonical set of sailing spots with name, geographic coordinates, phone contacts, and radio channels.
- Geographic search of spots within a given radius of a point.
- Community-driven proposal workflow: any authenticated member can suggest a new spot, an edit, or a deletion of any sub-record (contact, channel, or the spot itself).
- Administrator approval workflow: review, approve, reject, or batch-process proposals.
- Direct administrator CRUD over the canonical dataset (independent of the proposal workflow).
- User self-service over their own proposals: list, cancel pending requests.
- Soft deletion of spots and sub-records to preserve referential history.
- Deduplication rules that prevent overlapping or contradictory entries.

### Out of Scope

- Public, unauthenticated access to the spots directory.
- Verified or "official" status for marinas curated by the marina owners themselves.
- Booking, payment, or reservation features against spots.
- Real-time availability information (berth occupancy, weather conditions).
- Photos, reviews, opening hours, or pricing on spots.
- Automated scraping or imports from third-party marina databases.
- Multilingual spot names (current scope assumes a single canonical name per spot).

## Personas and Roles

### Standard User (Contributor)

A community member who uses the directory and may propose changes to keep it accurate and useful.

Core business capabilities:

- Search and browse spots, including by geographic proximity.
- View spot details including contacts and radio channels.
- Propose creation of new spots and additions/updates/deletions of contacts and channels.
- Propose deletion of an existing spot, with a required reason.
- View and cancel their own pending change requests.

### Administrator (Editor)

A platform-trusted role responsible for the data quality of the directory.

Core business responsibilities:

- Directly create, update, and soft-delete spots and their sub-records when needed.
- Review and approve, reject, or batch-process community-submitted change requests.
- Provide rejection reasons when declining a proposal.
- Ensure deduplication, naming, and consistency rules are upheld at the moment of approval.

### Cruise Organizer / Participant / Content Author

Adjacent roles that **consume** spot data when planning cruises, creating posts, or publishing check-ins, and benefit from a consistent, well-curated dataset.

## Spots Domain Model (Business View)

### 1. The Spot

A spot is a sailing-relevant place with:

- A canonical name.
- A geographic position.
- Optional structured **phone contacts** (e.g. marina office, harbor master, fuel dock).
- Optional structured **radio channels** (VHF channel or MHz frequency), one of which may be marked as **primary**.
- A soft-deletion state so historical references remain intact even after removal.

### 2. Phone Contact

A phone contact carries:

- A descriptive label (what the number is for).
- A phone number, optionally with an extension.
- A normalized identity used to detect duplicate contacts within the same spot.

### 3. Radio Channel

A radio channel carries:

- A descriptive name.
- A channel kind (VHF channel or analog frequency in MHz).
- A normalized identity used to detect duplicate channels within the same spot.
- A flag identifying it as the primary channel (at most one per spot).

### 4. Change Request

A change request is a community member's proposed modification, captured with:

- A type (which canonical operation is being proposed: create/update/delete a spot, contact, or channel).
- A payload describing the proposed change.
- A status lifecycle: pending → approved | rejected | cancelled.
- Authorship information (who proposed it).
- Optional comment/reason (required for delete-type requests).
- Optional rejection reason set by an administrator.

### 5. Normalization and Deduplication

The product enforces normalization and deduplication so the directory remains consistent over time:

- Spot names are normalized (trim, collapse whitespace, lowercase) for duplicate detection.
- Two active spots with the same normalized name within ~100 m are treated as duplicates and rejected.
- Within a spot, two contacts with the same normalized phone identity (number + optional extension) are treated as duplicates.
- Within a spot, two channels with the same normalized channel identity are treated as duplicates.
- Within a spot, only one channel can be marked as primary.

## Core Business Rules

### Authoring and Approval Rules

- Any authenticated member can propose any change (create, update, delete) for spots, contacts, or channels.
- Administrators can apply changes directly without going through a change request.
- Direct administrator changes and approved community changes share the same canonical data and validation rules.
- Approval re-validates the proposed change against the **current** state of the data, so a stale proposal cannot bypass deduplication or consistency checks.
- Rejected proposals never modify canonical data and must include a rejection reason for transparency.
- Delete-type proposals must include a comment explaining the reason.

### Ownership Rules

- A member can only cancel their **own** pending change requests.
- Attempts to cancel another member's request are rejected at the per-item level.
- Administrators can act on any pending change request.

### Lifecycle Rules

- A pending change request can transition to: approved, rejected, or cancelled.
- An approved change request applies its payload transactionally; on failure, no partial state is created and the failure reason is communicated.
- An approved or rejected change request is final and cannot be reopened.
- A cancelled change request is final and cannot be re-approved (a new request must be created).

### Data Integrity Rules

- Spot deletion is **soft**: the spot and its sub-records are hidden but historical references (e.g. cruises, posts, check-ins anchored at the spot) remain intact.
- Active deduplication rules apply to active spots/sub-records only; soft-deleted entries do not block new entries.
- Coordinate keying ensures two spots cannot be created at the exact same precise coordinates with the same normalized name.

### Search and Discovery Rules

- Geographic search requires all three of: latitude, longitude, and radius.
- Geographic results are ordered by distance ascending so the closest spot is most prominent.
- Without geographic search parameters, browsing returns spots in a stable, paginated order.

### Visibility Rules

- The directory is available to authenticated members.
- A user's own change requests are visible to the user, regardless of status.
- Administrators can see all change requests across all members.

## Cross-Domain Business Impact

- **Cruises:** organizers anchor cruises to known spots so participants can recognize departure/arrival points.
- **Posts:** location-aware posts can reference a known spot for trustworthy place context.
- **Check-Ins:** members frequently check in at marinas/harbors that exist in the spots directory.
- **Location & Areas:** spots complement the broader coordinate-based location model with on-the-ground place-level detail (see [PRD-009: Location & Areas](./PRD-009-location.md)).
- **Sailing Brief / Friends / Reviews:** higher quality place data improves clarity in any place-aware narrative.

## End-to-End User Journeys

### Journey A: Member Proposes a Missing Marina

1. Member discovers a marina that is not in the directory.
2. Member opens the proposal form, enters a name and coordinates, and optionally adds known phone and radio contacts.
3. Member submits the proposal; status is `pending`.
4. Administrator reviews the proposal and approves it.
5. The spot becomes part of the canonical directory; member can see the request marked as `approved`.

### Journey B: Member Proposes a Correction

1. Member notices an outdated phone number for a known marina.
2. Member submits a proposal to update the contact, including the corrected number.
3. Administrator reviews and approves.
4. The directory reflects the corrected number; the member's request shows as `approved`.

### Journey C: Member Proposes a Deletion with Reason

1. Member observes that a marina has permanently closed.
2. Member submits a deletion proposal with a reason explaining the closure.
3. Administrator reviews the proposal and approves.
4. The spot is soft-deleted; references in past cruises and posts remain intact but the spot stops appearing in active discovery.

### Journey D: Member Cancels Their Own Pending Proposal

1. Member submits a proposal but realizes the data was incorrect.
2. Member cancels the pending proposal.
3. The proposal is finalized as `cancelled` and never reaches an administrator decision.

### Journey E: Administrator Rejects with a Reason

1. Administrator reviews a proposal that conflicts with an existing spot or violates data quality rules.
2. Administrator rejects the proposal and provides a rejection reason.
3. The proposing member sees the request marked as `rejected` with the reason for transparency.

### Journey F: Administrator Direct Edit

1. Administrator needs to apply a known correction quickly.
2. Administrator updates the spot directly (no change request workflow needed).
3. The directory reflects the change immediately, subject to the same deduplication rules.

### Journey G: Geographic Search

1. Sailor opens the spots directory and searches near their current position.
2. The platform returns active spots within the chosen radius, ordered by distance.
3. Sailor selects a spot and reviews its contacts and radio channels.

## Functional Requirements

### FR-001 Canonical Spot Catalog

The platform maintains a canonical, deduplicated list of sailing spots with name, position, phone contacts, and radio channels.

### FR-002 Community Change Proposals

Any authenticated member can propose creation, update, or deletion of a spot or any of its sub-records.

### FR-003 Required Reasoning for Deletions

Deletion proposals require an explanatory comment so administrators can make informed decisions.

### FR-004 Administrator Approval Workflow

Administrators can review and approve, reject, or batch-process pending proposals, with the ability to record rejection reasons.

### FR-005 Re-Validation at Approval Time

When a proposal is approved, the platform re-validates it against the current canonical data to prevent stale or conflicting changes.

### FR-006 Direct Administrator CRUD

Administrators can create, update, and soft-delete spots and sub-records directly without going through the proposal workflow.

### FR-007 User Self-Service on Own Proposals

Members can list and cancel their own pending change requests.

### FR-008 Soft Deletion

Spot deletion is soft to preserve referential integrity for historical content.

### FR-009 Deduplication Enforcement

The platform prevents creation of duplicate active spots, contacts, and channels under business-defined normalization rules, and limits primary radio channels to one per spot.

### FR-010 Geographic Search

The platform supports radius-based geographic search of active spots, returning results ordered by distance ascending.

### FR-011 Authoring Transparency

Members can always see the status and outcome of their own proposals, including rejection reasons when provided.

## User Stories

### US-071: Browsing the Spots Directory

As a sailor, I want to browse the directory of sailing spots so that I can plan trips and reference reliable place information.

Acceptance criteria:

1. I can list spots in a stable, paginated way.
2. I can search spots by geographic radius around a chosen point.
3. Results include name, position, contacts, and radio channels.
4. Soft-deleted spots do not appear in active browsing.

### US-072: Geographic Search

As a sailor, I want to find spots near a chosen point so that I can quickly identify nearby marinas and harbors.

Acceptance criteria:

1. I can search by latitude, longitude, and radius.
2. Results are ordered by distance ascending.
3. The search returns only currently active spots.

### US-073: Proposing a New Spot

As a community member, I want to propose adding a missing marina to the directory so that the community can rely on more complete data.

Acceptance criteria:

1. I can submit a proposal with at minimum a name and coordinates.
2. I can optionally include phone contacts and radio channels.
3. I receive confirmation that my proposal is `pending`.
4. The proposal is rejected if it duplicates an existing active spot under the deduplication rules.

### US-074: Proposing a Correction

As a community member, I want to propose corrections to an existing spot so that bad or stale data can be fixed.

Acceptance criteria:

1. I can propose updates to the spot's name, position, contacts, or channels.
2. I can add new contacts or channels and update or remove existing ones via proposals.
3. My proposal stays `pending` until an administrator decides.

### US-075: Proposing a Deletion with Reason

As a community member, I want to propose deletion of a closed or invalid spot, with a reason, so that the directory reflects reality.

Acceptance criteria:

1. I can submit a deletion proposal with a required comment explaining the reason.
2. My proposal stays `pending` until an administrator decides.
3. If approved, the spot is soft-deleted and stops appearing in active browsing and search.

### US-076: Cancelling My Own Pending Proposal

As a community member, I want to cancel a pending proposal I submitted in error so that it does not reach administrators.

Acceptance criteria:

1. I can cancel any of my own pending proposals.
2. I cannot cancel proposals submitted by other members.
3. After cancellation, my proposal is finalized as `cancelled` and is not actionable.

### US-077: Administrator Approval with Re-Validation

As an administrator, I want approval to re-validate the proposed change so that conflicts introduced after submission are caught.

Acceptance criteria:

1. On approval, the platform re-checks deduplication and consistency rules against current data.
2. If the proposal can no longer be applied, the approval fails and the failure reason is communicated.
3. If the proposal applies cleanly, the canonical data is updated transactionally.

### US-078: Administrator Rejection with Reason

As an administrator, I want to reject a proposal with a reason so that the proposer understands the decision.

Acceptance criteria:

1. I can reject a pending proposal and record a rejection reason.
2. The proposer can read the rejection reason on their proposal status.
3. Rejection does not modify any canonical data.

### US-079: Administrator Direct Maintenance

As an administrator, I want to create, update, and soft-delete spots directly so that I can apply known fixes without a proposal cycle.

Acceptance criteria:

1. I can create a new spot and have it become available immediately, subject to deduplication rules.
2. I can update an existing spot's name, coordinates, contacts, and channels directly.
3. I can soft-delete a spot, after which it stops appearing in active discovery but historical references remain intact.

### US-080: Listing My Own Proposals

As a community member, I want to list my own proposals across all statuses so that I can track my contributions and outcomes.

Acceptance criteria:

1. I see all my proposals (pending, approved, rejected, cancelled).
2. I do not see other members' proposals.
3. Administrators can see all proposals across the community.

## Success Metrics

- Total number of spots in the canonical directory over time.
- Share of spots enriched with at least one phone contact and one radio channel.
- Number of community-submitted change requests per week.
- Approval rate of community-submitted proposals.
- Median time from proposal submission to administrator decision.
- Reduction in duplicate or conflicting spots flagged after deduplication rules are enforced.
- Coverage of geographic queries returning at least one nearby spot in target sailing areas.

## Risks and Mitigations

### Risk: Low-Quality or Spammy Proposals

Open community proposals may produce noisy or low-quality submissions.

Mitigation:

- Administrator approval gate before any change becomes canonical.
- Required reasoning for deletion proposals.
- Per-item rejection reasons to help proposers understand and improve.

### Risk: Stale Proposals Bypassing Deduplication

A proposal pending for a long time could conflict with newer canonical data at approval time.

Mitigation:

- Re-validation at approval time against the live state of the directory.
- Per-item failure reporting in batch approval flows.

### Risk: Broken References After Deletion

Hard-deleting a spot would orphan cruises, posts, and check-ins anchored to it.

Mitigation:

- Soft-deletion preserves referential integrity for historical content.
- Active discovery flows exclude soft-deleted spots.

### Risk: Administrator Bottleneck

A growing community can outpace the administrators' review capacity.

Mitigation:

- Batch approval workflow.
- Future iterations may introduce trusted-contributor automation (see Open Product Decisions).

### Risk: Coordinate Imprecision

Members may propose duplicate spots at slightly different coordinates.

Mitigation:

- Coordinate normalization (fixed precision) and a proximity-based duplicate check (~100 m).
- Administrator judgment at approval time.

## Open Product Decisions

1. **Trusted contributor model**  
   Should highly-active or trusted contributors be allowed to bypass administrator approval for certain low-risk change types (e.g. updating phone contacts)?

2. **Verified marina ownership**  
   Should marina operators be able to claim and verify ownership of "their" spot, with elevated editing rights?

3. **Multilingual spot data**  
   Should spot names and labels support multiple language variants for international sailing communities?

4. **Public read access**  
   Should the spots directory be made available to non-authenticated visitors as a discovery surface?

5. **Conflict-resolution UX**  
   Define the canonical UX when two members submit conflicting proposals concurrently for the same spot.

6. **Hard deletion policy**  
   Confirm whether and when administrators may hard-delete spots (e.g. for legal reasons), and how dependent references should be migrated.

7. **Quality enrichment fields**  
   Decide whether to extend the schema with additional sailing-relevant attributes (depth, fuel availability, services) and whether their introduction should be community- or administrator-driven.

## Related

- `PRD-003-cruises.md` for cruise journeys that anchor on departure and arrival spots.
- `PRD-009-location.md` for the broader location and area context model.
- `PRD-012-check-ins.md` for real-time presence frequently anchored at known spots.
- `PRD-001-users.md` for authentication and administrator role context.
