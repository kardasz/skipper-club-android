# Cruise Participant Manage Screen – Organizer View

## Purpose

The **Cruise Participant Manage Screen** is an **organizer-only** management view, reached from the  
**Cruise Detail Screen – Organizer View** via the **Manage participants** action.

From this screen, the organizer can:

- Review and manage **current and past crew members** (accepted, left, removed).
- Review and manage **join requests** and **invitations**.
- Trigger allowed participant state transitions in line with the global  
  **Cruise Participant State Flow** (see `cruise-participant-state-flow.md`).

This document focuses purely on **UI & interaction behavior** for the organizer. It does not define
server-side validation or authorization rules.

---

## Layout & Main Structure

The Participant Manage Screen is a standard pushed screen on the navigation stack.

- Header
  - Title, e.g. “Zarządzaj uczestnikami”.
  - Standard **Back** navigation (system back or explicit Back button) returning to  
    **Cruise Detail Screen – Organizer View**.

- Segmented control (tabs) below the header:
  - Left segment: **Załoga** – **Crew Member List**.
  - Right segment: **Zaproszenia** – **Invitation List**.
  - Only one tab is visible at a time; switching tabs preserves scroll position per tab if possible.

- Content area
  - **Załoga tab**: vertical list of crew-related participants (current + historical).
  - **Zaproszeni tab**: primary **Zaproś** button + vertical list of request/invitation participants.

Navigation from this screen:

- **Back** → Returns to `Cruise Detail Screen – Organizer View`, ideally preserving:
  - Selected tab (Załoga / Zaproszeni).
  - Scroll position on the previously visible tab.
- **Tap avatar/name** anywhere in lists → **User Profile Screen**.  
  Back from profile returns to the Participant Manage Screen (same tab & scroll position).

---

## Data & State Overview

The screen operates on `CruiseParticipant` resources for a given `cruiseId`, using:

- `GET /cruises/{cruiseId}/participants`
  - Returns `CruiseParticipantsList` (with `participants: [CruiseParticipant]`).
  - Supports `state` filter and pagination via `limit` / `offset`.
- `PATCH /cruises/{cruiseId}/participants/{participantId}`
  - Body: `CruiseParticipantStateUpdate { state }`.
  - Used by organizer to update participant state (accept/reject/remove/withdraw).
- `POST /cruises/{cruiseId}/participants`
  - Body: `{ userId }` – creates a new participant record (invitation or join request).

For the UI, we use the logical states defined in **Cruise Participant State Flow**:

- `PENDING`
- `INVITED`
- `ACCEPTED`
- `REJECTED_BY_PARTICIPANT`
- `REJECTED_BY_ORGANIZER`
- `WITHDRAWN_BY_PARTICIPANT`
- `WITHDRAWN_BY_ORGANIZER`
- `CANCELED_BY_PARTICIPANT`
- `CANCELED_BY_ORGANIZER`

The backend encodes these as `CruiseParticipantState` enum values  
(`pending`, `invited`, `accepted`, `rejected_by_participant`, etc.).

---

## Załoga Tab – Crew Member List

### Included States

The **Załoga** tab shows users who are or were crew members of the cruise:

- `ACCEPTED` – active crew members.
- `CANCELED_BY_ORGANIZER` – removed from the cruise by organizer (terminal).
- `CANCELED_BY_PARTICIPANT` – participant left the cruise (terminal).

The list is typically grouped so that:

- `ACCEPTED` participants appear at the top (primary management focus).
- Terminal states (`CANCELED_BY_*`) appear below, as read-only history entries.

### List Item Layout

Each row in the **Załoga** list:

- Left:
  - **Avatar** – circular user profile image (falls back to placeholder if missing).
- Center:
  - **User name** – full display name.
- Right:
  - **Status badge** – color and label depend on participant state.
  - **Contextual action button** – only for states that allow organizer action (see below).

The whole row (excluding explicit buttons) is tappable:

- Tap avatar or name → **User Profile Screen**.

### State-specific UI & Behavior

#### ACCEPTED

- **Description**
  - Active, confirmed crew members for the cruise.

- **Status badge**
  - Color: **green**.
  - Text: **„Członek załogi”**.

