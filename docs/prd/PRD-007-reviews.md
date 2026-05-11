# PRD-007: Reviews

## Product Purpose

The Reviews feature builds trust in the SkipperClub community after a cruise is completed.
It helps users make safer and better decisions about who to sail with in the future by showing
structured, experience-based feedback about behavior and cooperation on board.

## Business Goals

- Increase trust signals for organizers and participants.
- Improve crew matching quality for future cruises.
- Encourage honest, balanced feedback through a fair review mechanism.
- Provide clear reputation context on user profiles.

## In Scope (Current Phase)

- Post-cruise reviews between eligible users connected to the same cruise.
- Blind review process where feedback is hidden until both sides submit.
- Multi-category ratings and written feedback.
- Review visibility aligned with cruise privacy settings.
- Aggregated ratings on user profiles based on published reviews.

## Out of Scope (Current Phase)

- Review editing after submission.
- Review deletion by users.
- Review appeals and dispute resolution workflows.
- Manual moderation workflows for review content.
- Public ranking or leaderboard mechanics based on reviews.

## User Roles and Responsibilities

### Organizer

- Organizes the cruise and is accountable for onboard collaboration quality.
- Can review eligible cruise participants after cruise completion.
- Can view reviews according to cruise visibility rules.

### Accepted Participant

- Participates in the cruise and can review other eligible users from the same cruise after completion.
- Receives notifications related to pending and published mutual reviews.
- Uses reviews to decide future participation with specific organizers and crew members.

### Other User (Non-Participant / Public Viewer)

- Can use published reviews as a trust signal when evaluating users.
- Access to cruise-level reviews depends on whether the cruise is public or private.

## Core Business Rules

### Review Eligibility

1. Reviews are allowed only after the cruise has completed.
2. Only eligible users from that cruise can submit reviews.
3. Users cannot review themselves.
4. A user can submit only one review per reviewed person within the same cruise.

### Review Content and Quality

1. Each review contains scores for communication, behavior, skills, and duties.
2. Ratings use a consistent 1-5 scale.
3. Each review requires written feedback that is substantive and meaningful.
4. An overall average rating is calculated from category scores.

### Blind Review Fairness

1. A submitted review remains hidden until the other side submits its reciprocal review.
2. Neither side can view the other review before mutual publication.
3. Once both sides submit, both reviews become visible at the same time.
4. Users are informed when a review is waiting for their reciprocal submission and when publication occurs.

### Visibility and Privacy

1. For public cruises, published cruise reviews are visible to everyone.
2. For private cruises, published cruise reviews are visible only to the organizer and accepted participants.
3. Published reviews contribute to user profile reputation and aggregate ratings.
4. Cruise privacy settings always take priority for cruise-level review visibility.

## Why Blind Reviews Matter

Blind publication is a trust mechanism designed to reduce biased behavior:

- Reduces retaliation risk (users cannot react to already-seen feedback).
- Reduces social pressure to give inflated ratings.
- Encourages independent and honest evaluation from both sides.

## User Journeys

### Journey 1: Participant Submits First

1. Cruise is completed.
2. Participant submits a review for another eligible user.
3. The review remains hidden as waiting for reciprocal feedback.
4. The reviewed user is prompted to submit a reciprocal review.
5. When reciprocal feedback is submitted, both reviews are published simultaneously.

### Journey 2: Organizer and Participant Exchange Feedback

1. Cruise is completed.
2. Organizer reviews an accepted participant.
3. Participant reviews the organizer.
4. Both reviews are published at the same moment and become part of profile reputation.

### Journey 3: User Evaluates Another User Before Joining a Cruise

1. User opens another profile to assess reliability and cooperation quality.
2. User reads published reviews and aggregated ratings.
3. User makes a participation decision with better trust context.

## Functional Requirements

- Post-cruise blind review system.
- Multi-category rating model: communication, behavior, skills, duties.
- Mutual publication model for reciprocal reviews.
- Visibility rules aligned with cruise privacy.
- Profile-level aggregation of published reviews.

## User Stories

### US-014: Submitting a Review

As an eligible cruise user, I want to rate another eligible user after the cruise so that I can share meaningful experience-based feedback and strengthen community trust.

Acceptance criteria:

1. I can submit a review only after the cruise is completed.
2. I can review only eligible users from that cruise, never myself.
3. I submit ratings across communication, behavior, skills, and duties on a 1-5 scale.
4. I provide meaningful written feedback.
5. I can submit at most one review per reviewed person within the same cruise.
6. The overall score is computed from the category ratings.

### US-056: Blind Review Publication

As an eligible cruise user, I want reviews to be published only when both sides have submitted so that feedback remains fair and independent.

Acceptance criteria:

1. My submitted review remains hidden until reciprocal feedback is submitted.
2. Neither side can see the other review before publication.
3. Both reviews become visible simultaneously after reciprocal submission.
4. I am notified when reciprocal action is needed and when publication is completed.

### US-057: Review Visibility

As a user, I want to see published reviews in the right privacy context so that I can evaluate potential organizers and crew members.

Acceptance criteria:

1. For public cruises, published cruise reviews are visible to everyone.
2. For private cruises, published cruise reviews are visible only to organizer and accepted participants.
3. User profiles show published reviews and aggregate ratings as reputation signals.
4. Cruise privacy rules are consistently respected.

## Success Metrics

- Mutual review completion rate after cruise completion.
- Share of completed cruises with at least one published reciprocal review pair.
- Average time from first submitted review to mutual publication.
- Profile review coverage for active organizers and participants.
- User-reported confidence in crew/organizer selection decisions.

## Risks and Mitigations

### Risk: Low Reciprocal Completion

If second-party reviews are not submitted, first reviews remain hidden and trust value is delayed.

Mitigation:

- Reminder notifications for pending reciprocal reviews.
- Clear messaging that publication requires both sides.

### Risk: Inflated or Unbalanced Feedback

Users may try to avoid honest feedback without a fair process.

Mitigation:

- Blind publication to reduce retaliation and social pressure.
- Structured categories to anchor feedback quality.

### Risk: Privacy Misunderstanding

Users may be unsure who can see cruise-level reviews.

Mitigation:

- Explicit visibility messaging tied to cruise privacy mode.
- Consistent profile and cruise-level communication of visibility expectations.

## Open Product Decisions

- Should users be allowed to edit reviews after publication (and under what constraints)?
- Should users be allowed to delete their reviews?
- Should there be a formal dispute flow for harmful or misleading review content?
- Should review recency weighting be introduced in profile aggregates?
