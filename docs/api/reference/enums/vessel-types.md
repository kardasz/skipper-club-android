# Vessel Types

This document describes all available vessel type enum values that can be assigned to cruises in the system.

## Overview

The `VesselType` enum defines the types of vessels that can be used for cruises. Each cruise specifies one vessel type to help participants understand what kind of boat they'll be sailing on.

## Available Vessel Types

### `SAILING_YACHT`

**Traditional Sailing Yacht**  
A monohull sailing yacht powered primarily by sails. The most common type of vessel for recreational sailing. Offers a classic sailing experience with good performance and seaworthiness. Typical sizes range from 30 to 60 feet.

### `CATAMARAN`

**Catamaran**  
A twin-hull sailing vessel known for stability and spacious decks. Popular for charter cruises due to comfortable accommodations and minimal heeling. Provides more living space than monohulls of similar length. Great for families and those prone to seasickness.

### `MOTORBOAT`

**Motorboat**  
A motor-powered vessel without sails. Used for cruises where engine power is preferred over sailing. Offers faster travel times and independence from wind conditions. Suitable for day trips or longer coastal cruises.

### `TRIMARAN`

**Trimaran**  
A three-hull sailing vessel combining speed with stability. Features a main hull with two smaller outrigger hulls. Known for excellent performance and reduced heeling compared to monohulls. Less common but offers a unique sailing experience.

### `GULET`

**Gulet**  
A traditional wooden sailing vessel, originating from Turkey. Typically features a wide stern and can accommodate larger groups. Popular for luxury cruises in the Mediterranean, especially along the Turkish and Croatian coasts. Often crewed with chef and captain included.

### `SCHOONER`

**Schooner**  
A multi-masted sailing vessel, typically with two or more masts. Rigged for efficient sailing with fore-and-aft sails. Often used for tall ship experiences and traditional sailing. Provides a classic, nostalgic sailing atmosphere.

## Quick Reference Table

| Type            | Description               | Best For                       |
| --------------- | ------------------------- | ------------------------------ |
| `SAILING_YACHT` | Traditional monohull      | Classic sailing experience     |
| `CATAMARAN`     | Twin-hull stability       | Families, comfort cruises      |
| `MOTORBOAT`     | Engine-powered            | Speed, wind-independent travel |
| `TRIMARAN`      | Three-hull performance    | Speed enthusiasts              |
| `GULET`         | Traditional wooden vessel | Luxury group cruises           |
| `SCHOONER`      | Multi-masted tall ship    | Classic tall ship experience   |

## Usage Notes

- The `vesselType` field is required when creating a cruise
- Vessel type is used for filtering cruises in search
- Additional vessel details can be specified:
  - `vessel` — Name/description of the specific vessel
  - `vesselBrand` — Manufacturer (e.g., Bavaria, Beneteau)
  - `vesselModel` — Specific model (e.g., Cruiser 46)
  - `vesselYear` — Year built (1950-2030)
  - `vesselLength` — Length in feet (15-200)
  - `vesselCabins` — Number of cabins (1-20)

## Related

- [Cruises API](../../cruises/index.md) — Full cruise documentation
- [Cruise Types](./cruise-types.md) — Cruise category types
