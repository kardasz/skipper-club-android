# PRD-004: Messages

## 1. Product Purpose

Messages is the communication domain of SkipperClub. It helps users coordinate sailing plans, ask clarifying questions, and maintain trusted relationships before, during, and after cruises.

The product value comes from four outcomes:

- Faster coordination between users and crews.
- Better cruise-fit decisions through pre-join conversations.
- Higher community trust through clear conversation continuity and read visibility.
- Lower communication friction through live, always-available chat experiences.

## 2. Business Scope

### In Scope

- Private one-to-one conversations between authenticated users.
- User-created group conversations.
- Cruise communication channels (crew group and organizer Q&A).
- Conversation history and message visibility rules.
- Message read state and unread message tracking.
- Presence and typing signals as coordination aids.
- Personal chat list management, including hide behavior.
- Bulk chat management actions.

### Out of Scope

- Transport and protocol details.
- Internal architecture and infrastructure decisions.
- UI design and interaction design specifications.
- External notification channel strategy (email, push provider specifics).
- Content moderation policy and legal retention policy.

## 3. User Roles and Responsibilities

### Standard User

- Starts private chats and custom group chats with other authenticated users.
- Sends and reads messages in conversations they can access.
- Manages their own chat list and read state.

### Cruise Organizer

- Participates in crew group communication.
- Answers private pre-join questions from prospects.
- Maintains communication transparency with accepted participants.

### Prospective Participant

- Uses private cruise Q&A to evaluate fit before joining.
- Maintains a separate conversation thread with each organizer.

### Accepted Participant

- Gains access to crew coordination chat for accepted cruises.
- Participates in operational coordination with organizer and crew.

## 4. Core Business Rules

### 4.1 Chat Types and Intended Use

- **Private chat** is a direct conversation between two users.
- **Group chat** is a named multi-user conversation for ad hoc coordination.
- **Cruise group chat** supports organizer and accepted participants of one cruise.
- **Cruise Q&A chat** supports private pre-join questions between one user and one organizer.

### 4.2 Access and Membership Rules

- Private and custom group chats can be created between authenticated users without a friendship prerequisite.
- Only one private chat can exist for the same two users.
- Each new group creation produces a separate group conversation, even with the same members.
- Cruise group chat access is limited to organizer and accepted participants.
- Users gain cruise group access when accepted and lose access when removed or when leaving participation.
- Cruise Q&A is private to one user and one organizer, and each user-organizer pair has a separate thread per cruise context.

### 4.3 Conversation Lifecycle and Persistence

- Cruise group chat is created automatically as part of cruise communication setup.
- Cruise Q&A thread is created when the first question is sent.
- Conversation history is retained for future reference, including cruise-related threads.
- A hidden conversation is not globally deleted and does not affect other participants.

### 4.4 Visibility and Chat Hiding

- Users can hide conversations from their personal list.
- Hiding a conversation removes earlier history from that user's visible timeline.
- If a new message arrives, the conversation returns to the user's list.
- After return, only messages from the new period are visible for that user.

### 4.5 Read State, Unread Tracking, and Presence

- Messages support individual read state per participant.
- Users can update read state per message and across multiple conversations.
- Unread tracking is available both per conversation and at total account level.
- Presence and typing signals are available to improve response expectations.

### 4.6 Business Constraints

- Message text supports concise communication with a defined upper length bound.
- Group chat name is mandatory and length-bounded for user-created groups.
- Participant selection and bulk operations are limited to controlled list sizes.
- Message and chat history browsing supports high-volume conversations through bounded result windows.

## 5. Messaging Business Model

### 5.1 Private Communication

Private messaging supports direct user-to-user conversation continuity. It prioritizes low friction and avoids duplicate threads between the same two users.

### 5.2 Group Communication

Custom groups support multi-person planning and social coordination. Group identity is explicit through a user-provided name.

### 5.3 Cruise Crew Communication

Crew communication supports operational planning among organizer and accepted participants. Access follows participation state and updates automatically as participation changes.

### 5.4 Cruise Q&A Communication

Cruise Q&A supports private qualification of expectations before joining. It reduces mismatched participation and improves organizer decision quality.

## 6. End-to-End User Journeys

### Journey A: Start and Continue a Direct Conversation

1. User starts a private conversation with another authenticated user.
2. The conversation appears in both users' chat context.
3. Both users exchange messages and can revisit history over time.
4. Read and presence cues reduce uncertainty about response timing.

### Journey B: Coordinate a Cruise Crew

1. Cruise communication space exists for organizer and accepted participants.
2. Participants join crew conversation automatically upon acceptance.
3. Crew coordinates logistics and preparation in one shared thread.
4. Access changes immediately when participation status changes.

