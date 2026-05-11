# Cruise Detail Screen – Visitor View

## Purpose

The Cruise Detail Screen in **Visitor** mode presents a complete, read-only view of a public cruise to users who are not yet part of the crew (neither organizer nor confirmed participant).  
From this screen, a visitor can:

- Understand what the cruise is about, when and where it takes place, and what it costs.
- See the vessel characteristics and required skills.
- Browse media, route, and basic statistics.
- Explore the organizer and crew.
- Request to join the cruise or cancel their previous join request.
- Open a dedicated Q&A chat with the organizer.
- Navigate to related cruises via hashtags.

This document focuses only on the **Visitor** view of the Cruise Detail Screen. Organizer and Participant variants are documented separately.

## Layout & Main Sections

The screen is a vertically scrollable detail view composed of clearly separated sections. The join / cancel and Q&A actions remain easy to discover while browsing.

- **Header**
  - **Screen title** – short label such as “Cruise details”.
  - **Cruise title** – prominent display of the cruise name (e.g., “Weekend Baltic Adventure”).
  - **Organizer summary row**:
    - Organizer avatar – small circular photo.
    - Organizer name – text next to the avatar.
    - Tapping the avatar or name opens the Organizer Profile (see Interactions).
  - **Primary actions area**
    - **Join / Cancel button** (stateful):
      - Shows **Join** when the visitor has not yet requested to join.
      - Shows **Cancel** when the visitor has an active join request.
    - **Q&A Chat button**:
      - Secondary button leading to the dedicated Chat Screen for questions about this cruise.
  - Optional short labels or chips indicating:
    - User’s relationship when applicable (e.g., “You requested to join”).

- **Media area**
  - Large, edge-to-edge image or media carousel at the top of the content.
  - Displays photos and videos associated with the cruise.
  - If multiple items are available, the user can swipe horizontally through them or use pagination dots.
  - If no media is available, this section is omitted so the screen focuses on text details.

- **Cruise overview**
  - **Title** – repeated prominently if needed, ensuring the cruise name is always visible near the top.
  - **Short description block**:
    - Rich text description of the cruise, written by the organizer.
  - **Quick facts row** (compact data chips), for example:
    - Duration (e.g., “3 days”).
    - Cost per person (e.g., “PLN 150.50 / person”).
    - Vessel type (e.g., “Sailing yacht”).
    - Approximate crew size (e.g., “8 places”).

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
    - Vessel type (e.g., “Sailing yacht”, “Catamaran”).
    - Vessel length (e.g., “41 ft”).
    - Year of construction if present (e.g., “Year: 2024”).
    - Number of cabins (e.g., “3 cabins”).
  - The emphasis is on giving visitors a clear, non-technical feeling of comfort, size, and type of the boat.

- **Requirements**
  - **Required skills**
    - A short paragraph describing the expected sailing skills or experience (e.g., “Basic sailing knowledge required. Previous experience with larger boats preferred.”).
  - This section helps participants confirm that the cruise still matches their comfort and skill level, and clarify what is expected of them during the trip.

- **Price, capacity & visibility**
  - **Cost per person**
    - Clear, emphasized line showing price and currency (e.g., “PLN 150.50 / person”).
    - If relevant, a concise note that the price is per person and what it includes or does not include can be added in small text.
  - **Capacity and availability**
    - Display the maximum number of participants and how many places are taken:
      - For example: “5 / 8 participants” or “8 places – 5 taken”.
    - Optionally, a low-emphasis message about availability:
      - When nearly full, a hint like “Spots filling up”.
      - When full, a clear note that there are no remaining places.
  - **Privacy indicator**
    - Small label or icon showing whether the cruise is public or private:
      - For example: “Public cruise” or “Private cruise”.
    - The indicator is informational; whether a visitor can join a private cruise depends on separate rules, but the label itself is always clear.

- **Hashtags**
  - A dedicated row or block listing all hashtags associated with the cruise, separate from the main description.
  - Each hashtag is rendered as a tappable chip (e.g., `#baltic`, `#weekend`, `#sailing`).
  - Tapping a hashtag opens the All Cruises Screen with that hashtag applied as an active filter.
  - The layout should support multiple hashtags, wrapping onto additional lines if needed.

- **Accepted participants (bottom section)**
  - At the bottom of the screen, a clearly labeled section lists all accepted participants for the cruise.
  - **Section header**
    - Title such as “Crew” or “Participants”.
    - Optional hint showing how many accepted participants there are (e.g., “Accepted participants (5)”).
  - **Participant list**
    - Vertical list of participant cards, each showing:
      - Avatar – small circular image.
      - Name – full name of the participant.
    - Each participant row is fully tappable:
      - Tapping opens the User Profile screen for that participant.
  - The visitor can scroll through the list to see everyone who has already been accepted onto the cruise.

