# PocketWeibo — Engineering Review (2026-10-18)

Scope: full source-tree review of `app/src/main/java/com/pocketweibo/**` against `FEATURE.md`, `FIX.md`, `RULES.md`. Companion doc: `BUGS.md` (defect list with repro + suggested fixes).

---

## 1. Project snapshot

| Item | Value |
|---|---|
| Package | `com.pocketweibo` (application id `com.pocketmyweibo`) |
| versionName / versionCode | 3.58.0 / 196 (consistent with the top `FIX.md` row 3.57.1/195 → new minor bump) |
| minSdk / target / compile | 26 / 34 / 34 |
| Stack | Kotlin, Jetpack Compose (Material3), Room v12, Coil, DataStore (Draft + UiPreferences), AlarmManager reminders |
| R8 | `isMinifyEnabled = true`, `isShrinkResources = false` — intentionally left off because avatars are resolved at runtime via `Resources.getIdentifier` (see §5) |
| Docs | `README.md`, `FEATURE.md` (F-001…F-061, all “Done”), `FIX.md` (per-version ledger), `RULES.md`, `docs/F010-F020-rollout-log.md` |
| Tests | 8 JVM unit tests (`app/src/test/...`, pass) + 1 androidTest (`IdentityDeleteKeepsPostsTest.kt`); **no data/DAO or migration tests** — see `REVIEW-2.md` §6 |

## 2. Architecture

Clean, conventional single-module layout:

- `data/local` — Room: 5 entities (`PostEntity`, `IdentityEntity`, `CommentEntity`, `PostReminderEntity`, `IdentityScheduleEntity`), DAOs, `AppDatabase` (v12) with an explicit migration chain `DatabaseMigrations.kt` (2→3 … 11→12).
- `data/repository/WeiboRepository.kt` (1.2k lines) — broad facade: identity CRUD (+avatar options), posts (+gallery/voice attachment staging), comments (+voice), reminders, identity schedules, drafts (DataStore), full JSON/ZIP export/import (merge & replace), markdown export.
- `data/media/*` — app-private attachment storage per post/comment with relative-path serialization (`PostAttachmentStorage`, `CommentAttachmentStorage`, `IdentityAvatarStorage`, `VoiceRecordingController`).
- `data/backup/*` — F-038 auto daily text backups: AES-GCM (key from AndroidId+package), 3-day retention.
- `reminder/*` — post reminders (`PostReminderAlarmScheduler`/`Receiver`/`BootReceiver`, stable notify IDs, legacy PendingIntent cleanup) and identity-switch schedules (`IdentityScheduleAlarmScheduler`, `IdentityScheduleNextFire`, `Receiver`). DB is treated as source of truth with `rescheduleAll…FromDb()` on start/boot; overdue reminders are delivered immediately rather than dropped.
- `ui/**` — Compose screens in manual-stack style (single `MainActivity`, state-based navigation in `MainScreen`, `SwipeBackHandler` for predictive back, `WeiboBottomTabBar`, share + post-open intents funneled through `ComposeIntentViewModel`).
- `diagnostic/*` — logcat + ring-buffer capture for settings export.

**Strengths**

- Solid persistence hygiene: explicit FKs, indexed queries, a real migration chain, `@Transaction` rewrites for list-typed columns (e.g. `likedBy`, `imageUris`), and attachment files always moved under per-row directories on insert.
- Reminder layer is careful everywhere it matters: exact-alarm + fallback + notification-channel checks, boot re-schedule, stale-row recovery, legacy-ID cancel, retry on next fire for repeating rules.
- Export/import paths are thorough — merge mode remaps old→new ids for identities, posts, comments *and* reparents reply threading and attachment paths; replace mode clears before import; ZIP staging dir cleaned in `finally`.
- Almost everything goes through string resources (`values` 374 = `values-en` 374, parity OK) and content descriptions; timezone-correct reminder math (`ReminderRepeatRule` stores schedule-millis vs local wall time).
- `MainScreen` handles the double-tap-home-scroll + "scroll to latest" consumption signals correctly.

**Weak points**