- **Actions**
  - **Primary action button** on the right: **„Usuń z rejsu”**.
    - Tapping opens a confirmation dialog/modal:
      - Title: e.g. “Usunąć uczestnika z rejsu?”.
      - Message: short explanation that the participant will lose crew status and access.
      - Buttons:
        - Primary destructive: “Usuń z rejsu”.
        - Secondary: “Anuluj”.
    - On confirmation:
      - Call `PATCH /cruises/{cruiseId}/participants/{participantId}` with:
        - `state: "canceled_by_organizer"`.
      - While the request is in progress:
        - Disable the button (and optionally show inline activity indicator).
      - On success:
        - The participant stays in the **Załoga** list, but state changes to `CANCELED_BY_ORGANIZER`.
        - UI updates the badge and removes the action button (read-only).
      - On error:
        - Show non-blocking error message (e.g. inline or toast).
        - Keep the participant in `ACCEPTED` state (no optimistic change unless rolled back).

- **Row tap**
  - Avatar/name → User Profile (no state change).

#### CANCELED_BY_ORGANIZER

- **Description**
  - Participant was removed from the cruise by the organizer.
  - This is a terminal state – no further transitions from this screen.

- **Status badge**
  - Color: **red**.
  - Text: **„Uczestnik został wyrzucony”**.

- **Actions**
  - No state-changing actions (no buttons).
  - Row is **read-only**, except:
    - Avatar/name still opens User Profile.

#### CANCELED_BY_PARTICIPANT

- **Description**
  - Participant voluntarily left the cruise.
  - Terminal state – no further transitions.

- **Status badge**
  - Color: **red**.
  - Text: **„Uczestnik opuścił rejs”**.

- **Actions**
  - No state-changing actions (no buttons).
  - Row is **read-only**, except:
    - Avatar/name still opens User Profile.

### Załoga Tab – Loading, Empty & Error States

- **Initial loading**
  - While fetching `GET /cruises/{cruiseId}/participants` (filtered to crew-related states), show:
    - Top-level spinner, or
    - Skeleton rows representing crew entries.

- **Empty state**
  - If there are **no participants** in any of the crew states:
    - Show neutral message, e.g. “Brak członków załogi”.
    - Organizer still has access to **Zaproszeni** tab to invite people.

- **Error state**
  - On network / API error:
    - Show short, user-friendly message, e.g. “Nie udało się załadować listy załogi”.
    - Provide **Retry** action (button or pull-to-refresh).

---

## Zaproszeni Tab – Invitation List

The **Zaproszeni** tab shows all users in the request/invitation flow for this cruise, both active
and terminal states.

### Included States

The tab covers:

- `PENDING` – user requested to join (waiting for organizer decision).
- `INVITED` – organizer invited user (waiting for participant decision).
- `REJECTED_BY_ORGANIZER` – organizer rejected join request (terminal).
- `REJECTED_BY_PARTICIPANT` – participant rejected invitation (terminal).
- `WITHDRAWN_BY_PARTICIPANT` – participant withdrew join request (terminal).
- `WITHDRAWN_BY_ORGANIZER` – organizer withdrew invitation (terminal).

All items remain visible for **history/audit**, but only `PENDING` and `INVITED` support actions on
this screen.

### Layout

Content of the **Zaproszeni** tab:

1. **Primary button at top**
   - Label: **„Zaproś”**.
   - Full-width primary style button pinned at top of the list content area (below the tab bar).
   - Tap opens **User Search Modal** for inviting new users.

2. **Invitation list**
   - Vertical list of participants in request/invitation states.

### List Item Layout

Each row in the **Zaproszeni** list:

- Left:
  - **Avatar** – circular profile image.
- Center:
  - **User name**.
  - Optional text indicating the type of relationship (e.g., “Zaproszenie wysłane”, “Prośba o dołączenie”)
    if it helps clarify context; not mandatory.
- Right:
  - **Status badge** – color & label depend on state.
  - Optional one or two **action buttons** depending on state.

Row tap behavior:

- Tap avatar/name → **User Profile Screen** (no state change).

### State-specific UI & Behavior

#### PENDING (Join request waiting for organizer)

- **Status badge**
  - Color: **orange**.
  - Text: **„Oczekuje na akceptację”**.

