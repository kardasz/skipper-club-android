# PRD-003: Cruises

## 1. Product Purpose

Cruises is the planning and crew-formation domain of SkipperClub. It helps organizers publish clear sailing plans, attract the right people, and manage participation decisions. It helps users discover relevant cruises, ask questions before committing, and join trips that match their preferences.

The product value comes from three outcomes:

- Better matching between cruise organizers and participants.
- Lower coordination friction before and during a cruise.
- Higher trust in the community through transparent rules and post-cruise feedback.

## 2. Business Scope

Cruises covers:

- Cruise publishing and editing.
- Public discovery and filtering.
- Participation decision flow (request or invitation).
- In-cruise and pre-cruise communication channels.
- Post-cruise participant reviews.
- AI-assisted draft creation to speed up publishing.

Cruises does not define payments, refunds, or financial settlements.

## 3. User Roles and Business Responsibilities

### Organizer

- Creates and owns a cruise.
- Defines participation rules and cruise profile.
- Decides on incoming join requests.
- Invites users directly.
- Can remove accepted participants when needed.
- Maintains communication with participants and prospects.

### Prospective Participant

- Evaluates cruises before applying.
- Can ask organizer questions in a dedicated pre-join conversation.
- Can request to join eligible public cruises.

### Invited User

- Receives direct invitations from organizers.
- Accepts or declines invitations.
- Becomes an active participant only after acceptance.

### Accepted Participant

- Has confirmed participation in the cruise.
- Gains access to group communication with the cruise crew.
- Can leave the cruise if plans change.
- Can review other participants after cruise completion.

### Public User (Observer)

- Browses public cruises.
- Uses discovery filters to find relevant opportunities.
- Does not access private cruise details or participant-only communication.

## 4. Core Business Rules

### 4.1 Cruise Visibility

- Public cruises are discoverable and searchable.
- Private cruises are visible only to organizer and related participants.
- Private cruises are joinable by invitation only.

### 4.2 Capacity and Eligibility

- Every cruise has a participant capacity limit.
- Organizer cannot be treated as a participant candidate.
- The same user cannot hold duplicate participation records in one cruise.
- If capacity is full, new acceptance is blocked until a spot becomes available.

### 4.3 Participation Entry Paths

There are two valid ways to reach accepted participation:

1. **User-driven request path** (user applies to join).
2. **Organizer-driven invitation path** (organizer invites user).

No waiting list is currently part of the confirmed business model.

### 4.4 Participation Lifecycle Governance

The lifecycle is state-based and controls permissions for both sides.

- **Request path:** pending -> accepted or declined by organizer, with user withdrawal option before decision.
- **Invitation path:** invited -> accepted or declined by invited user, with organizer withdrawal option before decision.
- **Active participation:** accepted.
- **Exit from active participation:** participant leaves or organizer removes participant.

Closed outcomes are final and do not automatically reopen.

### 4.5 Notifications and Transparency

- Both sides receive clear decision outcomes for requests/invitations.
- Crew changes are visible to relevant users.
- Availability updates reflect participation decisions.

## 5. Cruise Definition Model (Business View)

An organizer defines a cruise through:

- Identity and narrative (title and description).
- Timeframe (start and end dates).
- Route intent (departure and arrival context).
- Vessel profile (vessel type, optional dimensions/details).
- Cruise profile (cruise type, atmosphere, and expectations).
- Participation preferences (for example smoking, alcohol, children, pets).
- Visibility policy (public or private).
- Capacity limit.

Cruise type and vessel type taxonomy provide consistency across publishing and discovery.

## 6. Discovery and Matching

Users can discover cruises using criteria such as:

- Date window.
- Location context (coordinates + distance, not a region picker — see [PRD-009: Location & Areas](./PRD-009-location.md)).
- Vessel type.
- Cruise type.
- Participation preferences.
- Keywords and hashtags.

Expected business outcome: users should find cruises aligned with their constraints and intent without contacting organizers unnecessarily.

## 7. Communication Model

Cruises uses two communication contexts with different goals:

### Group Communication

- Purpose: coordinate active crew members.
- Access: organizer and accepted participants only.
- Lifecycle impact: users gain access when accepted and lose access when removed or when they leave.

### Q&A Communication

- Purpose: allow prospects to ask organizer questions before joining.
- Access: one-to-one between user and organizer.
- Expected value: improve join quality and reduce mismatched applications.

## 8. Post-Cruise Trust Loop

After cruise completion, accepted participants can submit reciprocal participant reviews.

Business principles:

- Reviews are blind until both sides submit.
- Published reviews contribute to reputation and trust.
- Self-review is not allowed.
- Review availability depends on valid completed participation.

## 9. AI-Assisted Cruise Drafting

AI drafting supports organizers who start from natural language ideas.

Business expectations:

- Extract structured cruise details from free-form text.
- Understand sailing context and regional references.
- Keep organizer in full control through human review and edits before publishing.

Voice-assisted drafting is treated as an input channel to the same drafting outcome.

## 10. Location Context

Location context improves relevance of cruise discovery and planning. There
is no region picker or region taxonomy — see
[PRD-009: Location & Areas](./PRD-009-location.md) for the replacement model
(coordinates, radius, and technical area polygons).

Business requirements:

- Distance-based filtering should be consistent across listing and filtering.
- Localized naming must support English and Polish user experiences.
- GPS-aware experiences may prefill departure/arrival coordinates, especially on mobile.
- Location suggestions should reduce user input effort while preserving organizer control.

## 11. End-to-End User Journeys

