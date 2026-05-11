# Cruise Participant State Flow

This diagram shows the state machine for cruise participants, including all possible states and transitions between them.

## State Definitions

| State                      | Description                                                     | Category |
| -------------------------- | --------------------------------------------------------------- | -------- |
| `pending`                  | User sent a join request (waiting for organizer decision)       | Initial  |
| `invited`                  | Organizer sent an invitation (waiting for participant decision) | Initial  |
| `accepted`                 | Participant confirmed in the cruise                             | Active   |
| `rejected_by_participant`  | Participant rejected the invitation                             | Terminal |
| `rejected_by_organizer`    | Organizer rejected the join request                             | Terminal |
| `withdrawn_by_participant` | Participant withdrew their join request                         | Terminal |
| `withdrawn_by_organizer`   | Organizer withdrew their invitation                             | Terminal |
| `canceled_by_participant`  | Participant left cruise                                         | Terminal |
| `canceled_by_organizer`    | Organizer removed the participant from the cruise               | Terminal |

## State Diagram

```mermaid
stateDiagram-v2
    [*] --> pending: User requests to join
    [*] --> invited: Organizer invites participant

    pending --> accepted: Organizer accepts
    pending --> rejected_by_organizer: Organizer rejects
    pending --> withdrawn_by_participant: Participant withdraws request

    invited --> accepted: Participant accepts
    invited --> rejected_by_participant: Participant rejects
    invited --> withdrawn_by_organizer: Organizer withdraws invitation

    accepted --> canceled_by_participant: Participant leaves cruise
    accepted --> canceled_by_organizer: Organizer removes participant

    rejected_by_participant --> [*]
    rejected_by_organizer --> [*]
    withdrawn_by_participant --> [*]
    withdrawn_by_organizer --> [*]
    canceled_by_participant --> [*]
    canceled_by_organizer --> [*]

    note right of pending
        Initial state when user
        requests to join cruise
    end note

    note right of invited
        Initial state when organizer
        invites user to cruise
    end note

    note right of accepted
        Active participation state
        Can only transition to
        CANCELED states
    end note

    classDef initialState fill:#3B82F6,stroke:#1E40AF,color:#FFFFFF,stroke-width:2px
    classDef activeState fill:#10B981,stroke:#047857,color:#FFFFFF,stroke-width:2px
    classDef rejectedState fill:#EF4444,stroke:#B91C1C,color:#FFFFFF,stroke-width:2px
    classDef withdrawnState fill:#F59E0B,stroke:#B45309,color:#000000,stroke-width:2px
    classDef canceledState fill:#6B7280,stroke:#374151,color:#FFFFFF,stroke-width:2px

    class pending,invited initialState
    class accepted activeState
    class rejected_by_participant,rejected_by_organizer rejectedState
    class withdrawn_by_participant,withdrawn_by_organizer withdrawnState
    class canceled_by_participant,canceled_by_organizer canceledState
```

## State Transitions

### From `pending` (User Request)

| Transition                 | Who Can Perform | Result                             |
| -------------------------- | --------------- | ---------------------------------- |
| `accepted`                 | Organizer only  | User joins the cruise              |
| `rejected_by_organizer`    | Organizer only  | Request is rejected (final)        |
| `withdrawn_by_participant` | User only       | User cancels their request (final) |

### From `invited` (Organizer Invitation)

| Transition                | Who Can Perform | Result                               |
| ------------------------- | --------------- | ------------------------------------ |
| `accepted`                | User only       | User joins the cruise                |
| `rejected_by_participant` | User only       | User declines invitation (final)     |
| `withdrawn_by_organizer`  | Organizer only  | Organizer cancels invitation (final) |

### From `accepted` (Active Participation)

| Transition                | Who Can Perform | Result                              |
| ------------------------- | --------------- | ----------------------------------- |
| `canceled_by_participant` | User only       | User leaves the cruise (final)      |
| `canceled_by_organizer`   | Organizer only  | User is removed from cruise (final) |

## Terminal States

All `rejected_*`, `withdrawn_*`, and `canceled_*` states are terminal — no further transitions are possible. Any attempt to transition from a terminal state will result in an `InvalidStateTransitionException`.

## Side Effects

| Transition                   | Notifications                        | Chat Access             |
| ---------------------------- | ------------------------------------ | ----------------------- |
| → `pending`                  | Organizer notified                   | No chat access          |
| → `invited`                  | User notified                        | No chat access          |
| → `accepted`                 | Organizer + participants notified    | Added to group chat     |
| → `rejected_by_organizer`    | Requester notified                   | —                       |
| → `rejected_by_participant`  | No notification (user's own action)  | —                       |
| → `withdrawn_by_participant` | Organizer notified                   | —                       |
| → `withdrawn_by_organizer`   | Invited user notified                | —                       |
| → `canceled_by_participant`  | Organizer + participants notified    | Removed from group chat |
| → `canceled_by_organizer`    | Removed user + participants notified | Removed from group chat |

## Related

- [Cruises API](../../cruises/index.md) — Full cruise documentation
- [Participants](../../cruises/participants.md) — Participant management
- [Invitations](../../cruises/invitations.md) — Join request and invitation flows
