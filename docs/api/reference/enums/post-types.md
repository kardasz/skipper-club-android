# Post Content Keys

`PostType` has been removed. Posts are classified by their structured
`content` object and derived `contentKeys` array.

Current filter keys:

| Key     | Meaning                                      |
| ------- | -------------------------------------------- |
| `alert` | `content.alert` is present                   |
| `route` | `content.route` is present                   |
| `media` | At least one `post_media` row is attached    |
| `note`  | Special filter value for empty `contentKeys` |

Use:

- feed: `GET /v1/posts?contains=alert,media`
- map: `GET /v1/map/items?postContains=alert`

See [Post Content Object Registry](../../posts/content-objects.md).
