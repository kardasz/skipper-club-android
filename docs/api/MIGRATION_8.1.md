# API Migration Guide — v8.1.0 (Author-less Alert Posts)

This document describes the **API contract changes** introduced in **API
version 8.1.0**, which makes posts projected from imported alerts **author-less**
and removes the synthetic "SkipperClub Alerts" system user. It is written for the
mobile application team migrating the app to the new API. See the
[CHANGELOG](CHANGELOG.md#810---2026-07-07) entry for `8.1.0` for the high-level
summary.

> **Scope & compatibility.** `8.1.0` is a small but **breaking** change to a
> single field shape: a post's `user` (and a map post item's `author`) can now be
> `null`. Everything else in the `8.0.0` contract is unchanged. There is a
> forward-only database migration; no data backfill is required.

---

## 1. TL;DR — What changed

| Area                | Change                                                                                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Post author**     | `user` on a post is now **nullable**. It is `null` on system-generated posts (imported alerts).                                                                                         |
| **Map author**      | `author` on a `post` map item is now **nullable**. It is `null` for the same system-generated posts.                                                                                    |
| **System user**     | The seeded "SkipperClub Alerts" user (`019dfd19-0000-7000-8000-000000000001`) is **removed** — it is gone from search, tagging, and friend requests, and no longer authors alert posts. |
| **Everything else** | No other fields, endpoints, or query parameters changed.                                                                                                                                |

---

## 2. Why this changed

Imported official alerts are synced into public **posts**. In `8.0.0` those posts
were authored by a seeded "system user" that only existed in environments where
the CLI seeder had run. In production the seeder never runs, so the alert-to-post
sync failed on insert with a foreign-key violation
(`posts.user_id -> users.id`), and imported alerts never reached the feed or map.

Modelling these posts as **author-less** (`posts.user_id = NULL`) removes the
dependency on a synthetic account entirely, so the sync works in every
environment.

---

## 3. Posts — `user` is now nullable

`GET /posts`, `GET /posts/{id}`, and every endpoint returning a post now emit
`user: null` on system-generated posts (those carrying a `source` object, i.e.
imported alerts). User-created posts — including user-created alert posts — still
carry a full `user` object.

```jsonc
{
  "id": "019dfd19-...",
  "user": null, // ← system-generated (imported alert): no author
  "contentKeys": ["alert"],
  "status": "published",
  "content": {
    "text": "Radio navigational warning ...",
    "alert": { "category": "navigation_warning", "severity": "warning" },
  },
  "source": { "type": "alert", "id": "..." }, // present only on system posts
  // ... all other fields unchanged
}
```

How to tell an author-less post apart (any one of these is sufficient):

- `user === null`, or
- `source` is present (`source.type === "alert"` for imported alerts).

Client guidance:

- **Do not** assume `post.user` is non-null. Guard every `post.user.id` /
  `post.user.name` / `post.user.avatarUrl` access.
- Render system posts with a system/brand label (e.g. an "Official alert" or
  "SkipperClub Alerts" chip driven client-side) instead of an author profile
  link. There is no user profile to navigate to.
- `permissions.edit/delete/archive/resolve` remain `false` on these posts (as in
  `8.0.0`), so no author-only affordances should be shown regardless.

---

## 4. Map — `author` is now nullable

`GET /map/items` post items now emit `attributes.author: null` for the same
system-generated posts.

```jsonc
{
  "type": "post",
  "attributes": {
    "contentKeys": ["alert"],
    "alertCategory": "navigation_warning",
    "status": "published",
    "author": null, // ← system-generated (imported alert): no author
    "publishedAt": "2026-06-17T08:05:00.000Z",
    // ... all other fields unchanged
  },
}
```

Guard every `attributes.author.*` access the same way as `post.user`.

---

## 5. Removed system user

The reserved "SkipperClub Alerts" account
(`019dfd19-0000-7000-8000-000000000001`) no longer exists. Practical effects for
the app:

- It will never appear in `GET /users` search results.
- Tagging it (`taggedUserIds`) or sending it a friend request now returns the
  ordinary `404` (`/errors/tagged-users-not-found` /
  `/errors/target-user-not-found`) for a non-existent user — there is no longer a
  special reserved id to avoid.
- No client should hardcode or special-case that UUID.

---

## 6. Migration checklist (mobile)

- [ ] Treat `post.user` as **nullable** everywhere; guard all author field
      accesses.
- [ ] Treat map `attributes.author` as **nullable** on post items; guard all
      accesses.
- [ ] Render system/alert posts (`user === null` / `source` present) with a
      system label instead of an author profile link.
- [ ] Remove any hardcoded reference to the "SkipperClub Alerts" user id.

---

## 7. Backend / operations note

This release adds a forward-only migration that drops `NOT NULL` on
`posts.user_id`:

- `MakePostAuthorNullable` — `ALTER TABLE "posts" ALTER COLUMN "user_id" DROP NOT NULL`.

Run `npm run db:migration:run:prod` on deploy. No data backfill is needed; the
foreign key stays in place (a `NULL` value simply skips the FK check).

---

_Source of truth: `docs/openapi.yaml` on this branch. Diff it against `main` for
exact field-level detail._
