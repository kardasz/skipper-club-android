# PRD-010: AI and Voice

## Product Intent and User Value

This initiative helps users create cruise offers faster and with less effort by combining voice input with AI-assisted draft generation.

The core value for users is:

- Faster creation of a first cruise draft
- Lower effort when typing is inconvenient
- Better accessibility for users who prefer speaking over writing
- Higher confidence that they can always start from a usable draft, even when input quality is low

The core value for the business is:

- Higher cruise creation completion rate
- Faster time-to-publish for new cruise offers
- Better activation of new users who may struggle with long forms
- Better retention through reduced creation friction

## Target Users and Usage Contexts

### Primary Users

- Skippers and organizers creating new cruise offers
- Mobile-first users creating content while traveling
- Users with accessibility needs or temporary constraints that make typing difficult

### Typical Contexts

- On the move, with limited time to fill forms
- While planning a trip and quickly capturing details before they are forgotten
- In multilingual environments where users prefer speaking in their native language

## User Jobs to Be Done

1. Capture cruise intent quickly in natural language.
2. Convert spoken or written intent into a structured draft.
3. Review and adjust key details before publishing.
4. Avoid losing momentum when input is incomplete or unclear.
5. Publish a valid, high-quality cruise offer with low manual effort.

## End-to-End User Journey

### Stage 1: Intent Capture

The user starts from an idea and chooses voice or text input. Voice is preferred when speed or convenience is critical.

### Stage 2: Content Understanding

The system converts spoken input into text and interprets user intent to prefill a cruise draft structure.

### Stage 3: Structured Draft Delivery

The user receives a complete draft object that is always usable as a starting point, including safe defaults where information is missing.

### Stage 4: User Review and Refinement

The user reviews all proposed values, edits uncertain fields, and confirms business-critical information such as dates, route, vessel details, participant limits, and participation rules.

### Stage 5: Final Submission

The user submits the reviewed draft as a final cruise offer.

## Business Rules and Guardrails

1. The user must always receive a usable draft response after submitting valid input.
2. If extraction quality is low, the system must fall back to sensible defaults rather than fail the user journey.
3. User input language should be preserved in generated text whenever possible to reduce correction effort.
4. The user remains the final decision-maker; AI output is always a suggestion, not an automatic publication.
5. The experience should support common voice recording formats and typical mobile usage patterns.
6. Input constraints (for example recording size limits) must be clearly communicated to avoid confusion and drop-off.

## User Trust and Reliability Expectations

Users must perceive this feature as dependable, predictable, and safe to use in real workflows.

Trust expectations:

- They can rely on getting a result instead of hitting dead ends
- The first draft feels coherent and close enough to edit
- Fallback behavior is graceful and does not block progress
- Language behavior is intuitive and consistent with user expectations
- Error situations are understandable and recoverable from the user perspective

## Success Metrics

### Adoption Metrics

- Percentage of cruise creators using AI-assisted drafting
- Percentage of voice-driven draft initiations among all draft initiations

### Efficiency Metrics

- Median time from draft start to final cruise submission
- Median number of manual field edits before submission

### Quality Metrics

- Percentage of generated drafts that users continue to edit instead of abandoning
- Percentage of drafts that reach successful publication in the same session

### Reliability Metrics

- Percentage of valid requests that return a usable draft
- Percentage of fallback-based drafts that still convert to published cruises

### Satisfaction Metrics

- User-reported perceived usefulness of AI draft output
- User-reported confidence in voice input workflow

## Functional Scope

### In Scope

- Voice-to-text support for cruise content creation
- AI structuring of natural language descriptions into cruise draft fields
- Predictable fallback behavior with sensible defaults
- User-led review and editing before final publication

### Out of Scope

- Fully autonomous publishing without user review
- Replacing all manual editing for complex or ambiguous scenarios
- Non-cruise content generation flows

## User Stories

### US-063: Voice Input for Cruise Creation

As a user, I want to describe my cruise by voice so that I can create a draft when typing is inconvenient.

Acceptance criteria:

1. I can provide a voice recording and receive a readable transcript.
2. The flow supports common recording formats used by web and mobile users.
3. I can use my preferred language, and the output remains understandable in that language.
4. If my recording is not accepted, I receive clear guidance so I can quickly retry.
5. The transcript quality is good enough to continue the draft flow without rewriting from scratch.

### US-064: AI Draft as a Reliable Starting Point

As a user, I want AI to turn my description into a structured cruise draft so that I can save time on repetitive form filling.

Acceptance criteria:

1. I always receive a complete draft structure after providing a valid description.
2. Missing information is handled with sensible defaults instead of blocking errors.
3. The draft clearly supports user correction and completion before submission.
4. The generated draft reduces manual effort compared to starting from an empty form.

### US-065: User Control and Final Responsibility

As a user, I want to stay in control of all cruise details so that I can trust what gets published.

Acceptance criteria:

1. AI output is presented as editable suggestions.
2. I can update every important business field before publishing.
3. Final publication happens only after explicit user confirmation.

## Business Risks and Mitigations

### Risk: Low AI extraction quality for short or vague input

Mitigation: Keep fallback defaults safe and usable, and guide users to provide richer context.

### Risk: User frustration in edge cases

Mitigation: Ensure error communication is simple, actionable, and focused on recovery.

### Risk: Reduced trust if AI appears opaque

Mitigation: Keep user review central and emphasize that AI assists but does not decide.