- **Actions**
  - Two buttons on the right (or in a contextual action area):
    - **„Akceptuj”** – primary action.
    - **„Odrzuć”** – secondary / destructive action.

  - **Akceptuj**:
    - Optional confirmation (“Zaakceptować prośbę o dołączenie?”).
    - On confirm:
      - Call `PATCH /cruises/{cruiseId}/participants/{participantId}` with:
        - `state: "accepted"`.
      - While in progress:
        - Disable both buttons to prevent duplicate taps.
      - On success:
        - Participant is **removed** from the **Zaproszeni** list.
        - Participant **appears in the Załoga tab** with state `ACCEPTED`
          (green “Członek załogi” badge).
      - On error:
        - Show user-friendly error message.
        - Restore original `PENDING` state and re-enable buttons.

  - **Odrzuć**:
    - Optional confirmation (“Odrzucić prośbę o dołączenie?”).
    - On confirm:
      - Call `PATCH /cruises/{cruiseId}/participants/{participantId}` with:
        - `state: "rejected_by_organizer"`.
      - On success:
        - Row remains in the **Zaproszeni** list, but state changes to `REJECTED_BY_ORGANIZER`
          (badge & actions update).

#### INVITED (Organizer invited user)

- **Status badge**
  - Color: **violet**.
  - Text: **„Zaproszony”**.

- **Actions**
  - Single button on the right:
    - **„Anuluj zaproszenie”**.

  - Behavior:
    - Optional confirmation (“Anulować zaproszenie na rejs?”).
    - On confirm:
      - Call `PATCH /cruises/{cruiseId}/participants/{participantId}` with:
        - `state: "withdrawn_by_organizer"`.
      - On success:
        - Row remains on the **Zaproszeni** list, but state becomes `WITHDRAWN_BY_ORGANIZER`
          (see below), and the action button disappears (read-only).

  - Organizer **cannot** directly force `ACCEPTED` from `INVITED` on this screen; acceptance must
    come from the participant.

#### REJECTED_BY_ORGANIZER

- **Status badge**
  - Color: **red**.
  - Text: **„Odrzucony”**.

- **Actions**
  - No buttons – **read-only item**.
  - Avatar/name tap still opens User Profile.

#### REJECTED_BY_PARTICIPANT

- **Status badge**
  - Color: **red**.
  - Text: **„Odrzucił zaproszenie”**.

- **Actions**
  - No buttons – **read-only item**.
  - Avatar/name tap still opens User Profile.

#### WITHDRAWN_BY_PARTICIPANT

- **Status badge**
  - Color: typically neutral/red/orange (implementation detail), but distinct from active states.
  - Suggested text: **„Wycofał prośbę”**.

- **Actions**
  - No state-changing actions.
  - Row is read-only, except avatar/name → User Profile.

#### WITHDRAWN_BY_ORGANIZER

- **Status badge**
  - Color: neutral or light red (implementation detail).
  - Suggested text: **„Zaproszenie anulowane”**.

- **Actions**
  - No state-changing actions.
  - Row is read-only, except avatar/name → User Profile.

### Zaproszeni Tab – Loading, Empty & Error States

- **Initial loading**
  - Similar to Załoga: show spinner or skeleton rows while initial `GET /cruises/{cruiseId}/participants`
    (filtered to invitation-related states) is in progress.

- **Empty state**
  - If there are no invitation/request entries:
    - Show neutral message, e.g. “Brak próśb i zaproszeń”.
    - The **Zaproś** button remains visible and active.

- **Error state**
  - On failure to load:
    - Show message like “Nie udało się załadować listy zaproszeń”.
    - Provide retry.

---

## „Zaproś” Modal – User Search & Multi-select

Tapping **„Zaproś”** on the **Zaproszeni** tab opens a modal used to search users and send invitations.

### Entry & Exit

- **Entry**
  - Source: **Zaproś** button on Zaproszeni tab.
  - Presentation: full-screen modal or bottom sheet (implementation detail) with clear title.

- **Exit**
  - Close icon / “Anuluj” in the header.
  - Automatic dismissal after successful invitations (optional, but recommended).

### Layout

- **Header**
  - Title: e.g. “Zaproś uczestników”.
  - Close control (X or “Anuluj”).

- **Search field**
  - Single input at top with placeholder, e.g. “Szukaj użytkownika”.
  - As user types:
    - Debounced calls to `GET /users` with `search` query param.
    - Use `limit` / `offset` for pagination if needed.

- **Results list**
  - Each result row shows:
    - Avatar.
    - User name.
    - Optional metadata (e.g. city) – not required by this spec.
    - **Multi-select control**:
      - Checkbox or trailing checkmark indicating selection.
      - Tapping anywhere on the row toggles selected/unselected state.

- **Bottom bar**
  - Sticky bar above safe area with:
    - Text showing selection count, e.g. “Wybrano: 3”.
    - Primary button: **„Zaproś wybranych”**.
      - **Disabled** when no users are selected.

### Behavior

