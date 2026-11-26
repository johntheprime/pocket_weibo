# PocketWeibo — Engineering Review, Third Pass (Deep Dive) (2026-11-26)

_Scope: v3.72.0 (versionCode 216), Room schema v16, ~17.5k lines Kotlin. Builds on `REVIEW.md` and `REVIEW-2.md` (both 2026-10-18, v3.58.0) and `BUGS.md`. Static code review only — nothing was built or run on a device._

Severity: **P0** = data loss / security, fix before next release · **P1** = correctness / reliability · **P2** = maintainability / performance · **P3** = polish.

---

## 0. Status of earlier reviews

| Earlier finding | Source | Status at 3.72.0 |
|---|---|---|
| CI never runs tests; only `apk-build` branch | REVIEW-2 §2 (P1) | ✅ **Fixed** — `ci.yml` runs `./gradlew test assembleDebug` on PRs + `main`; release moved to `build-apk.yml` on `v*` tags |
| No lint gate in CI | REVIEW-2 §1 (P2) | ❌ Open — CI runs `test assembleDebug` only |
| Unused `navigation-compose` | REVIEW-2 §1 (P2) | ❌ Open |
| `enableJetifier=true` | REVIEW-2 §1 (P2) | ❌ Open |
| Global `androidx.core` force to 1.12.0 | REVIEW-2 §1 (P2) | ❌ Open (intentional, see AGENTS.md) |
| `material-icons-extended` bloat | REVIEW-2 §1 (P3) | ❌ Open |
| No baseline profile | REVIEW-2 §1 (P3) | ❌ Open |
| No DAO / migration tests | REVIEW / REVIEW-2 §6 | ❌ Open — still 1 instrumented test, `exportSchema = false` |
| BUG-001 `isPinned` lost on backup/restore | BUGS.md (High) | ❌ **Open** — `isPinned` appears 0× in `WeiboRepository.kt` |
| BUG-002 comment like substring match / default id 0 | BUGS.md (High) | ❌ **Open** — `likedBy LIKE '%' \|\| :id \|\| '%'`, default `currentIdentityId = 0` |
| BUG-009 `runBlocking` on main at startup | BUGS.md (Low) | ❌ Open — **2 sites** (see §3.1); upgraded to P1 here |
| BUG-003…BUG-011 | BUGS.md | Not re-verified individually; assume open |

Unit tests grew from 8 → 13 since REVIEW.md. Schema went v12 → v16 with no migration tests added.

---

## 1. New P0 findings

### 1.1 Zip Slip (path traversal) in backup import — **P0 security**
`WeiboRepository.kt`:
- Merge mode (~L979–983): `File(staging, name.replace('\\','/'))` → `writeBytes`.
- Replace mode (~L940–961): `File(context.filesDir, name)` for any entry starting with `identity_avatars/` (and similar for post/comment attachment roots).

Entry names are only `trimStart('/')`'d; `..` segments are **not** rejected. A crafted ZIP entry like `identity_avatars/../../shared_prefs/x.xml` or `post_attachments/../databases/pocketweibo_database` writes **anywhere in the app sandbox**, including the Room DB and DataStore files.

Realistic vector: user is sent a "backup" file and imports it. Local-only app, but backups are explicitly designed to be shared/moved.

**Fix:**
```kotlin
private fun safeChild(root: File, name: String): File? {
    val out = File(root, name).canonicalFile
    return if (out.path.startsWith(root.canonicalPath + File.separator)) out else null
}
```
Use it for every ZIP write; skip (and log) entries that return `null`. Add a unit test with a `../` entry.

### 1.2 Replace-mode import is non-atomic — **P0 data loss**
`importData(override = true)` calls `clearAllData()` (deletes all rows **and** all attachment trees), then inserts row by row. In the ZIP path, attachments are written into live `filesDir` *before* `data.json` is parsed.

- There are **0** `@Transaction` / `withTransaction` usages in the codebase.
- Any exception mid-import (malformed JSON, `getLong` on missing key, OOM on a large entry, process death) leaves the user with **an empty or half-populated database and deleted media**. The outer `catch (e: Exception) { e.printStackTrace(); false }` just reports "failed".

