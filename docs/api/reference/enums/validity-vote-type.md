# Validity Vote Type

The `ValidityVoteType` enum defines the types of validity votes that can be cast on time-sensitive posts.

## Values

| Value            | Description                                        |
| ---------------- | -------------------------------------------------- |
| `confirm`        | Confirms that the information is still accurate    |
| `report_invalid` | Reports that the information is no longer accurate |

## Usage

Validity votes are used for community verification of time-sensitive posts:

- `berth` — Available berth offers
- `weather` — Weather alerts
- `navigation_warning` — Navigation hazards

## API Example

### Request

```http
PUT /v1/posts/{postId}/validity-vote
Content-Type: application/json

{
  "voteType": "confirm"
}
```

### Response

```json
{
  "postId": "018fa2e4-8e3b-7b2e-8e3b-7b2e8e3b7b99",
  "voteType": "confirm",
  "confirmCount": 5,
  "invalidCount": 2
}
```

## Vote Summary

The post response includes a `validityVotes` object for votable types:

```json
{
  "validityVotes": {
    "confirmCount": 5,
    "invalidCount": 2,
    "userVote": "confirm"
  }
}
```

| Field          | Type           | Description                                    |
| -------------- | -------------- | ---------------------------------------------- |
| `confirmCount` | integer        | Number of confirm votes                        |
| `invalidCount` | integer        | Number of report_invalid votes                 |
| `userVote`     | string \| null | Current user's vote type, or null if not voted |

## Auto-Resolution

When a post receives **3 or more `report_invalid` votes**, it is automatically transitioned to `resolved` status. This community-driven mechanism helps maintain accurate information on the platform.

## Vote Immutability

Once a vote is cast, it cannot be changed:

- Casting the same vote type again returns **200 OK** (idempotent)
- Trying to change to a different vote type returns **409 Conflict**

## Related

- [Posts](../../posts/index.md) — Post API documentation
- [Post Content Keys](post-types.md) — Alert posts and content-based filtering
- [Post Status](post-statuses.md) — Post lifecycle states