### Journey C: Validate Cruise Fit Through Q&A

1. Interested user starts private questions with organizer.
2. Organizer responds in a dedicated one-to-one thread.
3. Conversation remains private and separate from crew communication.
4. User decides whether to continue toward participation.

## 7. Functional Requirements

### FR-001 Chat Type Coverage

The product supports private, group, cruise group, and cruise Q&A chat contexts with distinct purposes.

### FR-002 Access Governance

The product enforces role- and participation-based access to each chat context.

### FR-003 Conversation Continuity

The product preserves conversation continuity and history according to visibility rules.

### FR-004 Chat Hiding Management

Users can hide conversations in their personal scope without deleting conversations for others.

### FR-005 Read and Unread Management

The product provides message read state and unread summaries at conversation and account levels.

### FR-006 Live Coordination Signals

The product provides presence and typing indicators to support timely coordination.

### FR-007 Multi-Conversation Productivity

The product supports bulk actions to reduce chat management effort for active users.

### FR-008 High-Volume Browsing

The product supports browsing and filtering of long conversation histories in a scalable way.

## 8. User Stories

### US-010: Private Chat

As a user, I want to chat privately with another user so I can coordinate directly.

Acceptance criteria:

1. I can start a private conversation with any authenticated user.
2. The product keeps one shared private thread for the same two users.
3. Both users can continue the same conversation over time with preserved context.

### US-011: Cruise Group Chat

As an accepted cruise participant, I want one shared crew conversation so we can coordinate preparations.

Acceptance criteria:

1. Crew communication is available for organizer and accepted participants.
2. Access to crew communication follows participation state changes.
3. The organizer remains part of crew communication throughout the cruise lifecycle.

### US-012: Cruise Q&A Chat

As a user interested in a cruise, I want a private conversation with the organizer so I can ask questions before joining.

Acceptance criteria:

1. I can start a private Q&A thread with the cruise organizer.
2. Only the organizer and I can view this thread.
3. My Q&A thread is separate from other users' conversations with the same organizer.

### US-042: Group Chats

As a user, I want to create named group chats so I can coordinate with multiple people.

Acceptance criteria:

1. I can create a group conversation with multiple authenticated users.
2. Group identity includes a required name.
3. Each group creation produces an independent conversation thread.

### US-043: Conversation History

As a user, I want to review past conversation context so I can make better decisions.

Acceptance criteria:

1. I can browse conversation history for chats I can access.
2. I can narrow history using practical filtering options.
3. History browsing remains usable even for long conversations.

### US-044: Live Conversation Signals

As a user, I want live conversation signals so I can judge when a response is likely.

Acceptance criteria:

1. I can see when participants are typing.
2. I can see read progress for messages I sent.
3. I can see participant availability status in chat context.

### US-047: Chat Hiding

As a user, I want to hide conversations I no longer need in my list without deleting them for others.

Acceptance criteria:

1. I can hide selected conversations from my personal chat list.
2. Hidden conversations do not affect other participants' access.
3. New incoming activity makes a hidden conversation visible again in my list.

### US-048: Unread Message Counts

As a user, I want unread counters so I can prioritize responses.

Acceptance criteria:

1. I can see unread counts per conversation.
2. I can see one total unread count across my account.
3. Updating read state changes unread counters consistently.

### US-049: Bulk Chat Actions

As a user with many active conversations, I want to manage multiple chats at once so I can keep my inbox organized.

Acceptance criteria:

1. I can apply read updates to multiple conversations in one action.
2. I can hide multiple conversations in one action.
3. Bulk operations update my personal chat state consistently.

## 9. Success Metrics

- Higher share of active users who exchange messages weekly.
- Faster first-response time in active conversations.
- Lower unanswered-rate for cruise Q&A conversations.
- Increased participation conversion after Q&A engagement.
- Reduced unread backlog for active users through bulk actions.

## 10. Out of Scope (Current Phase)

- Message editing and deletion by sender.
- Advanced moderation workflows and abuse escalation policy.
- User-level blocking and mute models.
- Cross-channel campaign logic for message notifications.

## 11. Open Product Decisions

1. **Message retention policy**  
   Define long-term retention, archival horizon, and legal deletion obligations for message content.

2. **Typing indicator policy**  
   Define the exact business timeout behavior for stopping typing visibility.

3. **Presence semantics**  
   Define when users should be considered online from a product perspective in multi-device scenarios.

4. **Message edit/delete policy**  
   Confirm whether sender-side message correction or removal becomes part of future scope.
