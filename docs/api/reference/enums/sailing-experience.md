# Sailing Experience Levels

This document describes the sailing experience level enum values used for user profiles.

## Overview

The `SailingExperience` enum defines the sailing competency levels that users can set in their profiles. This helps cruise organizers understand a participant's skill level and helps users find appropriate cruises.

## Available Experience Levels

### `beginner`

**Beginner**  
New to sailing with little to no experience. May have taken an introductory course or had a few day sails. Still learning basic sailing concepts, terminology, and safety procedures. Comfortable being a passenger but needs guidance for any active role on board.

**Typical characteristics:**

- Less than 1 year of sailing exposure
- No formal sailing certifications
- Limited understanding of sailing terminology
- Needs supervision for safety procedures

### `intermediate`

**Intermediate**  
A comfortable crew member with some sailing experience. Can assist with routine tasks like handling lines, trimming sails, and basic navigation. Understands sailing terminology and can follow instructions from experienced sailors.

**Typical characteristics:**

- 1-3 years of sailing experience
- May have basic certifications (e.g., competent crew)
- Can handle routine tasks independently
- Comfortable in moderate weather conditions

### `advanced`

**Advanced**  
An experienced sailor capable of skippering in familiar waters. Has strong practical skills and theoretical knowledge. Can handle challenging conditions and make sound decisions. Often has formal certifications.

**Typical characteristics:**

- 3-10 years of sailing experience
- Formal certifications (e.g., Day Skipper, Coastal Skipper)
- Can skipper in familiar waters
- Experienced in various weather conditions

### `professional`

**Professional**  
A licensed professional skipper with extensive experience. Qualified to command vessels commercially and take responsibility for crew safety. Has comprehensive knowledge of navigation, weather, and maritime law.

**Typical characteristics:**

- 10+ years of sailing experience
- Professional certifications (e.g., Yachtmaster, commercial licenses)
- Can skipper in any waters
- May work as a professional skipper or instructor

## Quick Reference Table

| Level          | Experience | Certifications          | Role                 |
| -------------- | ---------- | ----------------------- | -------------------- |
| `beginner`     | < 1 year   | None                    | Passenger, learning  |
| `intermediate` | 1-3 years  | Basic (optional)        | Active crew member   |
| `advanced`     | 3-10 years | Day/Coastal Skipper     | Can skipper          |
| `professional` | 10+ years  | Yachtmaster, commercial | Professional skipper |

## Usage Notes

- Set via `sailingExperience` field in user profile
- Used for filtering cruises by required skill level
- Helps organizers assess crew composition
- Self-reported — users should be honest about their level
- Can be complemented by `sailingLicenses` field for specific certifications

## Related Profile Fields

| Field                   | Description                              |
| ----------------------- | ---------------------------------------- |
| `sailingExperience`     | Experience level enum                    |
| `sailingLicenses`       | Specific certifications held (free text) |
| `yearsOfExperience`     | Numeric years of experience (0-100)      |
| `preferredVoyageStyles` | Preferred cruise types                   |

## Matching with Cruise Types

| Experience Level | Recommended Cruise Types                |
| ---------------- | --------------------------------------- |
| `beginner`       | `BEGINNER_INTRO`, `RELAX`, `FAMILY`     |
| `intermediate`   | `TRAINING`, `MILEBUILDING`, most types  |
| `advanced`       | `ADVANCED`, `SPORT_REGATTA`, `SURVIVAL` |
| `professional`   | All types, can organize any cruise      |

## Related

- [Users API](../../users/index.md) — Full user profile documentation
- [Cruise Types](./cruise-types.md) — Cruise category types
