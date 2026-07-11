# Reaction Type

Enum defining the available emoji reactions for posts.

## Overview

Users can react to posts with a curated set of 20 emoji reactions - 10 standard reactions for general expression and 10 sailing-themed reactions unique to SkipperClub.

## Values

### Standard Reactions

| Value         | Emoji | Description          |
| ------------- | ----- | -------------------- |
| `heart`       | ❤️    | Love / Like          |
| `thumbs_up`   | 👍    | Approval             |
| `thumbs_down` | 👎    | Disapproval          |
| `laugh`       | 😂    | Funny                |
| `wow`         | 😮    | Surprised / Amazed   |
| `sad`         | 😢    | Sad                  |
| `fire`        | 🔥    | Hot / Amazing        |
| `clap`        | 👏    | Applause             |
| `party`       | 🎉    | Celebration          |
| `thinking`    | 🤔    | Thoughtful / Curious |

### Sailing Reactions

| Value       | Emoji | Description        |
| ----------- | ----- | ------------------ |
| `anchor`    | ⚓    | Anchored / Secured |
| `sailboat`  | ⛵    | Sailing            |
| `wave`      | 🌊    | Ocean / Wave       |
| `sun`       | ☀️    | Sunny weather      |
| `compass`   | 🧭    | Navigation         |
| `fish`      | 🐟    | Fish / Fishing     |
| `whale`     | 🐋    | Whale sighting     |
| `dolphin`   | 🐬    | Dolphin sighting   |
| `wind`      | 💨    | Windy conditions   |
| `lifesaver` | 🛟    | Safety / Help      |

## API Usage

### Add Reaction

```http
PUT /posts/{postId}/reactions/{reactionType}
```

Add an emoji reaction to a post. The operation is idempotent - adding the same reaction twice returns 200 OK.

**Example:**

```http
PUT /v1/posts/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99/reactions/heart
Authorization: Bearer <token>
```

**Response (200 OK):**

```json
{
  "total": 15,
  "byType": {
    "heart": 10,
    "anchor": 3,
    "sailboat": 2
  },
  "userReactions": ["heart"]
}
```

### Remove Reaction

```http
DELETE /posts/{postId}/reactions/{reactionType}
```

Remove an emoji reaction from a post. The operation is idempotent - removing a non-existent reaction returns 204 No Content.

**Example:**

```http
DELETE /v1/posts/018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99/reactions/heart
Authorization: Bearer <token>
```

**Response:** 204 No Content

## Response Format

### ReactionSummary

The reaction summary is included in post responses and returned when adding reactions:

```typescript
interface ReactionSummary {
  total: number; // Total count of all reactions
  byType: Partial<Record<ReactionType, number>>; // Count per reaction type
  userReactions: ReactionType[]; // Current user's reactions
}
```

**Notes:**

- `byType` only includes reaction types with count > 0 (empty types are omitted)
- Order of keys in `byType` is not guaranteed
- Users can add multiple different reaction types to the same post

## Restrictions

Per the D4 design decision, reactions follow strict access rules:

- **Allowed:** Only on `published` posts that are not effectively expired
- **Not Allowed:** On `archived`, `expired`, `resolved`, `deleted`, or effectively expired posts
- **No Author Exception:** Even the post author cannot react to their own non-published posts

All non-accessible posts return 404 for reaction operations.

## Validation

Invalid reaction types are rejected by generated parameter binding/feature validation and return a 422 ValidationProblem:

```json
{
  "type": "/errors/validation",
  "title": "Validation Error",
  "status": 422,
  "detail": "Validation failed",
  "errors": [
    {
      "property": "reactionType",
      "constraints": {
        "isEnum": "reactionType must be a valid enum value"
      }
    }
  ]
}
```

## Related

- [Posts Documentation](../../posts/index.md)
- [Post Types](./post-types.md)
- [Post Statuses](./post-statuses.md)
