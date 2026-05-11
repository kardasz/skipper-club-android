# PRD-005: Notifications & Email

## 1. Product Purpose

Notifications and email communications keep users informed about relevant actions, decisions, and account events across SkipperClub. This domain reduces coordination delays, lowers missed decisions, and increases user confidence that important platform activity is visible even when users are not actively browsing.

The product value comes from four outcomes:

- Faster user response to social and cruise-related events.
- Better participation decisions through timely and contextual updates.
- Lower uncertainty through clear read state and unread prioritization.
- Reliable account communication for onboarding, access, and account lifecycle events.

## 2. Business Scope

### In Scope

- A centralized in-app notification center for user-relevant events.
- Real-time arrival of new in-app notifications.
- Notification lifecycle management (read, unread, delete, bulk actions).
- Unread counters for prioritization.
- Push notifications as an additional mobile delivery channel.
- User-level channel preferences for notification email and mobile push.
- Transactional account and onboarding emails.
- Multi-language communication behavior for user-facing messages.

### Out of Scope

- Infrastructure details and provider-specific implementation.
- UI layout, visual hierarchy, and interaction design specifics.
- Marketing campaigns, newsletters, and promotional messaging.
- Per-event push preference controls in the current phase.
- Chat/message push channel in the current phase.
- Notification analytics dashboards in the current phase.

## 3. User Roles and Responsibilities

### Standard User

- Receives relevant notifications related to cruises, posts, friendships, and reviews.
- Manages personal notification state and cleanup.
- Uses unread count to prioritize follow-up actions.

### Cruise Organizer

- Receives participant and request-related notifications.
- Uses notifications to react to join requests, invitation responses, and participant changes.

### Content Author

- Receives activity updates on authored content, such as post reactions and comments.

### Reviewed User

- Receives review progress and publication notifications tied to reciprocal review behavior.

## 4. Channel Model

### 4.1 In-App Notification Center

The in-app notification center is the primary channel for activity awareness. It is the canonical user-facing record for social and cruise-related notification events.

### 4.2 Mobile Push Notifications

Push notifications are an additional channel intended to increase visibility when users are away from the app. Push delivery is best-effort and complements, but does not replace, the in-app notification center.

### 4.3 Transactional Email Communications

Email is reserved for transactional and account communication, including onboarding and account lifecycle events. Email is not used as a blanket mirror for all in-app social notifications in the current phase.

### 4.4 User Notification Settings

Users can control global opt-in/opt-out preferences for:

- notification emails
- mobile push notifications

In-app notification center visibility and real-time WebSocket events remain always on.

## 5. Core Business Rules

### 5.1 Universal Principle

- Users never receive notifications for their own actions.

### 5.2 Notification Domains

The product generates user notifications for four active domains:

- Cruises
- Posts
- Friends
- Reviews

### 5.3 Recipient Rules by Event Family

- **Cruise events** notify invited users, organizers, requesters, accepted participants, or affected participants depending on the participation action.
- **Post events** notify post authors when other users react or comment.
- **Friend events** notify request receivers and original senders on request outcome.
- **Review events** support blind review progression and publication awareness.

### 5.4 Read and Deletion Lifecycle

- Notifications start as unread.
- Users can mark notifications as read or unread.
- Users can delete notifications from their personal list.
- Deleted notifications are no longer visible in the user's notification list.
- Bulk actions provide the same outcomes as single-item actions at larger scale.

### 5.5 Ownership and Privacy

- Users can only manage notifications that belong to their own account.
- User actions on notification state affect only their personal notification space.

### 5.6 Language and Message Clarity

- User-facing communication supports English and Polish.
- Messaging should remain clear, action-oriented, and context-rich enough for decision-making.

### 5.7 Channel Preference Rules

- In-app notifications are always available in the notification center.
- Push delivery is controlled by the user's push preference.
- Notification email delivery is controlled by the user's email preference.
- Transactional account emails remain outside notification preference controls.

## 6. Event Coverage

The current phase covers 17 notification event types across four active domains:

- Cruise events: invitation, request, acceptance/rejection, participant changes, cruise updates.
- Post events: reaction, comment.
- Friend events: request sent, request accepted, request rejected.
- Review events: pending reciprocal review, published review.

## 7. End-to-End User Journeys

### Journey A: Cruise Invitation and Decision

1. Organizer invites a user to a cruise.
2. Invited user receives a notification.
3. User accepts the invitation.
4. Organizer receives acceptance feedback, and participants are updated about the new participant.

### Journey B: Join Request and Organizer Decision

1. User requests to join a cruise.
2. Organizer receives a pending request notification.
3. Organizer accepts or rejects.
4. Requester receives outcome notification.

### Journey C: Post Interaction Awareness

1. Another user reacts to or comments on a post.
2. Post author receives a contextual notification.
3. Author decides whether to engage further.

### Journey D: Friend Request Lifecycle

1. User sends a friend request.
2. Receiver is notified.
3. Receiver accepts or rejects.
4. Original sender receives the final outcome notification.

### Journey E: Blind Review Publication

1. First review is submitted.
2. Counterparty is notified that a reciprocal review is needed.
3. Both reviews become visible after reciprocal completion.
4. Both users receive publication awareness notifications.