**Fix:**
1. Parse and validate `data.json` fully **before** touching anything.
2. Extract media into a staging dir (as merge mode already does).
3. Run all DB deletes + inserts in a single `db.withTransaction { }`.
4. Only after commit, swap media: rename old trees to `*.old`, move staging in, delete `*.old`. On failure, restore.
5. Optionally take an automatic pre-import ZIP snapshot (into `filesDir`, not cache) the user can roll back to.

### 1.3 Encrypted auto-backup key derived from `ANDROID_ID` — **P0 data recoverability**
`AutoBackupCrypto` keys AES-256-GCM with `SHA-256(salt ‖ ANDROID_ID ‖ packageName)`.

- `ANDROID_ID` changes on **factory reset**, and since Android 8 is scoped per signing key — **a different signing key = different ID**. You have **4 keystores** in `secrets/`; a release signed with a different one than the last install cannot decrypt prior auto-backups.
- So the backups protect against nothing the user cares about (device loss/reset) while being unreadable exactly when needed. Also not a secret: any app/ADB user on the device with the same signing context can derive it.
- If `ANDROID_ID` is null the key falls back to the literal `"unknown"`.

**Fix (pick one, document in `BACKUP_ARCHIVE.md`):**
- **Recoverability-first**: drop encryption for local auto-backups (they live in the app sandbox already) and offer encryption only on *export* with a user passphrase (PBKDF2/Argon2 → AES-GCM).
- **Security-first**: use Android Keystore — but then be explicit that backups are device-bound and do not survive reset.
- Either way, keep a decrypt path for existing `PWB1` files.

