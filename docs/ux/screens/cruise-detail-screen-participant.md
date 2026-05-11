## Cruise Detail Screen – Participant View

## Purpose

The Cruise Detail Screen in **Participant** mode presents a complete, up-to-date view of a cruise for users who have already been accepted as crew members (but are not the organizer).  
From this screen, a participant can:

- **Review all key information** about the cruise: what it is about, when and where it takes place, the vessel, required skills, cost, and capacity.
- **See their crew**: the organizer and all accepted participants.
- **Browse media, route, and basic statistics** about the cruise.
- **Access collaboration tools** such as the dedicated Members Chat.
- **Manage their participation** by leaving the cruise when allowed.
- **Open the cruise review flow** once reviews are available for this cruise.
- **Navigate to related cruises via hashtags**.

This document focuses only on the **Participant** view of the Cruise Detail Screen. Organizer and Visitor variants are documented separately.

## Layout & Main Sections

The screen is a vertically scrollable detail view composed of clearly separated sections. The main actions (Members Chat, Leave, Review) remain easy to discover while browsing.

- **Header**
  - **Screen title** – short label such as “Cruise details”.
  - **Cruise title** – prominent display of the cruise name (e.g., “Weekend Baltic Adventure”).
  - **Organizer summary row**:
    - Organizer avatar – small circular photo.
    - Organizer name – text next to the avatar.
    - Tapping the avatar or name opens the Organizer Profile (see Interactions).
  - **Primary actions area**
    - **Members Chat button**:
      - Button labeled “Members Chat”.
      - Navigates to the dedicated Members Chat Screen for this cruise (group conversation for organizer + accepted participants).
    - **Leave button**:
      - Button labeled “Leave”.
      - Opens a Leave Cruise Confirmation modal before actually removing the user from the crew.
    - **Review button**:
      - Button labeled “Review”.
      - Available when the cruise is eligible for reviews (for example, after completion according to global rules).
      - Navigates to the Cruise Review Screen where the participant can submit reviews for other crew members.
  - Optional short labels or chips indicating:
    - User’s role on the cruise (e.g., “You are a participant”).

- **Media area**
  - Large, edge-to-edge image or media carousel at the top of the content.
  - Displays photos and videos associated with the cruise.
  - If multiple items are available, the participant can swipe horizontally through them or use pagination dots.
  - If no media is available, this section is omitted so the screen focuses on text details.

- **Cruise overview**
  - **Title** – repeated prominently if needed, ensuring the cruise name is always visible near the top.
  - **Short description block**:
    - Rich text description of the cruise, written by the organizer.
    - The description may mention itinerary highlights, atmosphere on board, or target audience (e.g., “relaxed family cruise”, “sporty crew”, “training-focused trip”).
  - **Quick facts row** (compact data chips), for example:
    - Duration based on departure and arrival dates (e.g., “3 days”).
    - Cost per person (e.g., “PLN 150.50 / person”).
    - Vessel type (e.g., “Sailing yacht”, “Catamaran”).
    - Approximate crew size (e.g., “8 places”).
  - Optional **status hints**, if provided:
    - Whether the cruise has already started or ended.
    - Whether the participant has already submitted all required reviews (high-level indication only).

- **Schedule & route**
  - **Dates**
    - Departure date and arrival date shown as a clear range (e.g., “12–15 Jul 2025”).
    - May additionally mention the day of the week for clarity.
  - **Ports**
    - Departure and arrival ports shown together (e.g., “Gdańsk Marina → Hel Marina”).
    - If helpful, a short label like “From” and “To” is used.
  - **Waypoints / stops**
    - If waypoints are provided, they are displayed as a vertical list of planned stops between departure and arrival.
    - Each waypoint is a simple line item with the name of the location (e.g., “Sopot Pier”, “Hel Peninsula”).
    - If no waypoints are defined, this subsection is omitted so the block remains concise.

