# Cruise Participant State Flow

This diagram shows the state machine for cruise participants, including all possible states and transitions between them.

## State Definitions

- **PENDING** - User sent a join request (waiting for organizer decision)
- **INVITED** - Organizer sent an invitation (waiting for participant decision)
- **ACCEPTED** - Participant confirmed in the cruise
- **REJECTED_BY_PARTICIPANT** - Participant rejected the invitation
- **REJECTED_BY_ORGANIZER** - Organizer rejected the join request
- **WITHDRAWN_BY_PARTICIPANT** - Participant withdrew their join request
- **WITHDRAWN_BY_ORGANIZER** - Organizer withdrew their invitation
- **CANCELED_BY_PARTICIPANT** - Participant left cruise
- **CANCELED_BY_ORGANIZER** - Organizer removed the participant from the cruise

## State Diagram

```mermaid
stateDiagram-v2
    [*] --> PENDING: Cruise - User requests to join
    [*] --> INVITED: Cruise - Organizer invites participant

    PENDING --> ACCEPTED: Organizer accepts
    PENDING --> REJECTED_BY_ORGANIZER: Organizer rejects
    PENDING --> WITHDRAWN_BY_PARTICIPANT: Participant withdraws request

    INVITED --> ACCEPTED: Participant accepts
    INVITED --> REJECTED_BY_PARTICIPANT: Participant rejects
    INVITED --> WITHDRAWN_BY_ORGANIZER: Organizer withdraws invitation

    ACCEPTED --> CANCELED_BY_PARTICIPANT: Participant leaves cruise
    ACCEPTED --> CANCELED_BY_ORGANIZER: Organizer removes participant

    REJECTED_BY_PARTICIPANT --> [*]
    REJECTED_BY_ORGANIZER --> [*]
    WITHDRAWN_BY_PARTICIPANT --> [*]
    WITHDRAWN_BY_ORGANIZER --> [*]
    CANCELED_BY_PARTICIPANT --> [*]
    CANCELED_BY_ORGANIZER --> [*]

    note right of PENDING
        Initial state when user
        requests to join cruise
    end note

    note right of INVITED
        Initial state when organizer
        invites user to cruise
    end note

    note right of ACCEPTED
        Active participation state
        Can only transition to
        CANCELED states
    end note

    classDef initialState fill:#4A90E2,stroke:#2E5C8A,color:#fff,stroke-width:2px
    classDef activeState fill:#50C878,stroke:#2E7D4E,color:#fff,stroke-width:2px
    classDef rejectedState fill:#E74C3C,stroke:#A93226,color:#fff,stroke-width:2px
    classDef withdrawnState fill:#F39C12,stroke:#B8760C,color:#fff,stroke-width:2px
    classDef canceledState fill:#7F8C8D,stroke:#5D6D7E,color:#fff,stroke-width:2px

    class PENDING,INVITED initialState
    class ACCEPTED activeState
    class REJECTED_BY_PARTICIPANT,REJECTED_BY_ORGANIZER rejectedState
    class WITHDRAWN_BY_PARTICIPANT,WITHDRAWN_BY_ORGANIZER withdrawnState
    class CANCELED_BY_PARTICIPANT,CANCELED_BY_ORGANIZER canceledState
```

## Flow Summary

### From PENDING (User Request)
1. **Organizer accepts** → ACCEPTED
2. **Organizer rejects** → REJECTED_BY_ORGANIZER (final)
3. **Participant withdraws** → WITHDRAWN_BY_PARTICIPANT (final)

### From INVITED (Organizer Invitation)
1. **Participant accepts** → ACCEPTED
2. **Participant rejects** → REJECTED_BY_PARTICIPANT (final)
3. **Organizer withdraws** → WITHDRAWN_BY_ORGANIZER (final)

### From ACCEPTED (Active Participation)
1. **Participant leaves cruise** → CANCELED_BY_PARTICIPANT (final)
2. **Organizer removes participant** → CANCELED_BY_ORGANIZER (final)

### Final States
All REJECTED, WITHDRAWN, and CANCELED states are terminal - no further transitions are possible.

