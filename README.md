# PocketWeibo

**English** | [简体中文](README.zh-CN.md)

A local-first, Weibo-style notes app for Android. Write short posts as historical figures, attach
images, comment (with text or voice), set system reminders, and browse everything offline.

Everything is stored on the device: **no account, no backend, no network API**. The app is bilingual
(Simplified Chinese by default, English available in Settings) and supports light/dark themes.

## Highlights

### Identities

- Multiple identities (historical figures) with name, nationality/region, gender, birth/death years,
  occupation, motto, famous work, and bio.
- Exactly one **active identity** at a time; switch from the identity list (Settings → Identity
  management), by selecting an identity in the Home filter sheet, or automatically with a schedule.
- 8 built-in hand-drawn preset avatars (Open Peeps, 4 male + 4 female) plus custom avatars picked
  from the gallery; custom avatars can be removed to return to a preset.
- **Scheduled identity switching**: add rules (identity + time + optional repeat days) and
  `AlarmManager` switches the active identity automatically (every day / workdays / weekends /
  custom days).
- Deleting an identity keeps its posts and comments. They show the author as **deleted account**;
  if the deleted identity was active, the oldest remaining identity becomes active.

### Posts

- Text posts up to **2,000 characters**; draft text is saved and restored between composer sessions.
- Up to **9 images** per post, from the gallery or camera. Images over ~1.5 MB are re-encoded to
  high-quality JPEG (longest edge 2560 px, quality 92); GIFs and smaller files are kept as-is.
  A per-post **original / no compression** switch is available when images are attached.
- Image handling runs in the background before sending; duplicate sends are blocked while preparing
  or sending.
- **Pin posts** from the detail menu; pinned posts sort to the top of the feed.
- Long-press a post to select and copy its body. Relative timestamps ("3 hours ago") can be tapped
  to toggle the full date/time (including weekday).
- Feed cards show a compact **image** badge instead of thumbnails; open the detail page for the full
  gallery.

### Comments

- Comment on posts with text or a **voice recording** (send voice only, or text + voice).
- Comments are listed **newest first**, and the list scrolls to the newest comment after sending.
- Long-press a comment to copy its body; the detail page can **copy all comments** as numbered plain
  text (oldest first, without authors or timestamps).
- Delete your own comments within **48 hours** of posting (from the home comment sheet). Older
  comments no longer show a delete action.
- Comment voice clips play inline; the recording banner can be tapped to stop and send immediately.
- Discover returns matching comments; Home search surfaces posts whose comments match.

### Reminders

- Attach a **system alarm reminder** to any post from the post detail menu, the clock button on a
  feed card, or the compose screen's reminder tool (choosing a time publishes the post immediately).
- Quick presets: 15/30 minutes, 1/3/6/10 hours, today 20:00, tomorrow 9:00 (the most-used three are
  reordered first), plus a custom date/time picker.
- Repeat rules: once, daily, workdays, weekly, monthly.
- Uses `AlarmManager.setAlarmClock` with notifications; reminders are restored after boot or app
  update and overdue reminders are delivered on next launch.
- **Shake to remind**: shake the post detail screen to schedule a reminder for that post. Settings
  let you choose the evening time (default 20:00) and next-morning fallback (default 09:00).
- **Pending reminders** are listed on the Me page, with countdown refresh and cancel/open actions.
  Feed cards show an orange reminder badge, and the detail page shows the next fire time and repeat
  rule.
- Optional **system calendar sync**: mirror reminders into the device calendar (with `RRULE` for
  repeats) so the calendar can notify even when PocketWeibo is not running.

### Home, Photo, Discover, Me

- **Home**: reverse-chronological feed (pinned first), pull-to-refresh affordance, search dialog, and
  a title sheet that filters the feed by identity. Double-tap the Home tab to scroll to the newest
  post and clear filters.
- **Photo**: an image-first feed containing only posts with images. Images use their real aspect
  ratio, support pinch zoom (1×–4×), panning, and double-tap zoom (about 2× at the touch point);
  tapping an image toggles the author/time/text overlay.
