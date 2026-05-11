# PRD-001: Users

## Purpose

Define business requirements for how user identity, profile trust, community visibility, and account lifecycle work in SkipperClub.

This document focuses on product behavior and business rules. It intentionally avoids technical and API-level details.

## Business Objectives

- Build trust between sailors through meaningful, credible user profiles.
- Enable safe and flexible access with both password and one-time-code login.
- Support community growth through user discovery and controlled invitations.
- Protect user privacy with clear public vs private profile boundaries.
- Provide reversible account deletion with a clear grace period and communication.

## Scope

### In Scope

- Registration and authentication experience.
- Session continuity across multiple devices.
- User profile management, including sailing experience and social trust signals.
- Avatar management as part of profile identity.
- User discovery and profile viewing.
- Role model for standard users and administrators.
- Account deletion, cancellation, and final deletion lifecycle.
- Admin invitation flow and invitation-based registration.

### Out of Scope

- Internal implementation details and service architecture.
- Endpoint contracts and transport-level behavior.
- UI designs and visual components.

## Personas and Roles

### Standard User

A regular community member who joins cruises, builds a profile, discovers other sailors, and participates in social interactions.

Core business capabilities:

- Register and authenticate.
- Build and maintain personal sailing profile.
- Upload and replace avatar.
- Discover other users and review public profile details.
- Manage sessions across devices.
- Schedule and cancel account deletion.

### Administrator

A trusted operator responsible for controlled user growth and selected governance functions.

Core business capabilities:

- All standard user capabilities.
- Send platform invitations to new users.
- Oversee invitation lifecycle (active, accepted, expired).

## Sailing Experience Framework

The profile supports four self-declared experience levels:

- `beginner`: new sailor, learning basics.
- `intermediate`: comfortable crew member.
- `advanced`: experienced sailor, can skipper in familiar contexts.
- `professional`: licensed and highly experienced skipper.

Business intent:

- Help organizers assess candidate fit.
- Improve user-to-user trust.
- Support better matching between user profiles and cruise expectations.

## User Lifecycle

### 1. Registration

Users can join through standard sign-up or invitation-based registration. New accounts start with standard user permissions.

### 2. Active Account

An active account can authenticate, manage profile data, participate in discovery, and use social/community features.

### 3. Deletion Scheduled (Grace Period)

When deletion is requested, the account enters a 30-day grace period:

- Access remains possible during this period.
- Deletion can be canceled by the user.
- Successful login during the grace period restores the account to active state.

### 4. Deletion Canceled

Cancellation immediately restores normal account status and keeps user data available to the account owner.

### 5. Deletion Finalized

After the grace period, the account is finalized as deleted and cannot continue normal user access.

## Profile Domain Model (Business View)

The user profile is designed as a trust and matching asset, not just account metadata.

Profile information includes:

- Identity basics (display name, avatar, location).
- Sailing credibility (experience level, years of experience, licenses/certificates).
- Communication context (languages spoken, preferred language).
- Social context (selected social handles and contact channels).
- Community activity signals (cruises count, friends count, posts count).

## Privacy and Visibility Rules

- The profile owner can access private account data tied to ownership.
- Other users can access a public profile view designed for trust and discovery.
- Public profile views must not expose private account identifiers.
- Relationship context between two users should be visible enough to guide safe social actions.

## Social Relationship Context

The platform communicates relationship context between users through three business states:

- `none`: no active relationship.
- `pending`: relationship request is in progress.
- `accepted`: users are connected.

Business value:

- Reduces ambiguity in user interactions.
- Improves confidence before private communication.
- Supports safer social engagement and crew selection.

## Invitation-Led Growth

Administrators can invite users by email to support controlled community expansion.

Invitation policy:

- Each invitation uses a unique code.
- Invitations have a fixed validity window.
- New invitation for the same email replaces any existing active invitation.
- Invitation codes are single-use and tied to the invited email identity.

## Business Rules and Constraints

- Registration requires valid identity and credential inputs.
- Email uniqueness is enforced at account creation.
- Profile fields must respect business validation boundaries (format, ranges, and allowed values).
- Sailing licenses and certificates are short-form profile evidence (business limit: concise text).
- Authentication failures must be handled with clear, user-readable feedback.
- High-risk flows (login attempts, one-time code requests) require abuse protection controls.

## User Stories

### US-001: Account Registration

As a new user, I want to register with email and password so I can join the platform.

Acceptance criteria:

1. Required registration data is validated before account creation.
2. A successful registration creates an account that can access the platform.
3. The user receives clear confirmation that registration succeeded.

### US-002: Password-Based Login

As a user, I want to log in with my email and password to access my account securely.

Acceptance criteria:

1. Valid credentials grant account access.
2. Session lifetime balances convenience and security.
3. Repeated failed attempts are limited to reduce abuse.
4. Invalid credentials return clear, actionable feedback.

### US-003: Profile Creation

As a user, I want to provide my sailing experience and preferences so others can assess my fit.

Acceptance criteria:

1. Profile data follows business validation rules.
2. Saved changes are reflected in my profile.
3. Public profile views expose only public profile data, while account-only data remains private.

### US-004: Adding Held Certificates and Licenses

As a user, I want to add certificates and licenses so others can evaluate my credibility.

Acceptance criteria:

1. License and certificate notes support concise text input.
2. This information is shown in profile context where relevant for trust and matching.

### US-015: Account Deletion

As a user, I want to delete my account to withdraw from the platform.

Acceptance criteria:

1. Deletion enters a 30-day grace period after confirmation.
2. During grace period, the account can be restored by cancellation.
3. After grace period ends, deletion is finalized and normal access is no longer available.
4. The user receives clear status communication throughout the deletion journey.

### US-016: Session Refresh

As a user, I want active use to keep my session alive so I am not interrupted unnecessarily.

Acceptance criteria:

1. Active usage can extend session continuity.
2. If continuity cannot be maintained, the user is prompted to authenticate again.

### US-023: Passwordless Login

As a user, I want to log in with a one-time email code for convenience and security.

Acceptance criteria:

1. I can request a one-time verification code.
2. The code has a clear expiration window.
3. Code verification grants account access.
4. A code can only be used once.
5. The resulting session works consistently with other login methods.

### US-024: Multiple Device Sessions

As a user, I want to stay logged in on multiple devices at the same time.

Acceptance criteria:

1. Each successful login creates a separate active session.
2. I can review all active sessions.
3. I can end selected sessions without ending all sessions.
4. Ending one session does not disrupt others.

### US-025: User Discovery

As a user, I want to discover other sailors by name so I can find potential crew members.

Acceptance criteria:

1. Name-based discovery supports partial matching.
2. Results present public profile information for decision support.
3. Discovery supports pagination and ordering for usability at scale.
4. I can open a richer profile view to assess sailing fit and credibility.

### US-026: Account Deletion Cancellation

As a user who scheduled deletion, I want to cancel it during grace period so I can restore my account.

Acceptance criteria:

1. Cancellation is available for the full grace period.
2. Cancellation immediately restores active account status.
3. Existing user data remains available after restoration.

### US-027: Profile Avatar Upload

As a user, I want to upload a profile photo to personalize my identity and increase trust.

Acceptance criteria:

1. Supported image types are clearly communicated and enforced.
2. The avatar appears in profile and user discovery contexts.
3. The avatar can be replaced at any time.

### US-031: Admin Platform Invitations

As an administrator, I want to invite new users by email to grow the community in a controlled manner.

Acceptance criteria:

1. An invitation includes a unique registration code.
2. Invitation communication includes clear joining guidance.
3. A new invitation replaces any existing active invitation for the same email.
4. Invitations expire if unused within the defined validity window.
5. Administrators can review invitation lifecycle status.

### US-032: Registration via Invitation Code

As a new user, I want to register using an invitation code so I can join the platform.

Acceptance criteria:

1. Registration accepts a valid invitation code.
2. Registration is allowed only for the invited email identity.
3. The invitation code is single-use.
4. Successful registration marks the invitation as accepted.
5. Expired invitations cannot be used.

## Business Assumptions and Open Product Decisions

The following areas require explicit product decisions to keep business documentation fully consistent:

1. **Final deletion semantics**  
   Define whether final deletion means full erasure, anonymization, or a policy-based mix.

2. **Grace-period profile visibility**  
   Define whether users scheduled for deletion remain discoverable to others during grace period.

3. **Deletion status visibility to the account owner**  
   Define where and how deletion status must be presented in the product journey.

4. **Licenses/certificates text limit policy**  
   Confirm one canonical business limit for the profile field and align all docs to it.

5. **Admin scope clarity**  
   Confirm which governance capabilities are currently in business scope versus planned.

6. **Preferred language management**  
   Confirm whether preferred language is user-editable within profile management.
