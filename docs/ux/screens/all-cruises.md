# All Cruises Screen

## Purpose

Screen showing all available cruises from `GET /cruises`. It acts as the main discovery hub for browsing public (and other visible) cruises, narrowing them down with filters, and starting the create-cruise flow.

## Layout & Main Actions

- **Header**
  - Screen title: “All cruises”.
  - Optional short subtitle explaining that this list shows public and discoverable cruises, not only the user’s own trips.

- **Top actions & filters (visible when there is at least one cruise in the system)**
  - **Primary action button: “Add cruise”**
    - Prominent button leading to the Create Cruise Screen.
    - Positioned at the top of the content area so users see the option to create a cruise before or while browsing the list.
  - **Basic filter row**
    - **Search field** – free-text search mapped to the `search` query, filtering by title, description, and location.
    - **Date range filter** – compact control for selecting departure date range:
      - “From” date mapped to `fromDate`.
      - “To” date mapped to `toDate`.
    - **Hashtag filter** – chip-like control or text field mapped to `hashtag` (without the “#” prefix).
  - **Advanced filters**
    - Collapsible section or modal sheet opened from a small “Filters” / “More filters” control next to the basic filter row.
    - Inside, grouped controls for vessel-related parameters:
      - **Vessel brand** – text field mapped to `vesselBrand`.
      - **Vessel model** – text field mapped to `vesselModel`.
      - **Vessel type** – segmented control or picker mapped to `vesselType` (e.g. SAILING_YACHT, CATAMARAN, etc.).
      - **Cabins** – numeric stepper or slider mapped to `vesselCabins`.
      - **Vessel length** – min/max slider or paired numeric fields mapped to `vesselLengthMin` and `vesselLengthMax`.
    - Clear “Apply filters” and “Reset filters” actions to update or clear the current filters.
  - **Sorting**
    - Compact sorting control (e.g. segmented control or menu) mapped to `sort` and `order`, with sensible presets like:
      - Departure date (ascending/descending).
      - Price per person.
      - Vessel length.
      - Recently created.

- **List area**
  - Vertical scrollable list of cruise cards representing items from the `cruises` array in `CruisesList`.
  - Each card is fully tappable to open the corresponding Cruise Detail Screen (user-context variant).
  - Paging / infinite scroll behavior can be used to represent `limit` and `offset`, but is not exposed as a technical detail in the UI copy; users simply see a continuous list that loads more cruises as they scroll.

## Empty State (no cruises)

- When `GET /cruises` returns an empty list:
  - All filter controls remain visible, so users can adjust filters if they narrowed the list too far.
  - The list area is replaced by a centered illustration or icon and short explanatory text, for example:
    - Main line: “There are no cruises to show yet.”
    - Supporting line: “Adjust filters to broaden your search, or add a new cruise as an organizer.”
  - Below the message, a clear primary action:
    - **Primary**: “Add cruise” – prominent button leading to the Create Cruise Screen, inviting users to create the first cruise.
  - The composition should make it obvious that either changing filters or adding a cruise are the next logical steps.

## Cruise Card Content

Each item in the list represents a single `CruiseWithUserContext` entry, visually focused on the underlying `Cruise` data, with subtle hints about the user’s relationship to the cruise when available.

- **Card header**
  - Organizer avatar (from `organizer.avatarUrl`) displayed as a small circular image.
  - Organizer name (`organizer.name`) next to the avatar.
  - Optional small chip on the opposite side when `currentUserRole` is not `none`, using a short label like “Organizer” or “Participant” to show the user’s role in that cruise.

- **Media area**
  - If the cruise has media in its `media` collection, show a single leading image or a small carousel preview at the top of the card.
  - If no media is available, this area is omitted so the card stays compact.

- **Core cruise details**
  - **Title** – cruise `title`, visually prominent.
  - **Dates** – departure and arrival dates (`departureDate`, `arrivalDate`) in a short, human-readable range (e.g. “12–15 Jul 2025”).
  - **Ports** – `departurePort` and `arrivalPort` displayed together (e.g. “Gdańsk Marina → Hel Marina”).
  - **Vessel** – compact one-line summary combining `vessel`, `vesselType`, and key vessel attributes (brand/model/length) when available.

- **Capacity, price, and visibility**
  - **Price per person** – formatted from `costPerPerson` and `currency`, clearly labelled (e.g. “PLN 150.50 / person”).
  - **Availability** – `participantsCount` vs `maxParticipants`, shown as:
    - “X / Y participants” or “Y spots, X taken”.
    - Optional simple indicator when the cruise is full.
  - **Privacy indicator** – small icon or label showing whether `private` is true or false (e.g. “Private cruise” vs “Public cruise”).

- **Hashtags**
  - If `hashtags` are present, display them in a compact row of hashtag chips (data without the “#” prefix, rendered with “#” in the UI).
  - The row is visually secondary, below the main details.

- **Footer / secondary info (optional)**
  - Very low-emphasis metadata such as:
    - A hint about required skills (shorten `requiredSkills` to a one-line summary when present).
    - A subtle note like “Spots filling up” when the cruise is nearly full.

## Interactions & Navigation

- Tapping anywhere on a cruise card navigates to the Cruise Detail Screen for that cruise, using the appropriate variant based on `currentUserRole` (organizer, participant, or visitor).
- Changing any filter or sorting control reloads the list to show only cruises matching the selected criteria.
- “Add cruise” navigates to the Create Cruise Screen where the user can define a new cruise.