- **Discover**: trending identities and posts, plus search across identities, post bodies/authors,
  and comments (results open the owning post). Multiple keywords can be separated with `|` (or the
  full-width vertical bar); any keyword may match, case-insensitively.
- **Me**: current identity card, identity details, and pending reminders (hidden when there are
  none). The settings icon opens the settings hub.

### Compose and sharing

- Dedicated composer with identity picker, character counter, image/camera tools, per-post original
  switch, reminder tool, and draft restore.
- The composer auto-focuses the text field and opens the keyboard.
- **Shake to send** or **double-tap the blank area below the editor** to publish.
- Accepts `ACTION_SEND` (`text/plain`) from other apps, prefilled as a new post.
- Long-pressing the launcher icon exposes a **New post** app shortcut (Android 7.1+).
- Android-style swipe-back navigation is supported on secondary screens.

### Backup and data

- Export to:
  - **JSON** — importable text backup (no binary media),
  - **ZIP** — `data.json` plus post images, comment voice clips, and custom avatars (recommended
    full backup),
  - **Markdown** — human-readable export for reading/archiving.
- Import JSON text or a ZIP backup, choosing **merge** or **replace** existing data.
- **Automatic daily text backup**: on the first launch of each local calendar day, a text-only JSON
  snapshot is written to app-private storage, encrypted with AES-GCM using a key derived from the
  device Android ID + package name. The latest **3 calendar days** are retained. Settings can
  decrypt and share a day's `.pwb` file, or decrypt a `.pwb` selected from storage.
- Settings also includes a **diagnostic log** capture/export toggle for troubleshooting reminders.

### Settings

The settings hub (Me → gear icon) contains:

1. Identity management
2. Scheduled identity switching
3. Export data
4. Import data
5. Interface language (system / Simplified Chinese / English)
6. Shake-to-remind evening and morning times
7. System calendar reminder sync
8. Automatic daily text backup
9. Diagnostic log capture / export / clear
10. About (version, description, GitHub links)

### Not included

- No accounts, cloud sync, or network API — everything is local.
- No push notifications; reminders are local system alarms/notifications.
- Like buttons and like counts are hidden in the current UI (legacy fields remain in the database
  for backup compatibility).
- No comment editing or threaded reply UI.

## Screens

| Screen | Description |
|--------|-------------|
| **Home** | Main feed with identity filter, search, reminders, and comment sheet |
| **Photo** | Image-only feed with zoom and overlay toggling |
| **Discover** | Trending identities/posts and multi-keyword search (posts + comments) |
| **Me** | Active identity, profile details, pending reminders, settings entry |
| **Compose** | New post: text, images, camera, original switch, reminder, shake/double-tap send |
| **Post Detail** | Full post, image gallery, comments, reminder info, pin/delete/share menu |
| **Identity List** | Create, edit, activate, and delete identities |
| **Identity Detail** | Edit identity fields and avatar |
| **Settings** | Language, backup/import, reminders, calendar, diagnostics, about |
| **Identity Schedules** | Add/edit/delete automatic identity-switch rules |

There is no message/notification inbox tab; the old Message tab was replaced by the **Photo** feed.
Reminders are surfaced as system notifications, feed badges, and the Me page list.

## Tech stack

- **Language**: Kotlin 1.9.20
- **UI**: Jetpack Compose (BOM 2023.10.01), Material 3, Material icons
- **Architecture**: MVVM with `WeiboRepository` exposing Room `Flow`s and screen `StateFlow`s
- **Database**: Room 2.6.1 (KSP), schema version **13**, explicit migrations only
- **Preferences**: DataStore Preferences (draft, language, backup state, reminder presets)
- **Images**: Coil Compose; attachments stored in app-private `filesDir`
- **Background work**: `AlarmManager` + `BroadcastReceiver` (no WorkManager)
- **Build**: Gradle 8.5, AGP 8.3.0, KSP 1.9.20-1.0.14, JDK 17 target
- **SDK**: minSdk 26, compileSdk/targetSdk 34
- **Release**: R8 code shrinking (resource shrinking intentionally off because avatars are resolved by
  resource name)

## Project structure

