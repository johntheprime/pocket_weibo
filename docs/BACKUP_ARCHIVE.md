# PocketWeibo backup formats

This document describes how local data is exported and imported so you can extend it (e.g. audience / visibility) without breaking older backups.

## JSON (`exportAllData` / `importData`)

- **File:** single UTF-8 JSON object.
- **`version`:** `3` for current full exports (older backups may show `1` or `2`; import remains compatible). Automatic text backups use their own `version: 2`.
- **Top-level keys:** `identities`, `posts`, `comments`, `postReminderFirings`, `exportedAt`, `version`.
- **Posts:**
  - `content` — text body (may be empty if the post is image-only).
  - `imageUris` — string: either a **JSON array** of paths relative to the app’s `filesDir`, e.g. `["post_attachments/12/0.jpg"]`, or legacy comma-separated values (older experiments). At publish time, images over **~1.5 MB** may be re-encoded to JPEG (longest edge up to 2048 px, output typically capped around **~2 MB**) unless **Compose → Original files for this post / 本帖原图** was on when those images were added; exports then contain whatever was stored.
  - `extrasJson` — string, JSON object, default `{}`. Reserved for future fields (e.g. audience). Older backups may include a legacy `location` key; the current app no longer writes it. Importers should preserve unknown keys if merging.
  - `todoDoneAt` — epoch millis when a TODO post was marked done, or `null` when unfinished. Older backups may omit it.
- **Reminder firings:** `postReminderFirings` is an array of delivered-notification records with `postId`, `reminderId` (nullable when the one-shot reminder row is gone), `scheduledForMillis`, `firedAtMillis`, and `repeatRule`. It is the durable history used by the TODO list, including old firings of repeated/postponed reminders.

JSON alone does **not** embed binary image data. Paths in `imageUris` only resolve on the same device (or after a ZIP restore, see below).

## Automatic text backup

- Text-only JSON written by `AutoDailyBackup`; no binary media is included.
- **`version`:** `2` (older `version: 1` backups still import).
- **Top-level keys:** `kind`, `exportedAt`, `version`, `identities`, `posts`, `comments`, `postReminders`, `postReminderFirings`.
- Posts use the same `todoDoneAt` field as full exports; image paths are intentionally empty.

## ZIP (full backup, recommended when posts have images)

- **Layout:**
  - `data.json` — same schema as JSON export (`version` 3).
  - `post_attachments/<postId>/<filename>` — image files matching paths referenced in each post’s `imageUris` array.
- **Import:**
  - **Replace existing data:** clears the database and `post_attachments/`, extracts ZIP, then imports `data.json` with original ids.
  - **Merge:** unpacks to a temporary folder, imports rows with new ids, and **copies** images from the temp tree into new `post_attachments/<newPostId>/…` paths.

## Markdown export

Human-readable only; includes an **attached image count** line when a post has stored paths. Does not include image files.

## Upgrade notes

- Room **v4** adds `posts.extrasJson` (default `'{}'`). Existing rows keep prior `imageUris` values.
- Room **v15** adds nullable `posts.todoDoneAt` and the `post_reminder_firings` history table; existing TODO state starts unfinished and reminder history starts empty.
- New posts store images under `filesDir/post_attachments/`.