### Journey A: Publish and Fill a Cruise

1. Organizer prepares cruise profile and participation rules.
2. Cruise becomes visible according to chosen visibility model.
3. Interested users either ask questions or request to join.
4. Organizer accepts best-fit participants and manages crew composition.
5. Accepted crew uses group communication for coordination.

### Journey B: Prospect to Participant

1. User discovers a relevant cruise.
2. User validates fit through details and optional Q&A.
3. User applies (public cruise) or accepts an organizer invitation.
4. User becomes accepted participant and joins crew communication.
5. User can continue participation or exit when plans change.

### Journey C: Post-Cruise Reputation

1. Cruise completes.
2. Participants submit reciprocal blind reviews.
3. Reviews become visible when publication conditions are met.
4. Reputation quality influences future matching and trust.

## 12. Functional Requirements

### FR-001 Cruise Publishing

Organizers can publish cruises with complete trip profile, participant expectations, and visibility settings.

### FR-002 Cruise Editing and Lifecycle Control

Organizers can update cruise details and remove cruises when needed, with user-facing transparency of impact.

### FR-003 Discovery and Filtering

Users can search and filter public cruises by multi-criteria matching signals.

### FR-004 Participation Decision Flow

The platform supports both join requests and organizer invitations, with clear decision states and final outcomes.

### FR-005 Participant Governance

Organizers can manage accepted participants, and accepted participants can leave cruises voluntarily.

### FR-006 Cruise Communication

The platform supports participant group communication and prospect-to-organizer Q&A with role-based access.

### FR-007 AI-Assisted Drafting

The platform supports natural language and voice-supported drafting with human approval before publishing.

### FR-008 Post-Cruise Reviews

The platform supports blind reciprocal reviews for accepted participants after cruise completion.

## 13. User Stories

### US-005: Publishing a Cruise

As an organizer, I want to publish a cruise with clear criteria so I can attract suitable participants.

Acceptance criteria:

1. I can define cruise profile, route intent, timeframe, and vessel information.
2. I can set participation expectations and visibility (public or private).
3. My cruise is discoverable according to its visibility policy.
4. I can review and refine details before publishing.

### US-006: Cruise Search

As a user, I want to filter cruises by relevant criteria so I can quickly find a suitable trip.

Acceptance criteria:

1. I can filter by date, location (coordinates + distance), cruise type, and vessel type.
2. I can apply additional preference-based filters.
3. I can use keywords and hashtags to narrow results.
4. I can combine filters for precise matching.

### US-007: Joining a Public Cruise

As a user, I want to request to join a public cruise so the organizer can evaluate my fit.

Acceptance criteria:

1. I can send a join request to a public cruise.
2. I can see whether my request is pending, accepted, or declined.
3. I can withdraw my request before a final organizer decision.
4. If accepted, I become an active participant.

### US-008: Organizer Decision on Requests

As an organizer, I want to accept or decline join requests so I can build a suitable crew.

Acceptance criteria:

1. I can review incoming requests and make a decision.
2. Decision outcomes are clearly communicated to affected users.
3. Accepted participants count toward cruise capacity.

### US-037: AI-Assisted Cruise Creation

As an organizer, I want to describe my cruise in natural language so I can generate a draft faster.

Acceptance criteria:

1. I can provide free-form input for cruise intent.
2. The system proposes a structured draft.
3. I can edit all generated content before publishing.

### US-038: Inviting Participants

As an organizer, I want to invite selected users so I can proactively build my crew.

Acceptance criteria:

1. I can send invitations to selected users.
2. Invited users can accept or decline.
3. I can withdraw pending invitations.
4. Accepted invitations convert users into active participants.

### US-039: Updating Cruise Details

As an organizer, I want to update cruise details as plans evolve so participants stay aligned.

Acceptance criteria:

1. I can update cruise information after initial publishing.
2. Relevant users are informed about impactful changes.
3. Updated details are visible consistently.

### US-040: Leaving or Removing Participants

As an accepted participant, I want to leave a cruise when needed, and as an organizer I want to remove participants when necessary.

Acceptance criteria:

1. Accepted participants can leave a cruise.
2. Organizer can remove accepted participants.
3. Group communication access updates accordingly.
4. Capacity reflects crew changes.

### US-041: Deleting Cruises

As an organizer, I want to remove a cruise when it is canceled or no longer relevant.

Acceptance criteria:

1. I can delete cruises that I own.
2. Deleted cruises no longer appear in public discovery.
3. Historical communication continuity is preserved for involved users.

### US-064: Voice-Assisted Cruise Creation

As an organizer, I want to describe my cruise by voice so I can create drafts with less typing effort.

Acceptance criteria:

1. I can provide voice input for cruise drafting.
2. Voice content is transformed into editable structured draft data.
3. The final publish decision remains manual.

## 14. Success Metrics

- Cruise publish-to-first-application conversion.
- Application-to-acceptance conversion.
- Invite acceptance rate.
- Q&A-to-join conversion.
- Percentage of cruises that reach target participant count.
- Participant retention until cruise completion.
- Reciprocal review completion rate after cruise completion.

## 15. Out of Scope (Current Phase)

- Payments, deposits, and refunds.
- Financial penalties for no-shows or late cancellation.
- Formal waitlist and automatic promotion logic.
- Organizer transfer between users.

## 16. Open Product Decisions

- Should waitlist be introduced as a future business capability?
- Should private cruises support shareable access links for controlled discovery?
- Should coordinate consistency be mandatory between departure and arrival context?
- What is the policy for communication retention after long-term account deletion?