- **Searching**
  - On first open:
    - Optionally show empty state (“Wyszukaj użytkowników, aby wysłać zaproszenia”) or a default list
      (e.g. recent).
  - On text input:
    - Debounce before calling `GET /users`.
    - Replace/append to list depending on pagination behavior.
  - On network errors:
    - Show short message and allow retry (e.g. pull-to-refresh or retry button).

- **Selection**
  - Organizer can select **multiple** users before sending invitations.
  - Tapping a selected user again deselects them.
  - Selected users remain highlighted even if additional search requests are performed, as long as
    they stay in the current result set; if not, selection can be preserved by ID internally.

- **Sending invitations – „Zaproś wybranych”**
  - When at least one user is selected and the organizer taps **„Zaproś wybranych”**:
    - For each selected `userId`:
      - Call `POST /cruises/{cruiseId}/participants` with:
        - `{ "userId": "<selected-user-id>" }`.
    - UI considerations:
      - Disable button and show progress indicator while requests are in flight.
      - Handle partial failures gracefully:
        - For users where invitation succeeded:
          - Add `INVITED` entries to **Zaproszeni** tab (violet “Zaproszony” badge).
        - For users where invitation failed (e.g. already participant, validation error):
          - Show inline error or aggregate error message.
    - On overall success:
      - Dismiss the modal and return to Zaproszeni tab.
      - The list now contains new `INVITED` entries.

- **Deduplication / Validation (UX expectations)**
  - If the backend returns a business error (“participant already exists”, “cannot invite yourself”):
    - Show clear, short explanation and skip those users.
  - The UI should **avoid obvious duplicates** where possible (e.g. hide users already present as
    participants in any state for this cruise), but the exact behavior may depend on API responses.

---

## Interactions & Navigation Summary

High-level organizer flows on the Participant Manage Screen:

- **Entry**
  - From `Cruise Detail Screen – Organizer View`:
    - Tap **Manage participants** → `Participant Manage Screen` (default to Załoga tab or remember
      last used tab).

- **Załoga tab**
  - Scroll through crew members (ACCEPTED + CANCELED_* history).
  - Tap **Usuń z rejsu** on `ACCEPTED` → confirm → participant moves to `CANCELED_BY_ORGANIZER`.
  - Tap avatar/name → User Profile → Back → return to same tab & position.

- **Zaproszeni tab**
  - Tap **Zaproś** → open multi-select User Search Modal → search/select users → **Zaproś wybranych** →
    new `INVITED` entries appear in list.
  - For `PENDING`:
    - Tap **Akceptuj** → state `ACCEPTED` → participant moves to Załoga tab.
    - Tap **Odrzuć** → state `REJECTED_BY_ORGANIZER` → item stays as read-only history.
  - For `INVITED`:
    - Tap **Anuluj zaproszenie** → state `WITHDRAWN_BY_ORGANIZER` → item remains but becomes read-only.
  - Tap avatar/name on any list item → User Profile → Back → return to same tab.

- **Exit**
  - Use Back navigation from Participant Manage Screen → return to `Cruise Detail Screen – Organizer View`.

---

## API & Model References

This screen uses the following endpoints and models from `openapi.yaml`:

- **List participants**
  - `GET /cruises/{cruiseId}/participants`
  - Response: `CruiseParticipantsList` with `participants: [CruiseParticipant]`.
  - Filter by:
    - `state: CruiseParticipantState` (e.g. `accepted`, `pending`, `invited`, `canceled_by_organizer`, …).
    - `limit`, `offset`, `order`, `sort`.

- **Create participant (invite)**
  - `POST /cruises/{cruiseId}/participants`
  - Request body:
    - `{ "userId": "<uuid>" }`
  - Response: `201 Created` with `CruiseParticipant`.

- **Update participant state**
  - `PATCH /cruises/{cruiseId}/participants/{participantId}`
  - Request body: `CruiseParticipantStateUpdate` with:
    - `state` ∈ `[accepted, rejected_by_participant, rejected_by_organizer, withdrawn_by_participant, withdrawn_by_organizer, canceled_by_participant, canceled_by_organizer]`.
  - Response: updated `CruiseParticipant`.

- **Search users to invite**
  - `GET /users`
  - Query:
    - `search` – search phrase (min length 2) to filter users.
    - `limit`, `offset`, `order`, `sort`.
  - Response: `UsersList` with `users: [User]`.

The UI must respect backend validation rules and gracefully handle all error responses defined for
these endpoints (e.g. `UnprocessableEntity`, `Forbidden`, `Unauthorized`).