## Join / Cancel & Q&A Actions

This section describes how the primary actions behave for a visitor on the Cruise Detail Screen. It focuses on user-facing behavior, not technical states or endpoints.

- **Join button (no active request)**
  - When the visitor has not yet requested to join the cruise:
    - A prominent **Join** button is displayed in the header actions area.
  - Tapping **Join** opens a **Join Confirmation** modal:
    - The modal clearly restates the action, e.g.:
      - Title: “Request to join this cruise?”
      - Body: Short explanation that the organizer must accept the request and that places may be limited.
    - Actions inside the modal:
      - **Confirm** – sends the join request, closes the modal, and returns the user to the Cruise Detail Screen with the button now in the **Cancel** state.
      - **Cancel / Dismiss** – closes the modal without creating a join request; the main button on the screen stays as **Join**.
  - If the join attempt fails (for example, the cruise is full or joining is disabled), a user-friendly error message is shown. In such cases, the button stays in the **Join** state and does not switch to **Cancel**.

- **Cancel button (active join request)**
  - When the visitor already has an active join request for this cruise:
    - The primary button shows **Cancel** instead of **Join**.
  - Tapping **Cancel** opens a **Cancel Confirmation** modal:
    - The modal clearly asks if the user wants to withdraw their join request, e.g.:
      - Title: “Cancel your join request?”
      - Body: Short explanation that they will no longer be in the queue to join this cruise.
    - Actions inside the modal:
      - **Confirm** – withdraws the join request, closes the modal, and returns the user to the Cruise Detail Screen with the button switched back to **Join**.
      - **Cancel / Dismiss** – closes the modal without withdrawing the request; the main button on the screen remains **Cancel**.
  - If the cancellation fails due to an error, an appropriate error message appears, and the button state remains in the **Cancel** state.

- **Q&A Chat button**
  - A secondary button labeled **Q&A Chat** is available on the screen.
  - Tapping **Q&A Chat**:
    - Navigates to a dedicated **Chat Screen** focused on questions about this cruise (logistics, requirements, expectations).
    - The chat is specific to this cruise and its organizer.
  - Returning from the Chat Screen (e.g. via Back navigation) brings the user back to the same Cruise Detail Screen in Visitor mode, preserving the visible state and scroll position where possible.

## Interactions & Navigation

This section summarizes the main interactions available on the Cruise Detail Screen for a visitor, and how they connect to other screens in the app.

- **Join / Cancel flow (visitor)**
  - Tap **Join** (no active request) → `Join Confirmation` modal:
    - Confirm → Join request is created → Button changes to **Cancel** → Stay on Cruise Detail Screen.
    - Cancel/Dismiss → No join request created → Button remains **Join**.
  - Tap **Cancel** (active request) → `Cancel Confirmation` modal:
    - Confirm → Join request is withdrawn → Button changes back to **Join** → Stay on Cruise Detail Screen.
    - Cancel/Dismiss → Join request remains active → Button remains **Cancel**.

- **Open Q&A chat**
  - Tap **Q&A Chat** button → Navigate to `Chat Screen` dedicated to this cruise.
  - Back from Chat Screen → Return to the same Cruise Detail Screen (Visitor view).

- **Open organizer profile**
  - Tap organizer avatar or name in the header:
    - Navigate to the organizer’s **User Profile** screen.
  - From User Profile, Back navigation returns the user to the Cruise Detail Screen (Visitor view).

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

The Cruise Detail Screen for a visitor assumes the cruise exists and is publicly visible, but some data may be missing or certain actions may be restricted. The screen should handle these cases gracefully:

- **No media**
  - If the cruise has no media, the media area is simply omitted.
  - The rest of the details (title, description, schedule, vessel, etc.) remain fully visible.

- **No waypoints**
  - If no waypoints are defined, the waypoints subsection is hidden, and only departure and arrival ports are shown.

- **No reviews yet**
  - If there are no published reviews for the cruise, the Reviews section shows a neutral message such as “No reviews have been published for this cruise yet.”

- **Cruise full or join disabled**
  - When there are no spots left or joining is disabled for another reason:
    - The Join button may be disabled or show a message clarifying that new participants cannot join.
    - Any attempt to request to join shows a clear, friendly error explanation.
    - The button does not transition into the **Cancel** state when the join request is not actually created.

Overall, the Cruise Detail Screen in Visitor view should feel like a complete, trustworthy overview of the cruise, clearly signaling how to join, how to ask questions, and how to explore related people and cruises without exposing any underlying technical details.