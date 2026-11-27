# PocketWeibo — Engineering Review (consolidated)

_Last updated **2026-11-26** against **v3.74.0 (versionCode 224)**, Room schema **v16**, `main` @ `e381b0e`._

This is the single engineering-review document. It merges, and replaces:

| Former file | Date / scope | Now lives in |
|---|---|---|
| `REVIEW.md` (pass 1) | 2026-10-18, v3.58.0 — architecture, feature spot-check | §1, §3 |
| `REVIEW-2.md` (pass 2) | 2026-10-18 — build, CI, security, startup, data, tests | §3, §5 |
| `REVIEW-3.md` (pass 3) | 2026-11-26, v3.72.0 — deep dive, backup/restore safety | §3, §5 |
| `BUGS.md` | 2026-10-18 — defect register BUG-001…011, OBS-01…05 | §4 |
| `ROADMAP.md` | 2026-11-26 — phased plan (Phase 0–7) | §6 |

Code comments that cite `REVIEW-2 §x` / `REVIEW-3 §x` / `BUG-0xx` still resolve: see the **ID index** in §7.

**Status legend:** ✅ Done · 🟡 Partly done · ⬜ Open · ↩️ Reverted · ❓ Needs a product decision.
Every ✅ / 🟡 row names the commit on `main` and its date. Severity: **P0** data loss / security · **P1** correctness / reliability · **P2** maintainability / performance · **P3** polish.

---

## 1. Project snapshot (2026-11-26)

| Item | Value |
|---|---|
| Package | `com.pocketweibo` |
| Version | 3.74.0 / 224 |
| SDK | minSdk 26 · targetSdk 34 · compileSdk 34 |
| Toolchain | AGP 8.3.0 · Kotlin 1.9.20 · KSP 1.9.20-1.0.14 · Gradle 8.5 · JDK 17 · Compose compiler 1.5.5 |
| Stack | Kotlin, Jetpack Compose (Material 3), Room v16 (14 hand-written migrations 2→16), Coil, DataStore, AlarmManager |
| Versions | `gradle/libs.versions.toml` (app `versionName` / `versionCode` stay in `app/build.gradle.kts` for CI `sed`) |
| Source | 95 Kotlin files, ~17.9k lines in `app/src/main` |
| Largest files | `WeiboRepository.kt` 1,569 · `MeSettingsScreen.kt` 1,278 · `PostDetailScreen.kt` 1,207 |
| Tests | 18 JVM test files (incl. Robolectric repository tests) + 1 instrumented test |
| CI | `ci.yml`: `./gradlew test lint assembleDebug` on PRs and `main`; `build-apk.yml`: `test lint assemble*` on `v*` tags / manual |

### Architecture (unchanged since pass 1)

- `data/local` — Room entities, DAOs, `AppDatabase`, `DatabaseMigrations.kt`.
- `data/repository/WeiboRepository.kt` — facade over DAOs, media, ZIP/JSON/Markdown export and import, reminders, schedules, drafts.
- `data/media/*` — app-private attachment storage (`PostAttachmentStorage`, `CommentAttachmentStorage`, `IdentityAvatarStorage`, `VoiceRecordingController`) plus `ShareCache` and `CacheSweeper`.
- `data/backup/*` — encrypted daily text backups (`AutoBackupCrypto`, `AutoDailyBackup`) and `ZipSafety`.
- `reminder/*` — post reminders and identity schedules (AlarmManager + receivers; DB is the source of truth).
- `ui/**` — Compose screens with manual state-based navigation in `MainActivity.MainScreen`.
- `diagnostic/*` — logcat + in-app ring-buffer capture.

**Strengths noted by every pass:** explicit FKs and indices; careful reminder layer (`setAlarmClock` + fallbacks, boot restore, overdue delivery); thorough merge-import id remapping; strict zh/en string parity; signing secrets never committed.

---

## 2. Implementation log (2026-11-26)

All review work landed on `main` on **2026-11-26** in this order:

| Commit | Version | Scope |
|---|---|---|
| `fd9049e` | 3.73.1 (218) | P0 data safety — BUG-001, BUG-002 hotfix, transactional `importData` |
| `7ed12e6` | — | Removed committed stash-conflict markers (strings, FEATURE.md) |
| `4803026` | 3.73.2 (219) | P1 — non-blocking startup, Room schema export, CI lint, Robolectric data tests |
| `02e8121` | 3.73.3 (220) | P2 — backup rules, fail-closed crypto, lifecycle-aware flows, BUG-007, BUG-010, first-run seeding, dependency cleanup |
| `f0c2f70` | 3.73.4 (221) | P3 — narrow FileProvider, scoped storage permission, version catalog |
| `7894874` | 3.73.5 (222) | Pass-3 backup safety — Zip Slip, staged atomic replace-import, streamed ZIP, BUG-003, time/zone receivers, release logging, cache sweep |
| `f2ffe9c` | 3.73.6 (223) | Pass-3 small items — BUG-004/005/006/008, exact-alarm prompt, transactions, orphan media sweep, string-parity test |
| `da004bd` | 3.74.0 (224) | Integrated local feature and fix branches (F-081) |
| `e381b0e` | 3.74.0 (224) | Removed Room schema export again (schema JSON should not be public) |

---

## 3. Findings and status

### 3.1 Backup, restore and data integrity