### Journey F: Inbox Hygiene and Prioritization

1. User opens the notification center.
2. User filters by status or source type.
3. User marks selected notifications as read or deletes outdated items.
4. Unread count reflects the updated state.

### Journey G: Mobile Visibility

1. User enables push notifications on a mobile device.
2. New activity triggers a push alert in addition to in-app notification records.
3. User opens the app and resolves outstanding notifications.

### Journey H: Transactional Email Lifecycle

1. User receives a transactional email for onboarding, access, or account lifecycle change.
2. Email content guides the user to the next required action.
3. User completes the action, reducing support effort and uncertainty.

### Journey I: Notification Channel Preferences

1. User opens notification settings in account/profile context.
2. User enables or disables email and push notification channels.
3. Future notification deliveries follow these preferences.
4. In-app notification center behavior remains unchanged.

## 8. Functional Requirements

### FR-001 Central Notification Center

The product provides one personal notification center where users can review relevant activity across supported domains.

### FR-002 Real-Time Awareness

The product provides real-time arrival of new notifications so users can react without manual refresh behavior.

### FR-003 Notification Organization

The product allows filtering, sorting, and paginated browsing so users can manage high notification volume efficiently.

### FR-004 Notification State Management

The product allows single-item read/unread updates and single-item deletion.

### FR-005 Bulk Management

The product supports bulk read and bulk deletion actions for faster inbox maintenance.

### FR-006 Unread Prioritization

The product provides unread counts that update consistently when notification state changes.

### FR-007 Channel Consistency

The product keeps in-app notifications as the source of truth while using push as an additional visibility channel.

### FR-008 Transactional Email Communication

The product sends transactional email communications for user-critical account and onboarding events.

### FR-009 Multi-Language Communication

The product supports bilingual communication behavior to align with user language context.

### FR-010 User Channel Preferences

The product allows users to manage global channel preferences for notification email and mobile push without affecting in-app notification center behavior.

## 9. User Stories

### US-033: Notification Center

As a user, I want to view all my notifications in one place so I can stay informed about platform activity.

Acceptance criteria:

1. I can view a paginated list of my notifications.
2. I can filter by read or unread state.
3. I can filter by source domain.
4. I can sort by recency.
5. Each item includes enough context to understand what happened.

### US-034: Notification Management

As a user, I want to manage my notifications so I can keep my notification center organized.

Acceptance criteria:

1. I can mark a single notification as read or unread.
2. I can delete a single notification.
3. I can see my unread notification count.
4. Unread count changes consistently with my actions.

### US-035: Bulk Notification Actions

As a user, I want to perform bulk actions on notifications so I can manage high-volume activity efficiently.

Acceptance criteria:

1. I can mark multiple notifications as read in one action.
2. I can mark all notifications as read in one action.
3. I can delete multiple selected notifications.
4. I can delete all notifications in one action.
5. Bulk outcomes are reflected consistently in my notification center.

### US-036: Real-Time Notifications

As a user, I want to receive notifications in real time so I can react quickly to relevant events.

Acceptance criteria:

1. New notifications appear as events occur.
2. Unread count updates as new notifications arrive.
3. I receive real-time updates for supported cruise, post, friend, and review events.
4. I do not receive notifications for my own actions.

### US-037: Mobile Push Awareness

As a mobile user, I want push alerts for relevant activity so I can notice updates when I am away from the app.

Acceptance criteria:

1. I can enable push alerts on my device.
2. I can stop receiving push alerts when I disable them on that device.
3. Push alerts align with in-app notification events in supported domains.
4. Missing push delivery does not remove the in-app record of the event.

### US-038: Transactional Email Clarity

As a user, I want transactional emails for account and onboarding actions so I can complete critical steps confidently.

Acceptance criteria:

1. I receive transactional emails for relevant account and onboarding events.
2. Email content clearly communicates the required next action.
3. Email communication is available in supported languages.
4. Email behavior remains separate from social in-app notification streams.

### US-039: Notification Channel Preferences

As a user, I want to control notification email and push channels so I can choose how I receive non-transactional updates.

Acceptance criteria:

1. I can view my current notification channel settings.
2. I can update both notification channel settings in one save action.
3. Disabling push stops future push alerts without removing in-app notifications.
4. Disabling notification email stops future notification emails without affecting transactional emails.

## 10. Success Metrics

- Increase in weekly active users who open or act on notifications.
- Reduction in time-to-first-response for actionable cruise and social events.
- Decrease in unread notification backlog for active users.
- Higher completion rate for transactional email-driven account actions.
- Lower support requests related to missed account state changes.

## 11. Out of Scope (Current Phase)

- Granular per-event push preference controls.
- Message/chat push notifications.
- Notification digest emails.
- Marketing campaign email logic.
- Advanced user-facing analytics for notification and email outcomes.

## 12. Open Product Decisions

1. **Email and in-app overlap policy**  
   Confirm which high-importance in-app events, if any, should later trigger additional transactional emails.

2. **Priority and urgency model**  
   Define whether some notification categories should have stronger visibility treatment than others.

3. **Granular notification preferences**  
   Confirm if users should control opt-in rules by event family or event type in a future phase.

4. **Retention and cleanup policy**  
   Define business retention expectations for long-term notification history visibility.
