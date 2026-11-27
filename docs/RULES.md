# PocketWeibo — project rules for features, fixes, and releases

These rules apply whenever you ship a **new feature** or a **fix**. Keep the app **bilingual** (Chinese default + English) and keep **versioning** aligned with the kind of change.

---

## 0. Workflow for every request (fix, feature, or both)

Treat **each** user request as one delivery unit. **Always** follow this sequence end to end; do not skip verification or logging.

| Step | What to do |
|------|------------|
| **1. Plan** | Restate the goal, scope (files/areas), and risks. For multi-step work, use a **todo list** in the editor and keep it updated. |
| **2. Implement** | Make the smallest change that satisfies the request; match existing patterns (`RULES.md`, nearby code). |
| **3. Verify** | Run **`./gradlew test lint assembleDebug`** — the same command CI runs (and `assembleRelease` when signing/release behavior changes). **Fix failures before you commit.** A passing build is required to treat the request as done. |
| **4. Record** | Update **`FEATURE.md`** for user-facing product changes and **`FIX.md`** for notable fixes, per the tables/workflows in those files. Bump **`versionName` / `versionCode`** in `app/build.gradle.kts` when shipping (see §2). |
| **5. Commit** | Work on a short-lived branch (`feat/…`, `fix/…`, `chore/…`, `docs/…`) and merge to `main` through a PR. Create **one git commit per user request** (one fix, one feature, or one combined fix+feature if the user asked for both together). Do **not** mix unrelated requests into the same commit. The commit should reflect a **green** verify step. |
| **6. Todos** | Mark planning/todo items **completed** (or cancelled if dropped) before ending the session so the next session sees an accurate state. |

**Mixed fix + feature:** If a single request includes both, one commit covering that request is correct. If the user sends **separate** requests, use **separate** commits (each passing tests).

When recording, always add the **commit hash and date**: FEATURE.md status `✅ Done · \`<hash>\` <YYYY-MM-DD>`, FIX.md **Commit** column, REVIEW.md status column.

---

## 1. Localized app (required)

PocketWeibo ships in **three languages**: default locale strings live in `app/src/main/res/values/strings.xml` (Simplified Chinese), with `values-en/strings.xml` (English) and `values-zh-rTW/strings.xml` (Traditional Chinese). Per-app language is controlled in settings (`UiPreferences` / `AppCompatDelegate.setApplicationLocales`) with codes `system`, `zh`, `zh-TW` and `en`.

**For every user-visible change:**

- Add or update the **same string keys** in **all three** `values/` directories (`strings.xml`, and `plurals.xml` where relevant). Do not leave a key missing from any locale used by the UI.
- Prefer `stringResource(R.string.*)` in Compose and `context.getString(R.string.*)` where a `Context` is available.
- Covers **all** UI chrome: titles, tabs, placeholders, content descriptions, toasts, dialogs, empty states, relative times, clipboard labels, markdown export headings/labels, etc. (Seed/demo **data** in `DataSeeder.kt` does not need translation.)

**Parity check:** any `R.string.*` referenced in code must exist in **every** locale resource directory before you merge. `StringParityTest` enforces matching string / plurals / string-array keys across all three locales in `./gradlew test`.

---

## 2. Version bumps (`app/build.gradle.kts`)

Semantic **`versionName`** is `MAJOR.MINOR.PATCH`. **`versionCode`** is an integer that **must increase** for every Play/CI APK that should upgrade in place over the previous build (same signing key).

| Change type | Bump `versionName` | Bump `versionCode` |
|-------------|--------------------|--------------------|
| **New user-facing feature** (new behavior, new screen area, new setting, i18n coverage for a whole flow, etc.) | **MINOR** (e.g. `3.1.0` → `3.2.0`) | **+1** (e.g. `101` → `102`) |
| **Patch / fix** (bugfix, regression, typo, small correction; no new product capability) | **PATCH** (e.g. `3.1.0` → `3.1.1`) | **+1** |
| **Breaking / major product change** (rare; coordinated release) | **MAJOR** | **+1** |

**Rule of thumb:** if you would describe it in release notes as a **feature**, bump **minor**. If you would describe it as a **fix**, bump **patch**. When unsure and the change is mixed, prefer **minor** if any new user-visible capability ships.

Update only `app/build.gradle.kts` (`versionName` / `versionCode`) unless another file documents the same numbers.

---

## 3. Before you finish (checklist)

Aligns with **§0**; use this as a quick pass before commit:

1. Strings: **zh + en** parity for all new/changed UI.
2. Version: bump **minor** or **patch** (and **versionCode**) per section 2.
3. Build: **`./gradlew test lint assembleDebug`** green (and `assembleRelease` when touching release/signing behavior).
4. Product log: **`FEATURE.md`** and/or **`FIX.md`** updated when the change is user-visible or notable.
5. **Git:** one commit per request, after tests pass.

---

## 4. Documentation map (`docs/`)

Only these documents are kept; add new material to the matching one instead of creating a new file.

| File | Owns |
|---|---|
| `RULES.md` | Workflow, bilingual rules, versioning, **backup format contract** (§5) |
| `FEATURE.md` | Shipped feature ledger (F-xxx, with commit + date), F-010–F-020 design notes, **proposed backlog** |
| `FIX.md` | Fix ledger (with commit), release / CI / signing notes |
| `REVIEW.md` | Consolidated engineering review: all review passes, bug register (BUG-xxx), decisions, remaining roadmap |
| `screenshots/` | README images |

