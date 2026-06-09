# Reference Documentation

This section contains focused reference documents for enums, state machines, and flows used throughout the SkipperClub API. These documents are designed for quick lookup and as context for AI-assisted development.

## Enums

Enum definitions with all possible values and their descriptions.

| Document                                            | Description                                                     |
| --------------------------------------------------- | --------------------------------------------------------------- |
| [Cruise Types](./enums/cruise-types.md)             | 18 cruise type categories (skill level, demographics, activity) |
| [Vessel Types](./enums/vessel-types.md)             | 6 supported vessel types                                        |
| [Chat Types](./enums/chat-types.md)                 | 4 chat types for messaging system                               |
| [Notification Types](./enums/notification-types.md) | Source types and 16 event types                                 |
| [Sailing Experience](./enums/sailing-experience.md) | 4 experience levels for user profiles                           |
| [Alert Categories](./enums/alert-categories.md)     | 10 navigation alert categories with EN/PL marker labels         |

## Flows & State Machines

State machines and flow diagrams for complex system behaviors.

| Document                                                                  | Description                              |
| ------------------------------------------------------------------------- | ---------------------------------------- |
| [Cruise Participant State Flow](./flows/cruise-participant-state-flow.md) | 9-state machine for cruise participation |
| [Cruise Chat Flows](./flows/cruise-chat-flows.md)                         | Group chat and Q&A chat architecture     |
| [Notification Flows](./flows/notification-flows.md)                       | When notifications are triggered         |
| [Friend Request Flow](./flows/friend-request-flow.md)                     | Friend request state machine             |
| [Blind Review Flow](./flows/blind-review-flow.md)                         | Reciprocal review system                 |
| [Alert Ownership Flow](./flows/alert-ownership-flow.md)                   | Owner / admin authorization on alerts    |

## Usage

These documents are optimized for:

1. **AI Context** — Small, focused documents that can be attached to prompts
2. **Quick Reference** — Fast lookup of enum values and state transitions
3. **Development** — Understanding system behavior without reading full API docs

## Related

- [API Documentation](../index.md) — Full API documentation
- [OpenAPI Specification](../openapi.yaml) — REST API contract
- [AsyncAPI Specification](../asyncapi.yaml) — WebSocket events contract