- `WeiboRepository` is a god-class; export/import logic (~700 lines) deserves its own file.
- Several screens reach directly into `app.repository` + `CoroutineScope(Dispatchers.IO).launch {}` or `rememberCoroutineScope` instead of a shared ViewModel layer (identity screens, IdentityListScreen); fine at this scale but unstructured scopes are a crash/teardown hazard (see `BUGS.md` #10).
- No data-layer tests: export/import round-trips, `likedBy` semantics, and the migration chain are untested (the highest-value gaps). Detailed test-quality audit in `REVIEW-2.md` §6.

## 3. Feature ledger spot-check (FEATURE.md → code)

Verified implemented and working: deleted-identity display fallback (`identityDisplayName`), likes, comment voice attachments (both compose bar and player, including the `PostVoice.STORED_PLACEHOLDER` convention), share-sheet intake + text parsing across App link flows, drafts, autosave-on-exit compose, shake-to-send / shake-to-remind (different thresholds, typing-idle guards, warmup, cooldown), quick reminder presets with usage-frequency reordering (`ReminderQuickPresetStats`), repeating reminders (daily/weekdays/weekly…), accurate-alarm permission fallbacks, boot restore, discovery search (posts + comments) with result dedup, photo/delete with per-post attachment cleanup,自動每日加密备份, presets avatars (the 8 Open Peeps PNGs exist in `drawable-nodpi`; `PresetAvatarThumbnail` has a fallback placeholder), pin/unpin with DAO `isPinned DESC` ordering for the main lists, markdown export, full/replace/merge backup restore.

Partial / doc-vs-code gaps (detailed in `BUGS.md`): F-059 lacks 工作日/周末 quick chips; F-060 pin ordering not applied to the home quick-access per-identity preview; F-038 restore resets pin state (bug, not just omission).

## 4. Platform & compatibility notes

- minSdk 26 is clean: `listIterator`/`VibratorManager`/`PickVisualMedia` paths are version-guarded; Material3 TimePicker/Predictive-back usage matches API 34 target.
- Notification IDs are forced non-negative (`PostReminderNotificationIds`), with legacy-id cancel for upgrade paths — well done.
- Exact alarms on Android 14 may not be granted; the scheduler picks `setAlarmClock` (user-visible) or `setExactAndAllowWhileIdle` fallback when possible. Behavior when SCHEDULE_EXACT_ALARM is denied falls back to `set()`-style inexact — reminder may fire late; FEATURE.md F-014 descriptions acknowledge exactness caveats. Acceptable, worth a settings hint.
- Edge-to-edge + `Scaffold` padding is consistently threaded through all screens; `Reference set done via enableEdgeToEdge()`.
- Dark theme is wired (`isSystemInDarkTheme` + `LocalDarkTheme`), but many components hardcode light colors (`Color.White` surfaces, gray text constants) — dark mode renders inconsistently (see observation OBS-01).

## 5. Build / tooling

- Toolchain is Kotlin **1.9.20** + AGP 8.3.0 + KSP 1.9.20-1.0.14 (root `build.gradle.kts`), with the classic `composeOptions.kotlinCompilerExtensionVersion = "1.5.5"` (no Kotlin 2.x / compose-compiler-plugin migration yet). Deep build/dependency audit in `REVIEW-2.md` §1.
- `isShrinkResources = false` is deliberate and documented (runtime `getIdentifier` avatar lookup); consider switching to resource obfuscation with an explicit keep-list instead — see suggestion S-1.
- The single `legal/open_peeps_notice.txt` is referenced in `IdentityDetailScreen` — attribution present, matching F-045.

## 6. Risk-ranked concerns (summary — details in BUGS.md)

1. **Data loss on restore**: `isPinned` (and `activeIdentity` for text backup) not exported → pin state wiped after any JSON/ZIP/auto-backup restore (`BUGS.md` #1).
2. **Comment-like state incorrect for multiple identities**: default `currentIdentityId = 0` fan-out of `LIKE '%' || id || '%'` substring semantics in `CommentDao` → `isLikedByMe` false hits + non-atomic `REPLACE` list updates (`BUGS.md` #2).
3. **`clearAllData()` leaves AlarmManager alarms orphaned** for both reminder types (receivers no-op via DB-row lookup, but alarms still fire) (`BUGS.md` #3).
4. BC-style negative years (seed data: `-551`) cannot survive identity editing (`BUGS.md` #4).

## 7. Suggestions (non-blocking)

- S-1: keep `isMinifyEnabled` but add `resConfigs("zh-rCN","en")` and a `getIdentifier` keep-list; then consider `isShrinkResources = true`.
- S-2: extract a `BackupManager` (export/import/zip/merge logic) out of `WeiboRepository`; make merge id-mapping a small pure function that becomes unit-testable.
- S-3: introduce a `useCase`/ViewModel per identity screen; replace ad-hoc `CoroutineScope(Dispatchers.IO)` with structured scopes.
- S-4: convert comment like/unlike to `@Transaction` DAO methods that read-modify-write the `likedBy` row once (removes substring matching entirely).
- S-5: `PostAudioPlayerBar` should create the `MediaPlayer` lazily off the main thread (IO dispatcher `prepareAsync`), and release on prepare failure (`BUGS.md` #7).
- S-6: add unit tests for: JSON round-trip with `isPinned`, `likedBy` multi-user toggles, `IdentityScheduleNextFire.computeNext` (Mon-first vs calendar-first), `PostReminderNotificationIds` uniqueness, and migration 11→12 hydration.
- S-7: consider a consistent dark-theme pass: route all hardcoded `Color.White`/gray text constants through `MaterialTheme.colorScheme` now that `LocalDarkTheme` exists.

---
**Verdict**: well-maintained surface with meticulous feature bookkeeping; the serious findings are 2 correctness bugs (pinned-state loss on restore, comment-like substring matching) plus a wipe-leaves-alarms hygiene gap — all reachable, all small-to-medium fixes.
