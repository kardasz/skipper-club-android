# PRD-006: Friends

## Purpose

Define business requirements for how users build, manage, and end trusted social connections in SkipperClub.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Increase trust between sailors through mutual social connections.
- Reduce friction when users discover and connect with relevant people.
- Support social continuity by making connection status clear at every step.
- Improve engagement in adjacent domains through friend-driven interactions.

## Scope

### In Scope

- Sending, receiving, accepting, rejecting, and canceling friend requests.
- Viewing and managing friend requests with filtering, sorting, and pagination.
- Maintaining a personal friend list and removing active friendships.
- Showing social relationship context between two users.
- Triggering business notifications for friend request lifecycle events.
- Reusing friend context across social and cruise-related experiences.

### Out of Scope

- Technical implementation details, transport contracts, and infrastructure.
- UI layout and interaction design specifics.
- Blocking or muting users as part of the friendship model.
- Grouping friends into custom lists or circles.
- Recommendation algorithms internals.

## User Roles and Responsibilities

### Standard User

A regular community member who can initiate and manage social connections with other users.

Core business capabilities:

- Send friend requests to other users.
- Review incoming requests and decide whether to accept or reject.
- Cancel outgoing requests before they are resolved.
- Maintain personal friend list and remove friendships when needed.

### Request Sender

A user currently acting as the initiator of a specific friend request.

Business responsibilities:

- Can cancel an unresolved request.
- Waits for recipient decision.
- Receives outcome communication for accepted or rejected requests.

### Request Recipient

A user currently acting as the decision-maker for a specific friend request.

Business responsibilities:

- Reviews pending request.
- Accepts or rejects request.
- Controls whether a new mutual friendship is created.

## Friends Domain Model (Business View)

The Friends domain manages social relationship progression from no relationship, through request exchange, to active friendship and potential removal.

### Relationship Context States

The platform exposes relationship context between two users using three business states:

- `none`: no active friendship and no active pending request.
- `pending`: there is an unresolved request between users.
- `accepted`: both users are active friends.

Business value:

- Removes ambiguity in social interactions.
- Helps users choose correct next action.
- Supports trust-sensitive decisions in community and cruise contexts.

### Friend Request Lifecycle

Friend requests move through the following business lifecycle:

1. Request created by sender.
2. Request visible as awaiting decision.
3. Recipient accepts or rejects, or sender cancels before decision.
4. If accepted, mutual friendship becomes active.
5. Friendship can later be removed by either side.

## Core Business Rules

- Friendship is mutual: when accepted, both users are connected equally.
- A user cannot send a friend request to themselves.
- A duplicate unresolved request between the same two users is not allowed.
- An existing friendship prevents creating a new request between those users.
- Only the recipient can accept or reject an unresolved request.
- Only the sender can cancel an unresolved request.
- Removing a friendship takes effect immediately for both users.
- Removing a friendship does not remove prior private conversation history.
- Reconnection after removal requires starting a new request lifecycle.

## Privacy and Visibility Rules

- Relationship context should be visible enough to guide safe user actions.
- Friends can receive richer social context than non-friends where product policy allows.
- Friendship count acts as a public social trust signal.
- Friendship actions are personal-account actions and cannot be performed on behalf of other users.

## Cross-Domain Business Impact

Friends functionality influences adjacent product domains:

- **Profiles:** relationship context helps users decide whether to connect.
- **Cruises:** friend context supports more confident participant decisions.
- **Messaging:** social connection supports trusted private communication journeys.
- **Notifications:** request lifecycle events drive awareness and response behavior.

## Notification Behavior

Business notifications are expected for key friend request lifecycle events:

- Request sent -> recipient is notified.
- Request accepted -> original sender is notified.
- Request rejected -> original sender is notified.

No notification is expected for:

- Request canceled by sender.
- Friendship removal.

## End-to-End User Journeys

### Journey A: Send and Accept

1. User discovers another profile and sends a request.
2. Recipient receives pending request awareness.
3. Recipient accepts.
4. Both users become friends and see each other in their friend lists.
5. Sender receives acceptance outcome communication.

### Journey B: Send and Reject

1. User sends request.
2. Recipient reviews and rejects.
3. No friendship is created.
4. Sender receives rejection outcome communication.

### Journey C: Send and Cancel