---

## 5. Backup format contract

Every new entity column must be written by **both** exporters and read by `postFromJson` / `identityFromJson` / `commentFromJson` (BUG-001 was a missing column). Bump the export `version` and keep new fields **optional** on import so older files still load. `WeiboRepositoryDataTest` covers the round-trip.

### Full JSON export (`exportAllData` / `importData`)

- Single UTF-8 JSON object. **`version`: `5`** (since 3.75.1; `1`–`4` still import).
- Top-level keys: `identities`, `posts`, `comments`, `postReminders`, `identitySchedules`, `postReminderFirings`, `topicAliases`, `topicExclusions`, `exportedAt`, `version`.
- **Posts:** `id`, `identityId` (nullable), `content` (may be empty for image-only posts), `imageUris`, `audioPath`, `extrasJson`, `createdAt`, `likeCount`, `commentCount`, `isLiked`, **`isPinned`** (since v4; missing → `false`), `todoDoneAt` (epoch millis or `null`; older files may omit it).
  - `imageUris` — JSON array of paths relative to `filesDir`, e.g. `["post_attachments/12/0.jpg"]`, or legacy comma-separated values. Images over ~1.5 MB may have been re-encoded to JPEG at publish time unless **本帖原图 / Original files for this post** was on.
  - `extrasJson` — JSON object string, default `{}`, reserved for future fields. Older backups may carry a legacy `location` key (no longer written). Importers should preserve unknown keys when merging.
- **Identities:** `id`, `name`, `avatarResName`, `customAvatarUri` (optional), `nationality`, `gender`, `birthYear` / `deathYear` (nullable; negative = BC), `occupation`, `motto`, `famousWork`, `bio`, `isActive`.
- **Comments:** `id`, `postId`, `identityId`, `content`, `audioPath`, `createdAt`, `replyingToCommentId`, `likeCount`, `likedBy` (comma-terminated identity ids, e.g. `"1,11,"`), `hiddenAt` (0 = visible).
- **`postReminders`** (since v5): `id`, `postId`, `fireAtMillis`, `repeatRule`. On import, past one-shot reminders are dropped, repeating ones roll forward, at most one per post, and alarms are re-armed. Calendar event ids are not exported; calendar sync re-creates events.
- **`identitySchedules`** (since v5): `id`, `identityId`, `hour`, `minute`, `daysOfWeek`, `enabled`. Duplicates (same identity, time and days) are skipped.
- **`postReminderFirings`:** delivered-notification history (`postId`, `reminderId` nullable, `scheduledForMillis`, `firedAtMillis`, `repeatRule`) used by the TODO list.
- **`topicAliases`** (`fromKey`, `toKey`) and **`topicExclusions`** (`topicKey`, `postId`), keys lower-cased.
- JSON alone contains no binary media; paths only resolve on the same device or after a ZIP restore.

### Automatic text backup (`AutoDailyBackup`)

- Text-only JSON, `kind: "pocket_weibo_text_auto_backup"`, **`version`: `4`** (since 3.75.1; `1`–`3` still import).
- Keys: `kind`, `exportedAt`, `version`, `identities` (no `customAvatarUri`), `posts` (empty `imageUris`), `comments`, `postReminders`, `identitySchedules` (since v4), `postReminderFirings`, `topicAliases`, `topicExclusions`.
- Encrypted with AES-256-GCM (`PWB1` header) under `filesDir/auto_text_backups/`, one file per day, newest **3** days kept. The key is derived from `ANDROID_ID` + package name, so files only decrypt on the same device and signing key; with no `ANDROID_ID` the backup is skipped (fail closed). Key strategy is an open decision — see REVIEW.md §5.

### ZIP (full backup, recommended when posts have media)

- `data.json` (full JSON above) plus `post_attachments/<postId>/…` (images and voice), `comment_attachments/<commentId>/…` (voice) and `identity_avatars/<identityId>.jpg`.
- **Import (both modes):** the ZIP is streamed into a private staging dir under `filesDir` (`ZipSafety`: entries escaping the root are skipped; caps 512 MB per entry / 2 GB total) and `data.json` is parsed before anything changes.
  - **Replace:** all row deletes + inserts run in one Room transaction; only after commit are the live media trees swapped for the staged ones (rolled back on failure), old alarms / calendar events cancelled and alarms re-armed.
  - **Merge:** rows get new ids; media are copied from staging into the new per-row folders.
- The export ZIP is written to `cache/shared/` for the share sheet; stale files are removed by `CacheSweeper` after 24 h.

### Timer-only restore

Settings → **从备份恢复提醒与定时 / Restore reminders & schedules** reads a ZIP, JSON or `.pwb` auto-backup and adds only `postReminders` and `identitySchedules`. Posts are matched by (`createdAt`, `content`), identities by `name`; nothing else changes and re-running it adds no duplicates.

### Markdown export

Human-readable only, with an attached-image count per post; no media files.

### Schema notes

- Room **v4** added `posts.extrasJson` (default `'{}'`).
- Room **v15** added nullable `posts.todoDoneAt` and the `post_reminder_firings` table.
- Room **v16** added `topic_aliases` / `topic_exclusions`.
- New media is always stored under `filesDir/post_attachments/`, `comment_attachments/`, `identity_avatars/`.

---

## 6. Where this is enforced for Cursor

`.cursor/rules/pocket-weibo-shipping.mdc` is set to **always apply** so agents load these expectations on new features and fixes. This file remains the full reference.