| ID | Finding | Sev | Status |
|---|---|---|---|
| R3 §1.1 | **Zip Slip**: ZIP entry names with `../` could write anywhere in the sandbox (DB, DataStore). | P0 | ✅ `7894874` 2026-11-26 — `ZipSafety.safeChild` canonical-path guard on every write; rejected entries skipped and logged; `ZipSafetyTest`. |
| R3 §1.2 · R2 §5 | **Non-atomic replace-import**: cleared rows and media, then inserted row by row; a failure left an empty or half dataset. | P0 | ✅ `fd9049e` 2026-11-26 (all DB writes in one `withTransaction`) + `7894874` 2026-11-26 (both modes extract to a `filesDir` staging dir, validate `data.json` first, swap media trees only after commit, roll back on failure; alarms/calendar side effects applied after commit). ⬜ Optional pre-import snapshot / "roll back last import" not done. |
| R3 §6.1 | `zis.readBytes()` loaded whole entries into memory (OOM / zip bomb). | P1 | ✅ `7894874` 2026-11-26 — streamed to disk; caps 512 MB per entry, 2 GB total. |
| R3 §2.5 | Import mixes throwing `getLong` / `getJSONArray` with `opt*`; one missing field aborts the import. | P1 | 🟡 Abort is now harmless (rollback, `fd9049e` / `7894874`). ⬜ Normalize to `opt*` + validation pass so imperfect files still import. |
| R3 §2.6 | Hand-rolled `JSONObject` mirrors of entities — root cause of BUG-001. | P2 | ⬜ Versioned DTOs (e.g. `kotlinx.serialization`) + reflection test that fails when a new column is not exported. |
| R3 §1.3 | Auto-backup key = `SHA-256(salt ‖ ANDROID_ID ‖ package)`: changes on factory reset and with the signing key (4 keystores in `secrets/`); not a secret; `"unknown"` fallback. | P0 | 🟡 `02e8121` 2026-11-26 — fallback removed, fails closed (that day's backup is skipped). ❓ **Decide**: recoverability-first (no encryption for in-sandbox auto-backups, passphrase only on export — recommended) vs. Android Keystore (device-bound). Keep a decrypt path for existing `PWB1` files. ⬜ Consolidate to one release keystore. |
| R3 §1.4 · R2 §3 | `allowBackup="true"` with no rules. | P1 | ✅ `02e8121` 2026-11-26 — `data_extraction_rules.xml` / `backup_rules.xml`: no cloud backup of private data; device-to-device transfer keeps DB, files, prefs. ⬜ Device transfer still copies `auto_text_backups/`, which cannot be decrypted on the new device (see §1.3) — exclude it once the key strategy is decided. |
| R3 §2.1 | No transactions for multi-step writes. | P1 | 🟡 `fd9049e`, `f2ffe9c` 2026-11-26 — import, clear-all, identity delete + active reassignment, set active identity, topic merge, comment insert/delete. ⬜ Post + attachments + reminders, merge-import id remapping outside `importData`. |
| R3 §2.4 | Media files and rows drift apart (orphans after crashes / failed imports). | P2 | ✅ `f2ffe9c` 2026-11-26 — `sweepOrphanMedia()` at startup removes numeric-id folders/avatars with no row. |
| R3 §2.2 · R2 §5 | `exportSchema = false`, 14 migrations untested; no `MIGRATION_1_2`. | P1 | ↩️ Schema export + `MigrationSchemaTest` added in `4803026`, removed in `e381b0e` (2026-11-26) because schema JSON should not be published. ⬜ Alternative: migration tests that build old schemas from SQL fixtures kept under `app/src/test`. ❓ **Confirm whether schema v1 ever shipped**; if yes add `MIGRATION_1_2` or a recovery message. |
| R2 §5 | `DataSeeder` re-seeds whenever the identity table is empty (clear-all brings demo data back). | P2 | ✅ `02e8121` 2026-11-26 — `seedIfFirstRun` with a first-run flag. |
| R2 §5 · OBS-04 | Export `version` written but never checked on import. | P3 | ⬜ |
| R3 §6.2 | ZIP export builds `data.json` as one pretty-printed string. | P2 | ⬜ Streaming `JsonWriter`, no pretty-printing. |

### 3.2 Startup, threading, lifecycle

| ID | Finding | Sev | Status |
|---|---|---|---|
| R2 §4 · R3 §3.1 · BUG-009 | Two `runBlocking` calls on the main thread at launch (reschedule all alarms; read UI prefs). | P1 | ✅ `4803026` 2026-11-26 — rescheduling runs in `applicationScope`; receivers call `awaitStartup()` first to keep ordering; language applied from a `SharedPreferences` mirror. 0 `runBlocking` left in `main`. ⬜ Reschedule only on boot / package replaced / time change instead of every process start (R3 §6.3). |
| R2 §4 · R3 §3.3 | `collectAsState()` without lifecycle. | P2 | ✅ `02e8121` 2026-11-26 — all sites use `collectAsStateWithLifecycle`. |
| BUG-010 · R3 §3.2 | Fire-and-forget `CoroutineScope(Dispatchers.IO)` / writes in composition scopes. | P2 | 🟡 `02e8121` 2026-11-26 — identity list fixed. ⬜ Move remaining DB writes from composables into ViewModels / `applicationScope`. |
| R3 §3.4 | 24 `!!` on navigation state. | P2 | ⬜ Goes away with NavHost (§6 Phase 4). |
| R3 §3.5 | Double-back-to-exit state lives on `PocketWeiboApp`. | P3 | ⬜ |
| BUG-007 · R2 §4 | `PostAudioPlayerBar` prepared `MediaPlayer` on the main thread and leaked on failure. | P2 | ✅ `02e8121` 2026-11-26 — prepare on IO, release on failure. |

### 3.3 Reminders and alarms

| ID | Finding | Sev | Status |
|---|---|---|---|
| R3 §4.1 · OBS-03 | Exact-alarm permission only checked in `ReminderPickerDialog`; identity schedules silently inexact. | P1 | ✅ `7894874` (receiver for `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`) + `f2ffe9c` (settings button on the identity-schedule screen), 2026-11-26. |
| R3 §4.2 | No `TIME_SET` / `TIMEZONE_CHANGED` handling. | P1 | ✅ `7894874` 2026-11-26. ⬜ `LOCKED_BOOT_COMPLETED` not added. |
| BUG-003 · R3 §4.3 | `clearAllData()` left AlarmManager alarms armed. | P1 | ✅ `7894874` 2026-11-26 — alarms and calendar events of cleared rows cancelled after commit. |
| BUG-008 · R3 §4.4 | Notification ids from a hash could collide. | P3 | ✅ `f2ffe9c` 2026-11-26 — id = reminder row id; uniqueness test. |
| R3 §4.5 | No DST-boundary tests for repeat rules. | P3 | ⬜ e.g. 2026-03-08 / 2026-11-01 America/Chicago. |
| R2 §3 | `SCHEDULE_EXACT_ALARM` needs a Play-policy justification. | P2 | ⬜ Only relevant for a Play release. |

### 3.4 Security and privacy

| ID | Finding | Sev | Status |
|---|---|---|---|
| R2 §3 · R3 §5.1 | FileProvider exposed the whole cache dir (plaintext decrypted backups, import staging). | P2 | ✅ `f0c2f70` 2026-11-26 — only `cache/shared/` (`ShareCache`). ✅ Stale share files: swept after 24 h by `CacheSweeper` (`7894874`); fixed share filenames + SAF export (F-082, 3.75.0, `7092c96` 2026-11-27). |
| R2 §3 · R3 §5.2 | `READ_EXTERNAL_STORAGE` without `maxSdkVersion`. | P2 | ✅ `f0c2f70` 2026-11-26 — `maxSdkVersion="32"`. ⬜ Remove both media permissions if Photo Picker / `GetContent` covers every image path. |
| R3 §5.3 | Calendar permissions should only be requested when the mirror toggle is turned on. | P3 | ⬜ Verify. |
| R3 §5.4 | `DiagnosticLog` wrote D/I to logcat in release. | P2 | ✅ `7894874` 2026-11-26 — D/I only in debug builds or while in-app capture is on. ⬜ Audit call sites so post body text is never logged. |
| R3 §5.6 | No crash log. | P3 | ⬜ Opt-in local crash log via uncaught-exception handler → `filesDir`. |
| R3 §5.5 · R2 §3 | Receivers / PendingIntents / signing secrets. | — | OK — non-exported receivers, `FLAG_IMMUTABLE`, no secret committed. ⬜ Check `git log --all -- secrets/ '*.keystore' '*.jks'` and delete stale keystore copies. |

### 3.5 Build, CI and tooling

| ID | Finding | Sev | Status |
|---|---|---|---|
| R2 §2 | CI never ran tests; single release-branch trigger. | P1 | ✅ before this review cycle (`d8bd845`, 2026-11-04): `ci.yml` on PRs + `main`, releases from tags. |
| R2 §1/§2 · R3 §8 | No lint gate. | P2 | ✅ `4803026` 2026-11-26 — `./gradlew test lint …` in both workflows; `lint {}` with `lint-baseline.xml`. ⬜ Detekt / ktlint with baselines. |
| R2 §1 | Unused `navigation-compose`; Jetifier on. | P2 | ✅ `02e8121` 2026-11-26 — both removed. |
| R2 §1 | Global `androidx.core` force to 1.12.0. | P2 | ✅ Kept on purpose and documented in `app/build.gradle.kts` (`02e8121`, 2026-11-26); revisit with compileSdk 35. |
| R2 §1/§2 | No version catalog; CI `sed`-parses the build file. | P3 | ✅ `f0c2f70` 2026-11-26 — `gradle/libs.versions.toml`. App version intentionally stays in `app/build.gradle.kts`. |
| R2 §1 · R3 §6.5 | `material-icons-extended`, no baseline profile. | P3 | ⬜ |
| R2 §1 · R3 §7.5 | `getIdentifier` for avatars blocks resource shrinking. | P3 | ⬜ `Map<String, Int>` of `R.drawable.*`, then `isShrinkResources = true`. |
| R3 §8 | No nightly emulator job; no Dependabot/Renovate. | P3 | ⬜ |

### 3.6 Tests

| ID | Finding | Sev | Status |
|---|---|---|---|
| R2 §6 · R3 §8 | No DAO / export round-trip tests. | P1 | ✅ `4803026` 2026-11-26 — `WeiboRepositoryDataTest` (Robolectric, in-memory Room): pinned round-trip, old files, exact-id likes, idempotent like/unlike, override rollback. `TopicsBatchDataTest` added in `da004bd`. ⬜ Full ZIP round-trip comparing every entity field + media hashes; truncated ZIP / missing `data.json` fixtures. |
| R3 §8 | Hostile ZIP input tests. | P0 | ✅ `7894874` 2026-11-26 — `ZipSafetyTest`. |
| R2 §6 | Tautological `CommentTimeFormattingTest`. | P2 | ✅ `02e8121` 2026-11-26 — deleted. ⬜ Real test for `RelativeTime`. |
| R2 §6 | No coroutine test tooling. | P2 | ✅ `4803026` 2026-11-26 — `kotlinx-coroutines-test`. |
| R3 §7.4 | No automatic zh/en string parity check. | P3 | ✅ `f2ffe9c` 2026-11-26 — `StringParityTest`. |

### 3.7 Architecture, UI and localization

| ID | Finding | Sev | Status |
|---|---|---|---|
| R3 §7.1 | Manual navigation (~26 `remember` states) is the highest-churn area in `FIX.md`. | P1 | ⬜ NavHost migration (§6 Phase 4). |
| R3 §2.7 · R1 S-2 | `WeiboRepository` god-class. | P2 | ⬜ Split, `BackupCodec` first. |
| R3 §7.2 | Screens > 800 lines with logic inline. | P2 | ⬜ |
| R3 §7.3 | No DI / test seam. | P3 | ⬜ Small manual `AppContainer`. |
| R3 §6.4 | `LIKE '%kw%'` search. | P3 | ⬜ FTS4/5 when data grows. |
| R2 §7 | Default resources are Chinese, so a third locale falls back to Chinese. | P2 | ❓ Keep Chinese or swap to English default. |
| OBS-01 · R2 §7 | ~40 hardcoded light colors; dark mode inconsistent. | P3 | ⬜ |
| R3 §7.4 | `DataSeeder` demo content is Chinese-only. | P3 | ⬜ Allowed by RULES.md §1; localize only if wanted. |

---

## 4. Bug register (formerly `BUGS.md`)

| ID | Sev | Summary | Status |
|---|---|---|---|
| BUG-001 | High | Restore wiped `isPinned` (JSON, ZIP, auto-backup). | ✅ `fd9049e` 2026-11-26 — exported and imported; export `version` 4 (full) / 3 (auto). |
| BUG-002 | High | Comment like-state: substring `LIKE`, default identity 0, non-idempotent like, corrupting unlike. | 🟡 `fd9049e` 2026-11-26 — active identity passed in; exact delimited match; idempotent like/unlike. ⬜ Proper fix: `comment_likes(commentId, identityId)` table + migration 16→17. |
| BUG-003 | Medium | `clearAllData()` left alarms armed. | ✅ `7894874` 2026-11-26 |
| BUG-004 | Medium | BC (negative) years lost their minus on edit. | ✅ `f2ffe9c` 2026-11-26 — `signedYearInput` + `SignedYearInputTest`. |
| BUG-005 | Low | Identity schedule lacked 工作日 / 周末 one-tap presets (F-059). | ✅ `f2ffe9c` 2026-11-26 |
| BUG-006 | Low | Home identity quick-access preview not pinned-first (F-060). | ✅ `f2ffe9c` 2026-11-26 |
| BUG-007 | Low | `PostAudioPlayerBar` main-thread prepare + leak. | ✅ `02e8121` 2026-11-26 |
| BUG-008 | Low | Notification-id collisions. | ✅ `f2ffe9c` 2026-11-26 |
| BUG-009 | Low | `runBlocking` at app start. | ✅ `4803026` 2026-11-26 |
| BUG-010 | Low | Unstructured coroutines in identity screens. | ✅ `02e8121` 2026-11-26 |
| BUG-012 | High | Full exports had no post reminders and no export had identity schedules; no import read them; replace-import cleared existing ones. Found 2026-11-27 after a restore lost all timers. | ✅ `f4b90be` 2026-11-27 (3.75.1) — both written and imported in every mode, timer-only restore in Settings, `TimersBackupTest`. |
| BUG-011 | Low | Notification tap ignored while a stacked screen (Settings, Compose…) is open. | ⬜ Fix with NavHost, or drop overlay states when `openPostId` arrives. |

**Design observations**

| ID | Observation | Status |
|---|---|---|
| OBS-01 | Dark theme wired but most components hardcode light colors. | ⬜ |
| OBS-02 | `PRAGMA foreign_keys` inside migration transactions is a no-op. | ⬜ |
| OBS-03 | Exact-alarm denial silently degrades precision. | ✅ see R3 §4.1 |
| OBS-04 | Export `version` never checked on import. | ⬜ |
| OBS-05 | Tests covered only pure functions. | ✅ see §3.6 |

**Checked and cleared (pass 1):** the 8 Open Peeps preset PNGs exist; receivers no-op safely on missing rows.

---

## 5. Decisions needed

1. **Auto-backup key strategy** (R3 §1.3) — recommended: plain in-sandbox auto-backups, passphrase encryption only on export; keep a `PWB1` decrypt path.
2. **Schema v1 in the wild?** (R3 §2.2) — determines whether `MIGRATION_1_2` is needed.
3. **Migration tests without published schema JSON** (after `e381b0e`) — SQL-fixture tests, or keep schema JSON in a private location.
4. **Default locale** (R2 §7) — Chinese or English fallback for third locales.

---

## 6. Remaining roadmap (formerly `ROADMAP.md`)

Phases kept from the 2026-11-26 roadmap; only open work is listed.

| Phase | Theme | Open items |
|---|---|---|
| 0 — Safety net | Tests before risky changes | Full ZIP round-trip (fields + media hashes); truncated / missing-`data.json` / old v1–v2 fixtures; migration tests (decision 3); schema v1 check (decision 2). |
| 1 — Backup safety | Remaining P0/P1 | `comment_likes` table (BUG-002 proper fix, migration 16→17); auto-backup key (decision 1) + one keystore; exclude `auto_text_backups/` from device transfer; `opt*` import normalization (R3 §2.5); optional pre-import snapshot. |
| 2 — Reliability | Startup, alarms, storage | Reschedule only on boot/update/time change; remaining transactions (post + attachments + reminders); `LOCKED_BOOT_COMPLETED`; DST tests; move composition-scope writes into ViewModels. |
| 3 — Refactor | Maintainability | Split `WeiboRepository` (`BackupCodec` first, streaming `JsonWriter`, versioned DTOs); slim screens > 500 lines; `AppContainer`; move double-back state off `Application`. |
| 4 — Navigation | NavHost | Typed routes; remove nav `!!`; route notification / shortcut / share intents (BUG-011); keep `SwipeBackHandler` semantics; back-navigation UI test. |
| 5 — Features | See FEATURE.md "Proposed backlog" | Offline province/country location; UI improvements. |
| 6 — Toolchain | 4.0.0 | Gradle/AGP current; Kotlin 2.x + Compose compiler plugin; library bumps; re-evaluate `core` pin; targetSdk 35 (edge-to-edge, predictive back, exact alarms, voice, boot receiver). ✅ Step 1 (version catalog) done in `f0c2f70`. |
| 7 — Hygiene | Ongoing | Detekt/ktlint; nightly emulator tests; Dependabot (grouped, PR-only); permission review (§3.4); post-text logging audit; crash log; icon slimming; avatar map + resource shrinking; baseline profile; FTS search; dark-theme colors. |

---

## 7. ID index

Former section numbers used in code comments and ledgers:

| Cited as | Topic | See |
|---|---|---|
| `REVIEW-2 §1` | Build & dependencies | §3.5 |
| `REVIEW-2 §2` | CI/CD | §3.5 |
| `REVIEW-2 §3` | Security & privacy | §3.1, §3.4 |
| `REVIEW-2 §4` | Startup & runtime performance | §3.2 |
| `REVIEW-2 §5` | Data layer & migrations | §3.1 |
| `REVIEW-2 §6` | Test quality | §3.6 |
| `REVIEW-2 §7` | Localization & accessibility | §3.7 |
| `REVIEW-2 §9` / "P0–P3" | Pass-2 remediation roadmap | §2 (commits `fd9049e`…`f0c2f70`) |
| `REVIEW-3 §1.x` | Backup P0s | §3.1 |
| `REVIEW-3 §2.x` | Data layer | §3.1 |
| `REVIEW-3 §3.x` | Threading & startup | §3.2 |
| `REVIEW-3 §4.x` | Reminders | §3.3 |
| `REVIEW-3 §5.x` | Security | §3.4 |
| `REVIEW-3 §6.x` | Performance | §3.1, §3.2, §3.5, §3.7 |
| `REVIEW-3 §7.x` | Architecture & UI | §3.7 |
| `REVIEW-3 §8` | Testing & tooling | §3.5, §3.6 |
| `BUG-0xx`, `OBS-0x` | Defects | §4 |
| `ROADMAP Phase n` | Plan | §6 |
| `FP P1/P2/P3` | Feature proposals | `FEATURE.md` → Proposed backlog |