```
app/src/main/java/com/pocketweibo/
├── MainActivity.kt            # Entry point + manual navigation (no NavHost)
├── MyWeiboApp.kt              # Application class; restores alarms, seeds data, runs auto backup
├── data/
│   ├── DataSeeder.kt          # First-run identities and sample posts
│   ├── backup/                # Encrypted automatic daily backup
│   ├── local/                 # Room database, migrations, DAOs, entities
│   ├── media/                 # Post/comment attachments, avatars, voice recording
│   ├── prefs/                 # DataStore-backed UI/backup/reminder preferences
│   └── repository/            # WeiboRepository: Room flows, export/import, reminders
├── diagnostic/                # In-memory diagnostic log ring buffer
├── reminder/                  # AlarmManager scheduling, receivers, calendar sync
└── ui/
    ├── components/            # PostCard, comment sheet, avatar, image viewer, etc.
    ├── screens/               # home, photo, discover, me, compose, detail, identity
    ├── theme/                 # Colors and Material theme
    └── util/                  # Time, clipboard, search, formatting helpers
```

Navigation is intentionally manual: `MainScreen` keeps remembered booleans/IDs and renders screens
with a `when` block, so list scroll positions survive detail navigation.

## Data model (Room v13)

| Table | Key fields |
|-------|------------|
| `identities` | `id`, `name`, `avatarResName`, `customAvatarUri`, nationality, gender, birth/death year, occupation, motto, famousWork, bio, `createdAt`, `isActive` |
| `posts` | `id`, `identityId` (nullable), `content`, `imageUris`, `audioPath`, `extrasJson`, `createdAt`, `likeCount`, `commentCount`, `isLiked`, `isPinned` |
| `comments` | `id`, `postId`, `identityId` (nullable), `content`, `audioPath`, `createdAt`, `replyingToCommentId`, `likeCount`, `likedBy` |
| `post_reminders` | `id`, `postId`, `fireAtMillis`, `repeatRule`, `calendarEventId`, `calendarId` |
| `identity_schedules` | `id`, `identityId`, `hour`, `minute`, `daysOfWeek`, `enabled` |

Important rules:

- Comments cascade-delete with their post; posts/comments survive identity deletion with
  `identityId = NULL`.
- Media paths are stored **relative to app-private `filesDir`**, not as absolute paths.
- Any schema change needs a new migration in `DatabaseMigrations.kt` registered in `AppDatabase`.
  There is no destructive fallback.
- Do not use `OnConflictStrategy.REPLACE` for identity inserts; SQLite deletes the old row first and
  has previously cascade-deleted a user's posts.

## Getting started

### Requirements

- JDK 17+
- Android SDK platform 34 and build-tools 34.0.0
- `local.properties` with `sdk.dir=...` (gitignored)
- Android Studio is recommended, but the Gradle wrapper works from the command line

### Build and test

```bash
./gradlew test assembleDebug        # required verification before committing
./gradlew assembleRelease           # signed locally when secrets/keystore.properties exists
./gradlew :app:testDebugUnitTest --tests 'com.pocketweibo.ui.util.ComposeContentTest'
```

On macOS, a bare `./gradlew` may not find Java even when Android Studio is installed. Export the
bundled JetBrains Runtime first:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

The helper scripts (`scripts/*.sh`) apply that fallback automatically.

### Install

- Open the project in Android Studio, sync Gradle, and run on a device/emulator.
- Or install a published APK from the GitHub **Releases** page.
- Or build and install over Wi-Fi with the scripts below.

