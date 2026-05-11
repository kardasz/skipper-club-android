# Cruise Detail Screen – Organizer View

## Purpose

The Cruise Detail Screen in **Organizer** mode presents a complete, management‑ready view of a cruise to the user who created it.  
From this screen, the organizer can:

- **Review all cruise information** – what the cruise is about, when and where it takes place, what it costs, and what vessel is used.
- **See the current crew situation** – who has been accepted, how many places are left, and what the visibility of the cruise is.
- **Browse media, route, and key statistics** – photos, videos, dates, ports, and waypoints.
- **Access organizer tools** – manage participants, edit cruise details, delete the cruise, talk with the crew in Members Chat, and open the review flow.
- **Explore related people and cruises** – by opening user profiles and navigating via hashtags.

This document focuses only on the **Organizer** view of the Cruise Detail Screen. Visitor and Participant variants are documented separately.

## Layout & Main Sections

The screen is a vertically scrollable detail view composed of clearly separated sections. Organizer actions remain easy to discover while browsing.

- **Header**
  - **Screen title** – short label such as “Cruise details”.
  - **Cruise title** – prominent display of the cruise name (e.g., “Weekend Baltic Adventure”).
  - **Organizer summary row**:
    - Organizer avatar – small circular photo.
    - Organizer name – text next to the avatar.
    - Tapping the avatar or name opens the Organizer Profile (see Interactions).
  - **Organizer role hint**
    - Optional small label such as “You are the organizer” to clearly indicate the user’s role on this cruise.
  - **Primary actions area (organizer tools)**
    - **Manage** button:
      - Clicking this button opens a context menu with the following options:
        - **Manage participants**: Opens the dedicated Participants Management Screen for this cruise.
        - **Edit**: Opens the Cruise Edit Screen for updating cruise information.
        - **Delete**: Opens the Delete Cruise Screen, where the organizer can confirm or cancel the deletion.
    - **Members Chat** button:
      - Opens the group chat shared with accepted crew members.
    - **Review** button:
      - Available after the cruise has completed.
      - Opens the Cruise Review Screen, where the organizer can enter or continue reviews for crew members.
  - There is **no Join / Cancel button**, no Q&A chat button, and no button to leave the cruise. The organizer cannot join or leave their own cruise.

- **Media area**
  - Large, edge‑to‑edge image or media carousel at the top of the content.
  - Displays photos and videos associated with the cruise.
  - If multiple items are available, the organizer can swipe horizontally through them or use pagination dots.
  - If no media is available, this section is omitted so the screen focuses on text details.

- **Cruise overview**
  - **Title** – repeated prominently if needed, ensuring the cruise name is always visible near the top.
  - **Short description block**:
    - Rich text description of the cruise, written by the organizer.
    - May include hashtags (e.g., `#baltic`, `#weekend`, `#sailing`) which are also exposed in the Hashtags section.
  - **Quick facts row** (compact data chips), for example:
    - Duration – derived from departure and arrival dates (e.g., “3 days”).
    - Cost per person – price with currency (e.g., “PLN 150.50 / person” or “EUR 500 / person”).
    - Vessel type – simple label such as “Sailing yacht”, “Catamaran”, or other supported vessel type.
    - Approximate crew size – based on capacity, for example “8 places”.

- **Schedule & route**
  - **Dates**
    - Departure and arrival dates shown as a clear range (e.g., “12–15 Jul 2025”).
    - May additionally mention the day of the week for clarity.
  - **Ports**
    - Departure and arrival ports shown together (e.g., “Gdańsk Marina → Hel Marina”).
    - If helpful, short labels like “From” and “To” are used.
  - **Waypoints / stops**
    - If waypoints are provided, they are displayed as a vertical list of planned stops between departure and arrival.
    - Each waypoint is a simple line item with the name of the location (e.g., “Sopot Pier”, “Hel Peninsula”).
    - If no waypoints are defined, this subsection is omitted so the block remains concise.

- **Vessel details**
  - Compact section describing the boat used for the cruise.
  - **Main vessel line**
    - A single, high‑level description combining brand, model, vessel name, type, and year when available (e.g., “Dufour 41 ‘No Worries’, sailing yacht, 2024”).
    - If full information is not available, the line uses whatever details exist, still keeping the description clear and readable.
  - **Additional vessel attributes** (shown as a short list or pill‑like labels), for example:
    - Brand and model (e.g., “Dufour 41”).
    - Vessel type (e.g., “Sailing yacht”, “Catamaran”).
    - Vessel length (e.g., “41 ft”).
    - Year of construction if present (e.g., “Year: 2024”).
    - Number of cabins (e.g., “3 cabins”).
  - The emphasis is on giving the organizer and potential participants a clear feeling of comfort, size, and type of the boat.

