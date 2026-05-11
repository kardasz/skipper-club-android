# Cruise Types

This document describes all available cruise type enum values that can be assigned to cruises in the system.

## Overview

The `CruiseType` enum defines various categories of sailing cruises, helping users find trips that match their interests, skill level, and preferences. Each cruise can have one type assigned.

## Available Cruise Types

### Skill Level & Training Based

#### `BEGINNER_INTRO`

**Beginner Introduction**  
Cruises designed for people with little to no sailing experience. These trips focus on basic sailing concepts, safety procedures, and getting comfortable with life aboard a sailing vessel. Perfect for first-time sailors who want to try sailing before committing to more advanced training.

#### `TRAINING`

**Training Cruises**  
Structured educational cruises focused on developing specific sailing skills. May include preparation for sailing licenses, advanced navigation techniques, or specialized sailing maneuvers. Suitable for sailors who want to improve their competencies.

#### `MILEBUILDING`

**Mile Building**  
Cruises specifically designed to accumulate nautical miles required for sailing certifications and licenses. These trips often cover longer distances and provide documented proof of miles sailed for certification purposes.

#### `ADVANCED`

**Advanced Sailing**  
Cruises for experienced sailors who want challenging sailing conditions, complex navigation, or demanding routes. Requires proven sailing skills and experience.

#### `SPORT_REGATTA`

**Sport & Regatta**  
Competitive sailing cruises focused on racing, regattas, or sport sailing. Emphasizes performance, tactics, and teamwork in competitive sailing scenarios.

### Demographic & Social Based

#### `FAMILY`

**Family Cruises**  
Cruises specifically designed for families with children. Features family-friendly activities, shorter sailing segments, and child-safe environments. Focuses on creating memorable family experiences on the water.

#### `SINGLES`

**Singles**  
Cruises for single participants looking to meet other solo sailors. Creates a social environment for networking and making new friends while sailing.

#### `COUPLES`

**Couples**  
Romantic cruises designed for couples, whether for honeymoons, anniversaries, or special getaways. May feature intimate settings and couple-oriented activities.

#### `SENIORS`

**Seniors**  
Cruises tailored for older adults, with consideration for comfort, accessibility, and pace. Features more relaxed schedules and activities suitable for senior participants.

#### `WOMEN_ONLY`

**Women Only**  
Cruises exclusively for women, creating a supportive and comfortable environment for female sailors. May focus on empowerment, skill-building, and community among women sailors.

#### `MEN_ONLY`

**Men Only**  
Cruises exclusively for men, providing a space for male bonding and sailing adventures.

### Activity & Theme Based

#### `PARTY`

**Party Cruises**  
Social cruises with emphasis on entertainment, music, and celebration. Features lively atmosphere, social events, and nightlife activities during port stops.

#### `RELAX`

**Relaxation**  
Peaceful, stress-free cruises focused on rest and rejuvenation. Features calm sailing conditions, comfortable pace, and opportunities for meditation and unwinding.

#### `SURVIVAL`

**Survival Training**  
Specialized cruises focusing on survival skills, emergency procedures, and handling challenging situations at sea. Includes training in safety equipment, emergency navigation, and crisis management.

#### `PHOTOGRAPHY`

**Photography**  
Cruises designed for photography enthusiasts, featuring scenic routes, optimal lighting conditions, and stops at photogenic locations. May include photography workshops or guidance.

#### `CULINARY`

**Culinary Experiences**  
Cruises with emphasis on gastronomy, featuring local cuisine, cooking workshops, or gourmet dining experiences. May include visits to local markets and culinary cultural sites.

#### `CULTURAL_HISTORICAL`

**Cultural & Historical**  
Educational cruises focusing on the cultural and historical aspects of the sailing region. Includes visits to historical sites, museums, and cultural landmarks along the route.

#### `EXPLORATION`

**Exploration**  
Adventure-focused cruises aimed at discovering new places, remote locations, or off-the-beaten-path destinations. Emphasizes the spirit of discovery and adventure sailing.

## Quick Reference Table

| Category         | Values                                                                                        |
| ---------------- | --------------------------------------------------------------------------------------------- |
| **Skill Level**  | `BEGINNER_INTRO`, `TRAINING`, `MILEBUILDING`, `ADVANCED`, `SPORT_REGATTA`                     |
| **Demographics** | `FAMILY`, `SINGLES`, `COUPLES`, `SENIORS`, `WOMEN_ONLY`, `MEN_ONLY`                           |
| **Activity**     | `PARTY`, `RELAX`, `SURVIVAL`, `PHOTOGRAPHY`, `CULINARY`, `CULTURAL_HISTORICAL`, `EXPLORATION` |

## Usage Notes

- Each cruise must have exactly one type assigned
- The type field is nullable in the database, allowing cruises without a specified type during transition periods
- Cruise organizers should select the type that best represents the primary focus of their cruise
- When multiple types could apply, choose the most dominant characteristic

## Related Fields

The cruise type works in conjunction with other cruise restriction fields:

- `smokingAllowed` — Whether smoking is permitted on the cruise
- `alcoholAllowed` — Whether alcohol consumption is permitted
- `petsAllowed` — Whether pets are allowed on board
- `childrenAllowed` — Whether children are allowed to participate

These fields help participants understand the cruise rules and ensure a good fit for their preferences and needs.

## Related

- [Cruises API](../../cruises/index.md) — Full cruise documentation
- [Vessel Types](./vessel-types.md) — Supported vessel types