Use `install-release` when the phone already has a release-signed build: Android refuses to upgrade
across different signatures (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

## Wireless debugging

On the phone, enable **Settings → Developer options → Wireless debugging**. First-time pairing:

```bash
./scripts/adb-wireless.sh pair <phone-ip:pair-port> <6-digit-code>
./scripts/adb-wireless.sh connect <phone-ip>   # dynamic port is auto-scanned (30000–50000)
```

After that, the working address is cached locally, so each session only needs:

```bash
./scripts/adb-wireless.sh connect
./scripts/adb-wireless.sh install-debug     # build + install debug
./scripts/adb-wireless.sh install-release   # build + install signed release
```

Other commands:

```bash
./scripts/adb-wireless.sh status            # devices, mDNS services, cached address
./scripts/adb-wireless.sh logcat            # follow full logcat
./scripts/adb-wireless.sh crash             # follow fatal exceptions only
./scripts/adb-wireless.sh disconnect        # drop the Wi-Fi connection
./scripts/adb-wireless.sh enable-tcpip      # USB device → legacy fixed :5555 mode
```

Environment overrides: `DEVICE_IP`, `DEVICE_PORT` (default 5555), `PORT_SCAN_START` /
`PORT_SCAN_END` (default 30000 / 50000), `PORT_SCAN_JOBS`. The cache lives at
`~/.cache/pocketweibo-adb`; no device address is stored in the repository.

## Release builds and CI

- **CI** (`.github/workflows/ci.yml`) runs `./gradlew test assembleDebug` on pull requests and pushes
  to `main`. It does not lint.
- **Release** (`.github/workflows/build-apk.yml`) runs on `v*` tag pushes or manual dispatch. A tag
  must match `versionName` in `app/build.gradle.kts` (for example `v3.63.4` for `3.63.4`).
  Pushing to `main` never publishes a release.
- **Versioning**: bump `versionCode` by 1 for every shippable change. Bump `versionName` MINOR for a
  new user-facing feature, PATCH for a fix.

### Local signed release

`scripts/build-pocketweibo-release.sh` uses the same signing inputs as CI:

```bash
./scripts/build-pocketweibo-release.sh              # build, copy APK to ~/Downloads (macOS) or /storage/emulated/0/Download, remove root copy
./scripts/build-pocketweibo-release.sh --no-copy    # keep the APK in the repo root only
./scripts/build-pocketweibo-release.sh --copy-dest /path/to/dir
```

It reads `secrets/github-keystore-secrets.local.txt` (gitignored) with `KEYSTORE_PASSWORD` plus the
keystore base64 (companion file, `ANDROID_KEYSTORE_BASE64_FILE`, or inline
`ANDROID_KEYSTORE_BASE64`). Optional keys: `KEY_PASSWORD`, `KEY_ALIAS` (default `pocketweibo`).

For GitHub Actions, configure repository secrets `ANDROID_KEYSTORE_BASE64` and
`KEYSTORE_PASSWORD` (plus optional `KEY_PASSWORD` / `KEY_ALIAS`). See
[`secrets/README.md`](secrets/README.md) and [`docs/FIX.md`](docs/FIX.md) for the full signing flow.
Never commit keystores, passwords, base64 blobs, or `local.properties`.

## Screenshots

[![All screens](docs/screenshots/thumbnails/all_thumbnails.png)](docs/screenshots/home_screen.png)

The captures under `docs/screenshots/` are older than the current UI: the bottom tabs are now
**Home / Photo / Discover / Me**, and several screens (settings, reminders, image feed) are not shown.

## Documentation

- [`AGENTS.md`](AGENTS.md) — compact repo guidance for coding agents
- [`docs/RULES.md`](docs/RULES.md) — workflow, bilingual rules, versioning
- [`docs/FEATURE.md`](docs/FEATURE.md) — shipped feature ledger (F-xxx) and backlog detail
- [`docs/FIX.md`](docs/FIX.md) — fix ledger, CI/signing notes
- [`docs/BACKUP_ARCHIVE.md`](docs/BACKUP_ARCHIVE.md) — JSON/ZIP/Markdown backup formats
- [Chinese README](README.zh-CN.md) — full Chinese translation

## Privacy and license

PocketWeibo works entirely offline. Posts, comments, images, voice clips, identities, and reminders
stay in the app's private storage (or in backups you explicitly export). Reminders optionally create
events in the system calendar when you enable that setting.

The project is released under the [MIT License](LICENSE). Preset identity avatars use the
**Open Peeps** artwork by Pablo Stanley (CC0), rendered through DiceBear; see
[`legal/open_peeps_notice.txt`](legal/open_peeps_notice.txt).
