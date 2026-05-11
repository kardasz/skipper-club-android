# My Cruises Screen

## Purpose

Screen showing all cruises where the current user has a role (organizer or participant) or an active history entry, based on data from `GET /profile/cruises`. It acts as the main hub for returning to ongoing, upcoming, and past cruises, and for starting new ones.

## Layout & Main Actions

- **Header**
  - Screen title: "My cruises".
  - Subtitle: "Your personal sailing trips" – short explanatory text that this list is personal to the current user.

- **Top actions (visible when the user has at least one cruise)**
  - **Action buttons row** – two side-by-side buttons below the header:
    - **Primary button: "Add cruise"**
      - Prominent button (accent color) leading to the Create Cruise Screen.
      - Full width (50% of screen width in horizontal layout).
    - **Secondary button: "Browse all cruises"**
      - Secondary emphasis button (bordered style) leading to the All Cruises Screen with public cruises.
      - Full width (50% of screen width in horizontal layout).

  - **Filter/Sort menu** – single menu button in the top-right corner of the list area:
    - **Icon**: `line.3.horizontal.decrease.circle` – funnel/filter icon with circle.
    - Opens a contextual menu with three sections:
      1. **Sort By** – submenu with options:
         - `createdAt` – Date Created (default)
         - `updatedAt` – Last Updated
         - `state` – Status
      2. **Order** – submenu with options:
         - `asc` – Ascending
         - `desc` – Descending (default)
      3. **Filter by State** – submenu with participation states:
         - `All` (no filter, default)
         - `pending` – Pending
         - `invited` – Invited
         - `accepted` – Accepted
         - `rejected_by_participant` – Rejected by You
         - `rejected_by_organizer` – Rejected by Organizer
         - `withdrawn_by_participant` – Withdrawn by You
         - `withdrawn_by_organizer` – Withdrawn by Organizer
         - `canceled_by_participant` – Canceled by You
         - `canceled_by_organizer` – Canceled by Organizer
    - Each active selection is marked with a checkmark icon.
    - Changing any filter/sort option reloads the list with new parameters.

- **List area**
  - Vertical scrollable list of cruise cards.
  - Each card is fully tappable to open the corresponding Cruise Detail Screen (role-based view).
  - Cards are ordered in a sensible default (e.g. by departure date or most recent activity) but the exact sorting is not exposed here.

## Empty State (no cruises)

- When `GET /profile/cruises` returns an empty list:
  - The status filter and list are hidden (to avoid empty, unusable controls).
  - Centered illustration or simple icon plus short explanatory text, for example:
    - Main line: “You don’t have any cruises yet.”
    - Supporting line: “Browse all cruises to join an existing trip, or add a new cruise as an organizer.”
  - Two clear actions below the message:
    - **Primary**: “Browse all cruises” – emphasized as the easiest first step.
    - **Secondary**: “Add cruise” – for users who want to create a cruise immediately.
  - The overall composition should be friendly and encouraging, making it obvious what the user can do next.

## Cruise Card Content

Each item in the list represents a single `ProfileCruise` entry, visually focused on the underlying `Cruise` data and the user’s role/state.

- **Card header**
  - Organizer avatar (from cruise organizer profile) displayed as a small circular image.
  - Organizer name next to the avatar.
  - Participant state chip for the current user on the opposite side, using the `state` value from `ProfileCruise`:
    - Short label using a readable form of the state (e.g. “Pending”, “Invited”, “Accepted”, “Canceled by you”).
    - Subtle color coding aligned with the state flow (e.g. active, rejected, withdrawn, canceled).

- **Media area**
  - If the cruise has media in its `media` collection, show a single leading image or a small carousel preview at the top of the card.
  - If no media is available, don't show this area.

- **Core cruise details**
  - **Title** – cruise `title`, visually prominent.
  - **Dates** – departure and arrival dates in a short, human-readable range (e.g. “12–15 Jul 2025”).
  - **Ports** – departurePort and arrivalPort displayed together (e.g. “Gdańsk Marina → Hel Marina”).
  - **Vessel** – compact one-line summary combining vessel name/type information, e.g. from `vessel` and/or `vesselType`.

- **Capacity and price**
  - **Price per person** – formatted from `costPerPerson` and `currency`, clearly labelled (e.g. "PLN 150.50 / person").
  - **Availability** – participantsCount vs maxParticipants, shown as:
    - "X / Y" format with person icon.
    - **Availability badges** (displayed as colored capsule chips next to the participant count):
      - **"Full"** badge (red) – shown when `participantsCount >= maxParticipants` (100% occupancy).
      - **"Filling up"** badge (orange) – shown when occupancy rate > 80% but < 100%.
      - No badge shown when occupancy ≤ 80%.

- **Hashtags**
  - If `hashtags` are present, display them in a compact row of hashtag chips (without the "#" prefix in the underlying data, but rendered with "#" in the UI).
  - The row is visually secondary, below the main details.

## Interactions & Navigation

- Tapping anywhere on a cruise card navigates to the Cruise Detail Screen in a role-aware variant (organizer, participant, or visitor as appropriate).
- Changing the status filter reloads the list to show only cruises with the chosen `state`.
- “Add cruise” navigates to the Create Cruise Screen.
- “Browse all cruises” navigates to the All Cruises Screen with public cruises.