- **Requirements**
  - **Required skills**
    - A short paragraph describing the expected sailing skills or experience (e.g., “Basic sailing knowledge required. Previous experience with larger boats preferred.”).
  - This section helps the organizer confirm that the cruise settings still match their intended audience and clarify expectations for future participants.

- **Price, capacity & visibility**
  - **Cost per person**
    - Clear, emphasized line showing price and currency (e.g., “PLN 150.50 / person”, “EUR 500 / person”, or “USD 1200 / person”).
    - A concise note may indicate what the price includes or excludes.
  - **Capacity and participation**
    - Displays the maximum number of participants and how many places are taken:
      - For example: “5 / 8 participants” or “8 places – 5 taken”.
    - Optionally, a message about availability:
      - When nearly full, a hint like “Spots filling up”.
      - When full, a clear note like “No remaining places”.
    - The organizer always sees management actions regardless of whether the cruise is full.
  - **Visibility indicator**
    - Small label or icon showing whether the cruise is public or private:
      - For example: “Public cruise” or “Private cruise”.
    - For private cruises, this simply clarifies who can discover the cruise; the organizer always has full access here.

- **Hashtags**
  - A dedicated row or block listing hashtags associated with the cruise, separate from the main description.
  - Each hashtag is rendered as a tappable chip (e.g., `#baltic`, `#weekend`, `#sailing`).
  - Tapping a hashtag opens the All Cruises Screen with that hashtag applied as an active filter.
  - The layout supports multiple hashtags, wrapping onto additional lines if needed.

- **Accepted participants (bottom section)**
  - At the bottom of the screen, a clearly labeled section lists all **accepted participants** for the cruise.
  - **Section header**
    - Title such as “Crew” or “Participants”.
    - Optional hint showing how many accepted participants there are (e.g., “Accepted participants (5)”).
  - **Participant list**
    - Vertical list of participant cards, each showing:
      - Avatar – small circular image.
      - Name – full name of the participant.
    - Each participant row is fully tappable:
      - Tapping opens the User Profile screen for that participant.
  - The organizer can scroll through the list to see everyone who has already been accepted onto the cruise.

## Organizer Management & Communication Actions

This section describes how the management and communication actions behave for an organizer on the Cruise Detail Screen. It focuses on user‑facing behavior, not technical states or endpoints.

- **Manage participants button**
  - A primary, clearly visible button labeled **Manage participants** is available in the header.
  - Tapping **Manage participants**:
    - Navigates to the dedicated **Participant Manage Screen** for this cruise.
    - On that screen, the organizer can review requests, invitations, and accepted crew (full interactions are defined elsewhere).
  - Returning from the Participant Manage Screen (e.g., via Back navigation) brings the user back to the same Cruise Detail Screen in Organizer mode, ideally preserving the visible state and scroll position.

- **Members Chat button**
  - A primary or secondary button labeled **Members Chat** is available in the header actions.
  - Tapping **Members Chat**:
    - Navigates to the dedicated **Members Chat Screen** for this cruise.
    - The chat is a group conversation for the organizer and accepted crew to coordinate logistics, share updates, and discuss details.
  - Returning from the Members Chat Screen brings the user back to the Cruise Detail Screen in Organizer mode.

- **Edit button**
  - A clearly visible button labeled **Edit** is available in the header actions.
  - Tapping **Edit**:
    - Navigates to the **Cruise Edit Screen**.
    - On the Cruise Edit Screen, the organizer can modify cruise details such as title, dates, route, description, tags, and capacity (full form specification is documented separately).
  - After using the Cruise Edit Screen:
    - Saving changes updates the cruise and returns the organizer according to global navigation rules (typically back to the Cruise Detail Screen for this cruise or to the My Cruises Screen).
    - Canceling or backing out discards unsaved changes and returns to the Cruise Detail Screen with cruise details unchanged.

- **Delete button**
  - A clearly distinguishable button labeled **Delete** is available in the header actions.
  - Tapping **Delete**:
    - Navigates to the **Delete Cruise Screen**.
    - On the Delete Cruise Screen, the organizer can review key cruise information and confirm or cancel deletion.
  - If the organizer confirms deletion:
    - The cruise is removed.
    - The user is navigated away from the Cruise Detail Screen (typically back to My Cruises or previous context).
  - If the organizer cancels or backs out:
    - The cruise is not deleted.
    - The user returns to the Cruise Detail Screen in Organizer mode with the **Delete** button still available.
  - Any errors are displayed clearly; in such cases, the cruise remains unchanged.

- **Review button**
  - A button labeled **Review** is available to the organizer **after the cruise has completed** (once it is eligible for post‑cruise reviews).
  - When visible, tapping **Review**:
    - Navigates to the **Cruise Review Screen**.
    - On that screen, the organizer can provide ratings and comments for crew members (full flow is specified separately).
  - After submitting or canceling review actions:
    - The user returns according to global navigation rules (for example, back to the Cruise Detail Screen or My Cruises Screen).
  - When the cruise is not yet completed or reviews are otherwise unavailable, the **Review** button is hidden.

