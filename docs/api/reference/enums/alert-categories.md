# Alert Categories

Alert categories are still shared by imported source alerts and public alert
posts.

Values:

- `navigation_warning`
- `navtex`
- `notice_to_mariners`
- `obstruction`
- `works`
- `regatta`
- `diving`
- `military_exercise`
- `weather`
- `other`

Users set the category inside `content.alert` when creating an alert post:

```json
{
  "content": {
    "text": "Works near the harbor entrance.",
    "alert": { "category": "works", "severity": "warning" }
  },
  "location": {
    "point": { "lat": 43.5081, "lng": 16.4402 }
  }
}
```

Official imports may additionally populate source-only alert fields in the
generated post content.