1. User sends request.
2. Before recipient decides, sender cancels.
3. Request disappears from recipient pending view.
4. No friendship is created.

### Journey D: Active Friendship and Removal

1. Two users are active friends.
2. Either user removes the friendship.
3. Friendship ends immediately for both sides.
4. Historical private conversation remains intact.

## Functional Requirements

### FR-001 Relationship Creation

The product enables users to initiate friend requests to build trusted social connections.

### FR-002 Request Decision Control

The product enables recipients to accept or reject pending requests and directly control friendship creation.

### FR-003 Request Withdrawal

The product enables senders to cancel unresolved requests before recipient decision.

### FR-004 Request Management

The product provides filtering, sorting, date-range exploration, and pagination for request management at scale.

### FR-005 Friends List Management

The product provides searchable, sortable, and paginated friend lists for ongoing relationship maintenance.

### FR-006 Mutual Friendship Integrity

The product enforces mutual friendship behavior and synchronized relationship updates for both users.

### FR-007 Relationship Context Visibility

The product communicates relationship context (`none`, `pending`, `accepted`) to reduce ambiguity in social actions.

### FR-008 Cross-Domain Reuse

The product allows friend context to support higher-trust behavior in profiles, cruises, and messaging journeys.

### FR-009 Lifecycle Notifications

The product notifies relevant users about friend request lifecycle outcomes while avoiding unnecessary alerts on cancel and removal actions.

## User Stories

### US-018: Sending Friend Invitations

As a user I want to send friend invitations so I can build trusted social connections.

Acceptance criteria:

1. On another user's profile I can send an invitation and receive confirmation that it was sent.
2. I cannot invite myself or send a duplicate invitation; I see a clear error message.
3. I cannot send a new invitation to a user who is already my friend.
4. If the target profile is unavailable, I receive clear and actionable feedback.

### US-019: Accepting or Rejecting Friend Requests

As a user I want to accept or reject received friend requests to control my friends list.

Acceptance criteria:

1. I can view all pending friend requests.
2. I can accept a request to create a mutual friendship.
3. I can reject a request to decline the friendship.
4. After acceptance both users see each other on their friends lists.
5. The sender is notified of the acceptance.
6. The sender is notified of the rejection.

### US-028: Cancelling Sent Friend Requests

As a user I want to cancel friend requests I've sent if I change my mind.

Acceptance criteria:

1. I can view all my sent requests that are still pending.
2. I can cancel any pending request before the recipient responds.
3. After cancellation the request disappears from the recipient's pending list.
4. Canceling does not create an additional notification for either side.

### US-029: Removing Friends

As a user I want to remove someone from my friends list if the friendship is no longer relevant.

Acceptance criteria:

1. I can remove any user from my friends list.
2. The removal is mutual and immediate for both users.
3. No notification is sent about the removal.
4. Removing a friend does not delete existing chat history.
5. To restore the connection later, a new invitation lifecycle is required.

### US-030: Managing Friend Requests

As a user I want to view and filter my friend requests to manage my social connections efficiently.

Acceptance criteria:

1. I can view both sent and received friend requests.
2. I can filter requests by state (`pending`, `sent`, `accepted`, `rejected`, `canceled`).
3. I can filter requests by date range.
4. I can sort requests by recency and state.
5. Results are paginated for performance and clarity.

## Success Metrics

- Friend request acceptance rate.
- Median time from request sent to decision.
- Average number of friends per active user.
- Share of active users with at least one friend.
- Increase in social follow-up actions after friendship creation.

## Out of Scope (Current Phase)

- Blocking relationships and abuse controls at friendship level.
- Friend categories, labels, and custom relationship groups.
- Advanced social graph analytics for end users.
- Automated friendship suggestions with explainable ranking logic.

## Open Product Decisions

1. **Friendship limits policy**  
   Confirm whether a maximum number of friends is needed and how over-limit behavior should be communicated.

2. **Account lifecycle interactions**  
   Confirm friendship behavior when one user is in account deletion grace period.

3. **Post-removal visibility policy**  
   Confirm what social signals remain visible after friendship removal.

4. **Cross-request race behavior**  
   Confirm expected user experience when two users initiate requests around the same time.

## Related

- `PRD-001-users.md` for identity, profile context, and relationship visibility assumptions.
- `PRD-005-notifications.md` for notification channel and recipient behavior.