## Interactions & Navigation

This section summarizes the main interactions available on the Cruise Detail Screen for an organizer, and how they connect to other screens in the app.

- **Manage participants flow**
  - Tap **Manage participants** → `Participant Manage Screen`:
    - Use management tools there (accept, reject, invite, remove, etc., as defined elsewhere).
    - Back/Close → Return to `Cruise Detail Screen (Organizer view)`.

- **Members Chat flow**
  - Tap **Members Chat** → `Members Chat Screen`:
    - Chat with the crew about logistics and updates.
    - Back → Return to `Cruise Detail Screen (Organizer view)`.

- **Edit cruise flow**
  - Tap **Edit** → `Cruise Edit Screen`:
    - Save changes → Cruise is updated → Return according to global navigation (typically `Cruise Detail Screen (Organizer view)`).
    - Cancel/Back → No changes saved → Return to `Cruise Detail Screen (Organizer view)`.

- **Delete cruise flow**
  - Tap **Delete** → `Delete Cruise Screen`:
    - Confirm delete → Cruise is removed → Navigate away from `Cruise Detail Screen (Organizer view)` (e.g., to `My Cruises Screen` or previous context).
    - Cancel/Back → Cruise not deleted → Button remains **Delete** → Return to `Cruise Detail Screen (Organizer view)`.

- **Review flow (after cruise completion)**
  - Tap **Review** (when available) → `Cruise Review Screen`:
    - Submit or manage reviews → Return according to global navigation (e.g., `Cruise Detail Screen (Organizer view)` or `My Cruises Screen`).
    - Cancel/Back without submitting → Return without changes to reviews.

- **Open organizer profile**
  - Tap organizer avatar or name in the header:
    - Navigate to the organizer’s **User Profile** screen.
  - From User Profile, Back navigation returns the user to the Cruise Detail Screen (Organizer view).

- **Open participant profile**
  - Scroll to the **Accepted participants** section at the bottom.
  - Tap any participant’s row (avatar or name):
    - Navigate to that participant’s **User Profile** screen.
  - From User Profile, Back navigation returns the user to the Cruise Detail Screen (Organizer view).

- **Open hashtag‑filtered cruise list**
  - Tap any hashtag displayed on the cruise (in the description or hashtags block).
  - Navigation leads to the **All Cruises Screen** with the selected hashtag applied as an active filter:
    - The filtered list shows only cruises that match the chosen hashtag.
  - On the All Cruises Screen, the organizer can adjust or clear filters to broaden the search and explore other cruises.

- **Media interaction**
  - Swipe horizontally across the media area to browse multiple images or videos (if available).
  - Tapping a media item can optionally open a full‑screen viewer with zoom and swipe (implementation detail not enforced by this document), as long as returning is simple and intuitive.

- **Actions not present for the organizer**
  - There is **no Join button** and **no Cancel button** on this screen.
  - There is **no Q&A Chat** button; communication with the crew is handled via Members Chat and other chats.
  - There is **no button to leave the cruise**; the organizer cannot leave their own cruise from this screen.

## Empty & Edge States

The Cruise Detail Screen for an organizer assumes the cruise exists and is accessible to the organizer, but some data may be missing or certain actions may be restricted. The screen should handle these cases gracefully:

- **No media**
  - If the cruise has no media, the media area is simply omitted.
  - The rest of the details (title, description, schedule, vessel, etc.) remain fully visible.

- **No waypoints**
  - If no waypoints are defined, the waypoints subsection is hidden, and only departure and arrival ports are shown.

- **No accepted participants yet**
  - If there are no accepted participants, the Accepted participants section can still appear with:
    - A neutral message such as “No accepted participants yet”.
    - The organizer still has access to the **Manage participants** button to handle requests and invitations.

- **Cruise full or joining disabled**
  - When there are no spots left or joining is disabled for another reason:
    - Capacity information (“8 / 8 participants”, “No remaining places”) clearly reflects that the cruise is full.
    - Join‑related actions for visitors or participants are handled elsewhere; on the organizer view, management actions remain available.

- **Private cruise**
  - For private cruises, the visibility indicator clearly shows that the cruise is private.
  - The organizer still sees the full detail view and all management tools regardless of privacy.

- **No reviews yet**
  - If there are no published reviews for the cruise, the organizer does not see any review summaries here.
  - When the cruise is completed and reviews become available:
    - The **Review** button appears (if applicable).
    - Any review‑related summaries on this screen (if present in future designs) should handle the “no reviews yet” state with a neutral message such as “No reviews have been published for this cruise yet.”

Overall, the Cruise Detail Screen in Organizer view should feel like a complete, trustworthy overview of the cruise, while providing clear, focused tools for managing the crew, communicating with members, and handling post‑cruise reviews—without exposing any underlying technical details.


