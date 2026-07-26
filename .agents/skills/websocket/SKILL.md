---
name: websocket
description: SkipperClub Android realtime WebSocket architecture and binding anti-regression rules. Use before inspecting, changing, reviewing, or debugging ChatRealtimeClient, RealtimeConnectionManager, PresenceStore, unread stores, chat list or conversation controllers/screens; and for reconnect, join, catch-up, typing, read-receipt, missed-message, or background/foreground bugs.
---

# SkipperClub Android realtime

This is the Codex discovery entry point for the shared realtime documentation.
The canonical documents are shared with Claude Code so the two agents cannot
silently drift.

## Required context

Before analyzing the task, proposing a change, editing code, or reviewing a
realtime diff, read both of these files completely:

1. [`../../../.claude/skills/websocket/SKILL.md`](../../../.claude/skills/websocket/SKILL.md)
   — architecture, lifecycle rationale, cross-client decisions, and test seams.
2. [`../../../.claude/rules/websocket.md`](../../../.claude/rules/websocket.md)
   — binding implementation requirements and the mandatory anti-regression
   checklist.

Treat the rules document as binding. Do not replace either read with a summary
from memory, and do not start implementation after reading only one document.

## Workflow

1. Identify which realtime components and contract documents are in scope.
2. Check the proposed behavior against the binding invariants and the
   cross-client contract before editing.
3. Preserve recovery paths, lifecycle ownership, retry bounds, and event
   ordering called out by the shared documents.
4. Add or update tests at the layer named by the rules for every behavioral
   change.
5. Before declaring the task complete, run the anti-regression self-review and
   the applicable quality gate from the rules document.

Behavioral guidance belongs in the canonical `.claude/` documents. Update this
adapter only when its trigger description, source paths, or loading workflow
changes.
