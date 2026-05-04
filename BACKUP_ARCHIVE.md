# PocketWeibo backup formats

This document describes how local data is exported and imported so you can extend it (e.g. audience / visibility) without breaking older backups.

## JSON (`exportAllData` / `importData`)

- **File:** single UTF-8 JSON object.
- **`version`:** `2` for current exports (older backups may show `1`; import remains compatible).
- **Top-level keys:** `identities`, `posts`, `comments`, `exportedAt`, `version`.
- **Posts:**
  - `content` — text body (may be empty if the post is image-only).
  - `imageUris` — string: either a **JSON array** of paths relative to the app’s `filesDir`, e.g. `["post_attachments/12/0.jpg"]`, or legacy comma-separated values (older experiments). At publish time, images over **~1.5 MB** may be re-encoded to JPEG (longest edge up to 2048 px, output typically capped around **~2 MB**) unless **Compose → Original files for this post / 本帖原图** was on when those images were added; exports then contain whatever was stored.
  - `extrasJson` — string, JSON object, default `{}`. Reserved for future fields (e.g. audience). Older backups may include a legacy `location` key; the current app no longer writes it. Importers should preserve unknown keys if merging.

JSON alone does **not** embed binary image data. Paths in `imageUris` only resolve on the same device (or after a ZIP restore, see below).

## ZIP (full backup, recommended when posts have images)

- **Layout:**
  - `data.json` — same schema as JSON export (`version` 2).
  - `post_attachments/<postId>/<filename>` — image files matching paths referenced in each post’s `imageUris` array.
- **Import:**
  - **Replace existing data:** clears the database and `post_attachments/`, extracts ZIP, then imports `data.json` with original ids.
  - **Merge:** unpacks to a temporary folder, imports rows with new ids, and **copies** images from the temp tree into new `post_attachments/<newPostId>/…` paths.

## Markdown export

Human-readable only; includes an **attached image count** line when a post has stored paths. Does not include image files.

## Upgrade notes

- Room **v4** adds `posts.extrasJson` (default `'{}'`). Existing rows keep prior `imageUris` values.
- New posts store images under `filesDir/post_attachments/`.