### 1.4 `android:allowBackup="true"` with no rules — **P1 privacy / correctness**
No `dataExtractionRules` / `fullBackupContent`. Android Auto Backup / device-to-device transfer will copy the Room DB, DataStore, attachments **and** auto-backups (whose key won't match on the new device — see 1.3). Decide explicitly: either `allowBackup="false"`, or add rules that include DB + media and exclude `auto_text_backups/` and `cache`.

---

## 2. Data layer

| # | Finding | Sev |
|---|---|---|
| 2.1 | **No transactions anywhere.** Multi-step writes (delete identity + reassign active, post + attachments + reminders, merge import id remapping, topic alias merges) can half-apply. Wrap repository mutations in `db.withTransaction`. | P1 |
| 2.2 | `exportSchema = false` with 14 hand-written migrations (2→16). Any mistake is undetectable until a user upgrades. No `fallbackToDestructiveMigration` (good — avoids silent wipe) but a failing migration = crash-on-launch loop. Export schemas + `MigrationTestHelper`. Users still on **schema v1** have no `MIGRATION_1_2` → crash. | P1 |
| 2.3 | `likedBy` is a comma/substring string (BUG-002). Model as `comment_likes(commentId, identityId)` join table. | P1 |
| 2.4 | Media files and DB rows have no consistency check. Deleting rows doesn't always delete files (and vice versa after failed imports). Add an "orphan sweep" job: files under `post_attachments/`, `comment_attachments/`, `identity_avatars/` with no referencing row → delete. Also explains storage growth (ties to FEATURE-PROPOSALS P1). | P2 |
| 2.5 | `importData` uses `getLong("id")` / `getJSONArray` (throwing) mixed with `opt*`. A single missing field aborts the whole import (and with 1.2, after data was wiped). Normalize to `opt*` + validation pass. | P1 |
| 2.6 | Export/import are hand-rolled `JSONObject` mirrors of entities — the root cause of BUG-001. Consider `kotlinx.serialization` DTOs with a versioned schema + a round-trip test that enumerates entity fields via reflection so a new column can't be silently dropped. | P2 |
| 2.7 | `WeiboRepository` (1,521 lines) holds DB, files, ZIP, JSON, calendar sync and alarms. Split as in ROADMAP Phase 3; backup code (`BackupCodec`) first since it has the most P0s. | P2 |

---

## 3. Threading, lifecycle, startup

### 3.1 Two `runBlocking` calls on the main thread at launch — **P1**
- `PocketWeiboApp.onCreate` (~L56): `runBlocking(Dispatchers.IO) { read prefs; rescheduleAllPostRemindersFromDb(); rescheduleAllIdentitySchedulesFromDb() }` — **full table scans + one AlarmManager IPC per reminder, before any UI**. Grows linearly with reminder count. This also runs on every **BroadcastReceiver** process start (alarm fire, boot), adding latency to the exact path that must be fast.
- `MainActivity` (~L62): `runBlocking { UiPreferences.applyStored(...) }` — DataStore read on main (needed for locale/theme before first frame, but should be cached).

Risk: ANR (5 s) on slow devices with many reminders; slow cold start everywhere.

**Fix:** keep only a cheap, cached read on the critical path. Reschedule alarms in `applicationScope.launch(IO)`; to avoid the race the comment mentions, guard reschedule-vs-fire with a `Mutex` in the repository, and only reschedule on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / time-change / once per process — not on every receiver-triggered start. For locale/theme, use `AppCompatDelegate.setApplicationLocales` (persisted by the system) and a synchronous `SharedPreferences` mirror for theme.

### 3.2 Unscoped coroutines — **P2**
63 `.launch {` sites; many in composables via `mainScope`/`rememberCoroutineScope` performing DB writes (e.g. `MainActivity` L253, L331; identity screens — BUG-010). Writes launched from a composition scope are cancelled when the user navigates away mid-write. Move writes to ViewModels (`viewModelScope`) or `applicationScope` for must-complete work.

### 3.3 `collectAsState()` without lifecycle — **P3**
19 sites. Use `collectAsStateWithLifecycle()` (lifecycle-runtime-compose) so flows stop when backgrounded — matters for Room flows over large tables.

### 3.4 `!!` on navigation state — **P2**
24 non-null asserts, several in `MainActivity` routing (`publishedPostId!!`, `identityDetailId!!`, `postDetailId!!`). With manual boolean navigation, a recomposition where the flag is true but the id was cleared → crash. NavHost with typed args (ROADMAP Phase 4) removes the class of bug.

### 3.5 Static "double-back to exit" state on Application — **P3**
`PocketWeiboApp.shouldExit()/lastBackPressTime/onExitConfirm` lives on the Application object. UI state there leaks across activity recreation; move to the activity / a `BackHandler`.

---

## 4. Reminders & alarms

| # | Finding | Sev |
|---|---|---|
| 4.1 | Post reminders use `setAlarmClock` (best — exempt from Doze, shows in status bar) with fallbacks; identity schedules use `setExactAndAllowWhileIdle` → `setExact` → `set()`. Good layering. But `SCHEDULE_EXACT_ALARM` on Android 14+ is **denied by default** for new installs; only `ReminderPickerDialog` checks `canScheduleExactAlarms()`. Identity schedules silently degrade to inexact `set()` (can drift hours in Doze). Check + prompt in the identity-schedule screen too, and listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reschedule. | P1 |
| 4.2 | Boot receiver handles `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` only. Missing `TIME_SET` / `TIMEZONE_CHANGED` / `LOCKED_BOOT_COMPLETED`. RTC alarms stored as absolute millis — repeat rules ("every workday 9:00") computed in the old timezone fire at the wrong wall-clock time after travel/DST until next app open. | P1 |
| 4.3 | `clearAllData()` deletes calendar events but **does not cancel AlarmManager alarms** (BUG-003 confirmed). Deleted posts' reminders can still fire → receiver must tolerate missing rows (verify it does). | P1 |
| 4.4 | Notification ids derived from post/reminder ids can collide (BUG-008). Use a dedicated autoincrement column or `(type << 28) xor id`. | P3 |
| 4.5 | Reminder behaviour has decent unit coverage (`ReminderRepeatRuleTest`, `IdentityScheduleNextFireTest`, `PostReminderAlarmSchedulerTest`) — the best-tested area. Add DST-boundary cases (e.g. 2026-03-08 / 2026-11-01 America/Chicago; China has no DST but users travel). | P3 |

---

## 5. Security & privacy (beyond §1)

| # | Finding | Sev |
|---|---|---|
| 5.1 | `FileProvider` exposes `<cache-path path=".">` — the **entire cache dir**, which contains plaintext decrypted backups (`pw_decrypted_*.json`) and full backup ZIPs that are never deleted (see FEATURE-PROPOSALS P1). Only granted URIs are readable, but stale plaintext sitting indefinitely is a leak risk. Narrow to a `share/` subdir and delete after share. | P2 |
| 5.2 | `READ_EXTERNAL_STORAGE` declared without `maxSdkVersion="32"`; `READ_MEDIA_IMAGES` declared. If the app uses the Photo Picker / `GetContent`, **neither is needed**. Removing them avoids a scary permission and Play policy friction (Android 14 partial media access). | P2 |
| 5.3 | `READ_CALENDAR`/`WRITE_CALENDAR` requested for an optional mirror — ensure requested only when the toggle is turned on (verify). | P3 |
| 5.4 | `DiagnosticLog` writes to logcat unconditionally (`Log.d/i/w/e`) in release, including reminder content/ids in some call sites. Gate `d/i` behind `BuildConfig.DEBUG` or the in-app capture flag; never log post body text. | P2 |
| 5.5 | Receivers: `PostReminderReceiver` / `IdentityScheduleReceiver` are `exported=false` ✅; boot receiver `exported=true` is required ✅; all `PendingIntent`s use `FLAG_IMMUTABLE` ✅. | OK |
| 5.6 | Release builds: R8 on ✅, `SourceFile,LineNumberTable` kept ✅. No crash reporting — for a local-first app, consider an opt-in local crash log (you already have `DiagnosticLogBuffer`) via `Thread.setDefaultUncaughtExceptionHandler` writing to `filesDir`. | P3 |

---

## 6. Performance & resources

| # | Finding | Sev |
|---|---|---|
| 6.1 | Image import: `BitmapFactory` with bounds pass + `inSampleSize` ✅ (good, avoids OOM). But ZIP import uses `zis.readBytes()` per entry — a 50 MB video/large image or a zip bomb loads fully into heap → OOM. Stream entries to file with a size cap. | P1 |
| 6.2 | `exportAllDataZip` loads all posts into memory and builds `data.json` as one `String` (`toString(2)` pretty-printed). Fine at hundreds of posts; at 10k+ posts with comments it's tens of MB on heap. Use streaming `JsonWriter`, drop pretty-printing. | P2 |
| 6.3 | `rescheduleAll*FromDb()` on every process start (§3.1) — O(n) IPC calls. | P1 |
| 6.4 | Search uses SQL `LIKE '%kw%'` (full scans). Fine now; if posts grow, add an FTS4/FTS5 table for post + comment bodies (Room supports `@Fts4`). Also gives proper CJK tokenization options. | P3 |
| 6.5 | `material-icons-extended` + no baseline profile — debug build size and cold start. Copy the few icons used; add a Baseline Profile module (Macrobenchmark). | P3 |
| 6.6 | Feed uses Coil — ensure list thumbnails (if added per FEATURE-PROPOSALS P3) request `size()` and use `memoryCacheKey`; Coil's disk cache also lives in `cacheDir` and counts toward the cache-size complaint (bounded by default ~2% disk). | P3 |

---

## 7. Architecture & UI code

| # | Finding | Sev |
|---|---|---|
| 7.1 | Manual navigation (`MainScreen` with ~26 `remember` states + `when`) — the recurring source of FIX.md regressions (3.68.0 "返回落到错误层", BUG-011). This is now the **highest-churn area per FIX.md**. NavHost migration (ROADMAP Phase 4) pays for itself. | P1 |
| 7.2 | Screens >800 lines with business logic inline (`MeSettingsScreen` 1,277 incl. SAF/import/export/share/crypto; `PostDetailScreen` 1,207). Logic in composables is untestable — explains why tests cover only utils. | P2 |
| 7.3 | No DI. Repository is constructed on the Application and pulled via casting. Fine at this size; a tiny manual `AppContainer` interface would allow fakes in tests without Hilt. | P3 |
| 7.4 | Bilingual rule (RULES.md §1) is well followed — only `DataSeeder` (109) and `SearchKeywords` (3) contain hardcoded CJK. Add a unit test that diff-checks key sets of `values/strings.xml` vs `values-en/strings.xml` (and plurals) to enforce it automatically. | P3 |
| 7.5 | `getIdentifier` for avatars blocks resource shrinking (REVIEW-2). Replace with a `Map<String, Int>` of `R.drawable.*` — compile-time safe, enables `isShrinkResources = true`. | P3 |

---

## 8. Testing & tooling

Current: 13 JVM unit tests (utils, reminders, crypto), 1 instrumented test, CI runs `test assembleDebug`.

| Gap | Recommendation | Priority |
|---|---|---|
| Backup/restore (most P0s live here) | Round-trip test: seed → export ZIP → replace import → compare every entity field + media hashes. Plus malicious ZIP (`../`), truncated ZIP, missing `data.json`, old v1/v2 JSON fixtures. | **P0** |
| Migrations | `exportSchema = true`, commit `schemas/`, `MigrationTestHelper` 2→16 (+ 16→17 going forward). | P1 |
| DAO correctness | In-memory Room tests for comment likes (ids 1/11/21), pinned ordering, topic alias merges, search. Robolectric makes these JVM-fast. | P1 |
| String parity | JVM test comparing string/plural keys across locales. | P3 |
| Lint / static analysis | Add `./gradlew lint` to CI with `lint { warningsAsErrors = false; abortOnError = true; baseline = file("lint-baseline.xml") }`; add Detekt with baseline. | P2 |
| Instrumented in CI | Nightly emulator job for `connectedDebugAndroidTest`. | P3 |
| Dependency hygiene | Renovate/Dependabot in **PR-only, grouped** mode — keeps visibility without "casual upgrades". | P3 |

---

## 9. Prioritized action list

**Before next release (P0):**
1. Zip Slip guard on all import writes + test (§1.1).
2. Atomic replace-import: validate → stage → transaction → swap (§1.2).
3. Fix BUG-001 (`isPinned`) and BUG-002 (likes) — both already specced in ROADMAP Phase 1.
4. Decide auto-backup key strategy; at minimum document "device + signing-key bound" in Settings (§1.3).

**Next 1–2 releases (P1):**
5. Remove startup `runBlocking`; move reschedule off critical path with a mutex (§3.1).
6. `exportSchema` + migration tests; add `MIGRATION_1_2` or explicit message (§2.2).
7. Wrap repository mutations in transactions (§2.1).
8. Exact-alarm permission handling for identity schedules; time/timezone change receivers (§4.1–4.2).
9. Cancel alarms in `clearAllData()` (§4.3 / BUG-003).
10. Stream ZIP entries with size caps (§6.1).
11. Explicit `allowBackup` policy (§1.4).
12. NavHost migration (§7.1).

**Ongoing (P2/P3):** cache-path narrowing, permission cleanup, release logging, lint/Detekt in CI, repository/screens split, FTS search, baseline profile, icon slimming, string-parity test.

---

## 10. Mapping to existing docs

| This review | ROADMAP.md | FEATURE-PROPOSALS.md |
|---|---|---|
| §1.1, §1.2, §2.5, §6.1 | **New** — add to Phase 1 (P0) | — |
| §1.3, §1.4 | **New** — add to Phase 1/2 | — |
| §2.2, §8 migrations | Phase 0 | P2 depends on it |
| §2.3 | Phase 1 BUG-002 | — |
| §2.4, §5.1 | Phase 2 | P1 (cache/storage) |
| §3.1 | Phase 2 BUG-009 | — |
| §4.x | Phase 2 | — |
| §7.1 | Phase 4 | — |
| §5.2, §6.5, §8 lint | Phase 5/6 | — |