- **Vessel details**
  - Compact section describing the boat used for the cruise.
  - **Main vessel line**
    - A single, high-level description combining brand, model, vessel name, type, and year when available (e.g., “Dufour 41 ‘No Worries’, sailing yacht, 2024”).
  - **Additional vessel attributes** (shown as a short list or pill-like labels), for example:
    - Brand and model (e.g., “Dufour 41”).
    - Vessel type (e.g., “Sailing yacht”, “Catamaran”, “Motorboat”).
    - Vessel length (e.g., “41 ft”).
    - Year of construction if present (e.g., “Year: 2024”).
    - Number of cabins (e.g., “3 cabins”).
  - The emphasis is on giving participants a clear, non-technical feeling of comfort, size, and type of the boat they will sail on.

- **Requirements**
  - **Required skills**
    - A short paragraph describing the expected sailing skills or experience (e.g., “Basic sailing knowledge required. Previous experience with larger boats preferred.”).
  - This section helps participants confirm that the cruise still matches their comfort and skill level, and clarify what is expected of them during the trip.

- **Price, capacity & visibility**
  - **Cost per person**
    - Clear, emphasized line showing price and currency (e.g., “PLN 150.50 / person”).
    - If relevant, a concise note that the price is per person and what it includes or does not include can be added in small text.
  - **Capacity and participation**
    - Display the maximum number of participants and how many places are taken:
      - For example: “5 / 8 participants” or “8 places – 5 taken”.
    - Since the viewer is already accepted, it is clear they are included in the count.
    - Optionally, a low-emphasis message about the state of availability:
      - When nearly full, a hint like “Spots filling up”.
      - When full, a clear note such as “All spots taken”.
  - **Visibility indicator**
    - Small label or icon showing whether the cruise is public or private:
      - For example: “Public cruise” or “Private cruise”.
    - For private cruises, this simply clarifies who can discover the cruise; the participant always has full access here.

- **Hashtags**
  - A dedicated row or block listing all hashtags associated with the cruise, separate from the main description.
  - Each hashtag is rendered as a tappable chip (e.g., `#baltic`, `#weekend`, `#sailing`).
  - Tapping a hashtag opens the All Cruises Screen with that hashtag applied as an active filter.
  - The layout should support multiple hashtags, wrapping onto additional lines if needed.

- **Accepted participants (bottom section)**
  - At the bottom of the screen, a clearly labeled section lists all accepted participants for the cruise (including the organizer if they are counted as part of the crew list).
  - **Section header**
    - Title such as “Crew” or “Participants”.
    - Optional hint showing how many accepted participants there are (e.g., “Accepted participants (5)”).
  - **Participant list**
    - Vertical list of participant cards, each showing:
      - Avatar – small circular image.
      - Name – full name of the participant.
    - Each participant row is fully tappable:
      - Tapping opens the User Profile screen for that participant.
  - The participant can scroll through the list to see everyone who is part of the crew and open their profiles for more details.

## Participant Actions (Members Chat, Leave, Review)

This section describes how the primary actions behave for an accepted participant on the Cruise Detail Screen. It focuses on user-facing behavior, not technical states or endpoints.

- **Members Chat button**
  - A prominent button labeled **Members Chat** is available in the header actions area.
  - Tapping **Members Chat**:
    - Navigates to a dedicated **Members Chat Screen** for this cruise.
    - The chat is a group conversation for all confirmed crew members (organizer + accepted participants).
  - Leaving the Members Chat Screen via Back navigation returns the user to the Cruise Detail Screen in Participant mode, preserving visible state and scroll position where possible.

- **Leave button**
  - When the user is an accepted participant in the cruise:
    - The primary actions area shows a **Leave** button.
  - Tapping **Leave** opens a **Leave Cruise Confirmation** modal:
    - The modal clearly explains the consequences of leaving the cruise, e.g.:
      - Title: “Leave this cruise?”
      - Body: Short explanation that leaving will remove them from the crew and that they may lose access to Members Chat and other participant features.
    - Actions inside the modal:
      - **Confirm** – confirms leaving the cruise, closes the modal, and navigates away from the Cruise Detail Screen, typically back to `My Cruises Screen` or the previous context (according to global navigation rules).
      - **Cancel / Dismiss** – closes the modal without leaving the cruise; the main button on the screen stays as **Leave**.
  - If leaving is temporarily not allowed (for example, because the user is the last required crew member or the cruise is locked), an appropriate, user-friendly error message is shown. In such cases, the participant remains in the crew and the screen stays in Participant mode.

- **Review button**
  - When the cruise is eligible for reviews (for example, after the cruise has completed):
    - A **Review** button is visible in the header actions area or in a clearly visible section near the top of the screen.
  - Tapping **Review**:
    - Navigates to the **Cruise Review Screen** where the participant can submit reviews for other crew members (details defined in a separate document).
  - If reviews are not yet available (for example, before cruise completion):
    - The Review button may be hidden or disabled.
    - Optionally, a low-emphasis hint can explain that reviews will become available after the cruise.

## Interactions & Navigation

This section summarizes the main interactions available on the Cruise Detail Screen for a participant, and how they connect to other screens in the app.

- **Leave cruise flow (participant)**
  - Tap **Leave** → `Leave Cruise Confirmation` modal:
    - Confirm → User is removed from the cruise crew → Modal closes → Navigation transitions away from the Cruise Detail Screen (e.g., to `My Cruises Screen` or the previous context).
    - Cancel / Dismiss → User remains a participant → Button remains **Leave** → Return to Cruise Detail Screen (Participant view).

- **Open Members Chat**
  - Tap **Members Chat** button → Navigate to `Members Chat Screen` dedicated to this cruise.
  - Back from Members Chat Screen → Return to the same Cruise Detail Screen (Participant view).

- **Open cruise review flow**
  - Tap **Review** button → Navigate to `Cruise Review Screen`.
  - After submitting or cancelling the review → Return according to global navigation rules (e.g., back to Cruise Detail Screen or My Cruises Screen).

- **Open organizer profile**
  - Tap organizer avatar or name in the header:
    - Navigate to the organizer’s **User Profile** screen.
  - From User Profile, Back navigation returns the user to the Cruise Detail Screen (Participant view).

- **Open participant profile**
  - Scroll to the **Accepted participants** section at the bottom.
  - Tap any participant’s row (avatar or name):
    - Navigate to that participant’s **User Profile** screen.
  - From User Profile, Back navigation returns the user to the Cruise Detail Screen.

- **Open hashtag-filtered cruise list**
  - Tap any hashtag displayed on the cruise (in the description or hashtags block).
  - Navigation leads to the **All Cruises Screen** with the selected hashtag applied as an active filter:
    - The filtered list shows only cruises that match the chosen hashtag.
  - On the All Cruises Screen, the user can adjust or clear filters to broaden the search and explore other cruises.

- **Media interaction**
  - Swipe horizontally across the media area to browse multiple images or videos (if available).
  - Tapping a media item can optionally open a full-screen viewer with zoom and swipe (implementation detail not enforced by this document), as long as returning is simple and intuitive.

## Empty & Edge States

The Cruise Detail Screen for a participant assumes the cruise exists and the user is an accepted crew member, but some data may be missing or certain actions may be restricted. The screen should handle these cases gracefully:

- **No media**
  - If the cruise has no media, the media area is simply omitted.
  - The rest of the details (title, description, schedule, vessel, etc.) remain fully visible.

- **No waypoints**
  - If no waypoints are defined, the waypoints subsection is hidden, and only departure and arrival ports are shown.

- **No reviews yet**
  - If there are no published reviews for the cruise, the Reviews section (if present) shows a neutral message such as “No reviews have been published for this cruise yet.”
  - If the cruise is completed but reviews are not yet submitted, the Review button remains the primary entry point for the participant to start the review process.

- **Private cruise**
  - For private cruises, the visibility indicator clearly shows that the cruise is private.
  - The participant still sees the full detail view and buttons regardless of privacy.

- **Members Chat unavailable**
  - If Members Chat is not available for this cruise (for example, due to configuration or temporary issues):
    - The Members Chat button may be disabled or hidden.
    - If the participant attempts to open it while unavailable, a short message explains that the chat is currently inaccessible.

Overall, the Cruise Detail Screen in Participant view should feel like a complete, trustworthy overview of the cruise for accepted crew members, clearly signaling how to coordinate with the crew, manage participation, submit reviews, and explore related people and cruises—without exposing any underlying technical details.


