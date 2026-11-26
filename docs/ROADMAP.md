# PocketWeibo — Improvement Roadmap

_Drafted 2026-11-26 against v3.72.0 (versionCode 216, Room schema v16). **Rev 2 (2026-11-26):** merged findings from `REVIEW-3.md` and items from `FEATURE-PROPOSALS.md`._

Ordered by risk to user data first, then reliability, maintainability, features and platform readiness. Each phase ships as its own release(s) and must pass `./gradlew test assembleDebug` per `AGENTS.md`. References like **R3 §1.1** point to `REVIEW-3.md`; **FP P1** points to `FEATURE-PROPOSALS.md`.

| Phase | Theme | Effort | Ship as |
|---|---|---|---|
| 0 | Safety net (schema export, migration + backup tests) | S | 3.73.0 |
| 1 | **P0: backup/restore safety + high-severity data bugs** | M–L | 3.73.x / 3.74.0 |
| 2 | Reliability: startup, alarms, transactions, cache/storage | M | 3.75.0 |
| 3 | Refactor repository & large screens | L | 3.76–3.78 |
| 4 | Navigation (NavHost) | M | 3.79.0 |
| 5 | Features: offline province location, UI improvements | M | 3.80+ |
| 6 | Toolchain upgrade & targetSdk 35 | L | 4.0.0 |
| 7 | Quality tooling, security & hygiene | S | ongoing |

---

## Phase 0 — Safety net (do first, ~1–2 days)

Goal: make later changes provably non-destructive.

- [ ] `AppDatabase`: set `exportSchema = true`; KSP arg `room.schemaLocation = "$projectDir/schemas"`; commit `app/schemas/16.json`.
- [ ] Add `androidx.room:room-testing:2.6.1` and a `MigrationTestHelper` test for migrations up to 16 (back-fill older schemas where reconstructable; at minimum N-1 → N going forward). **R3 §2.2**
- [ ] Confirm whether schema **v1** ever shipped; if so add `MIGRATION_1_2` or a clear recovery message instead of a crash loop. **R3 §2.2**
- [ ] **Backup round-trip test**: seed posts/comments/identities/reminders/topics → export ZIP → replace import → compare every entity field + media file hashes. Same for `exportTextOnlyForAutoBackup()`. Should **fail today** (BUG-001).
- [ ] **Hostile-input import tests**: ZIP with `../` entries, truncated ZIP, missing `data.json`, missing required JSON fields, old v1/v2 JSON fixtures. **R3 §8**

**Done when:** schema JSON committed; migration, round-trip and hostile-input tests exist and run in CI (hostile ones may be `@Ignore`d until Phase 1 lands).

---

## Phase 1 — P0: backup/restore safety + data bugs (~1 week)

### 1a. Zip Slip guard — R3 §1.1 (security)
- [ ] Add `safeChild(root, name)` (canonical path must stay under `root`) and use it for **every** ZIP write in merge and replace imports; skip + log rejected entries.
- [ ] Hostile-input test from Phase 0 passes.

### 1b. Atomic replace-import — R3 §1.2, §2.5 (data loss)
- [ ] Parse + validate `data.json` completely **before** changing anything; switch throwing `getLong`/`getJSONArray` to `opt*` + explicit validation.
- [ ] Extract media to a staging dir for replace mode too (as merge mode does).
- [ ] Run all DB deletes + inserts in one `db.withTransaction { }`.
- [ ] After commit, swap media trees (old → `*.old`, staging → live, delete `*.old`); restore on failure.
- [ ] Optional: automatic pre-import snapshot in `filesDir` with a "roll back last import" action.

### 1c. Stream ZIP entries with size caps — R3 §6.1
- [ ] Replace `zis.readBytes()` with streaming copy to file; cap per-entry and total size (zip-bomb guard).

### 1d. BUG-001 — Restore drops `isPinned`
- [ ] `put("isPinned", post.isPinned)` in both export writers; `optBoolean("isPinned", false)` in `postFromJson`.
- [ ] Bump export `version`; field optional for v1/v2 archives; update `docs/BACKUP_ARCHIVE.md`.
- [ ] Use the round-trip test to find any **other** dropped fields.

### 1e. BUG-002 — Comment like-state substring / default id 0 — R3 §2.3
- [ ] Remove `currentIdentityId: Long = 0` default; callers pass the active identity.
- [ ] Hotfix (3.73.x): exact delimited match `',' || likedBy || ',' LIKE '%,' || :id || ',%'`.
- [ ] Proper fix (3.74.0): `comment_likes(commentId, identityId)` join table, migration 16 → 17 parsing existing `likedBy` (can share 16 → 17 with Phase 5 location column, or ship separately as 17 → 18).
- [ ] DAO test: identities 1, 11, 21.

### 1f. Auto-backup key strategy — R3 §1.3
- [ ] Decide and document in `BACKUP_ARCHIVE.md`:
  - **Recoverability-first (recommended):** no encryption for in-sandbox auto-backups; passphrase-based (PBKDF2/Argon2 → AES-GCM) encryption only on user export; or
  - **Security-first:** Android Keystore, explicitly device-bound.
- [ ] Keep a decrypt path for existing `PWB1` files; remove the `"unknown"` `ANDROID_ID` fallback.
- [ ] Consolidate to **one** release keystore so `ANDROID_ID` (scoped per signing key) stops changing between builds (see Phase 7).
- [ ] Interim: show "auto-backups only restore on this device and this build signature" in Settings.

### 1g. Explicit Android backup policy — R3 §1.4
- [ ] Either `allowBackup="false"`, or add `dataExtractionRules` + `fullBackupContent` including DB + media, excluding `auto_text_backups/` and cache.

**Done when:** all Phase 0 tests green with guards removed; BUG-001/002 marked fixed in `BUGS.md`.

---

## Phase 2 — Reliability (~1 week)

### Startup — R3 §3.1, §6.3 (BUG-009)
- [ ] Remove `runBlocking(Dispatchers.IO)` from `PocketWeiboApp.onCreate`; reschedule in `applicationScope.launch(IO)`, guarded by a repository `Mutex` against alarm delivery races.
- [ ] Reschedule only on boot / package replaced / time change / first process start — not on every receiver-triggered start.
- [ ] Replace `runBlocking { UiPreferences.applyStored() }` in `MainActivity`: `AppCompatDelegate.setApplicationLocales` for language + sync `SharedPreferences` mirror for theme.

### Transactions & consistency — R3 §2.1, §2.4
- [ ] Wrap multi-step repository mutations in `db.withTransaction` (identity delete + active reassignment, post + attachments + reminders, topic merges, merge-import id remapping).
- [ ] Orphan media sweep: files with no referencing row → delete (periodic / at startup on IO).

### Alarms & reminders — R3 §4
- [ ] **BUG-003**: `clearAllData()` cancels all post-reminder + identity-schedule alarms before wiping; receivers tolerate missing rows.
- [ ] Exact-alarm permission check + prompt in identity-schedule screen (not only `ReminderPickerDialog`); handle `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → reschedule. **R3 §4.1**
- [ ] Handle `TIME_SET`, `TIMEZONE_CHANGED` (and consider `LOCKED_BOOT_COMPLETED`) → recompute repeat rules in current zone. **R3 §4.2**
- [ ] **BUG-008** notification-id collisions → namespaced ids.
- [ ] DST-boundary test cases for repeat rules. **R3 §4.5**

### Cache & storage — FP P1, R3 §5.1
- [ ] Startup cache sweep (old `pocket_weibo_backup_*`, `pocket_weibo_diag_*`, `pw_decrypted_*`, `pw_import_*`, `pw_src_*`, `pw_voice_*`, `compose_capture_*`; idle `compose_prep/`).
- [ ] Export ZIP via SAF `ACTION_CREATE_DOCUMENT` (no cache copy); delete share files after use.
- [ ] Narrow `FileProvider` from `<cache-path path=".">` to a `share/` subdir.
- [ ] Import staging always cleaned in `finally`.
- [ ] Settings → Storage panel: "Data X MB · Cache Y MB · [Clear cache]".

### Other bugs
- [ ] **BUG-004** BC (negative) years corrupted on identity edit + tests.
- [ ] **BUG-007** `PostAudioPlayerBar` leak / prepare on main.
- [ ] **BUG-010** + **R3 §3.2**: move DB writes out of composition scopes into ViewModels / `applicationScope`.
- [ ] **BUG-005 / BUG-006** doc/UI mismatches.

---

## Phase 3 — Refactor for maintainability (~1–2 weeks, incremental)

No behaviour changes; rely on Phase 0 tests.

### Split `WeiboRepository.kt` (1,521 lines) — R3 §2.6, §2.7
- [ ] **`BackupCodec` / `BackupRepository` first** (most P0 history): versioned DTOs (e.g. `kotlinx.serialization`), streaming `JsonWriter` export without pretty-printing (**R3 §6.2**), reflection-based test so new entity fields can't be silently dropped.
- [ ] Then `PostRepository`, `CommentRepository`, `IdentityRepository`, `ReminderRepository`, `TopicRepository`; keep `WeiboRepository` as a temporary facade.
- [ ] Small manual `AppContainer` interface for test fakes (no Hilt needed). **R3 §7.3**

### Slim large screens — R3 §7.2
| File | Lines | Action |
|---|---|---|
| `MeSettingsScreen.kt` | 1,277 | Extract backup/import/export/share logic into `MeSettingsViewModel`; split sections |
| `PostDetailScreen.kt` | 1,207 | Extract header, gallery, comments, menus; logic into `PostDetailViewModel` |
| `TopicsScreen.kt` | 944 | Split topics / todo / search |
| `HomeScreen.kt` | 860 | Extract filter sheet, feed list |
| `IdentityDetailScreen.kt` | 838 | Add `IdentityDetailViewModel` |
| `ComposeScreen.kt` | 833 | Extract attachments, voice, reminder UI |

- [ ] Target: no file > ~500 lines; stateless composables + ViewModels.
- [ ] `collectAsState()` → `collectAsStateWithLifecycle()` (19 sites). **R3 §3.3**
- [ ] Move double-back-exit state off `PocketWeiboApp` into the activity / `BackHandler`. **R3 §3.5**

---

## Phase 4 — Navigation (~3–5 days) — R3 §7.1, §3.4

Highest-churn area in `FIX.md`.
- [ ] Replace boolean/ID `when` routing in `MainActivity.MainScreen` (~26 `remember` states) with `NavHost` + typed routes.
- [ ] Removes `!!` on navigation ids (`publishedPostId!!`, `identityDetailId!!`, `postDetailId!!`).
- [ ] Route notification / shortcut / share intents through the graph → **BUG-011**.
- [ ] Preserve `SwipeBackHandler` and back-stack semantics from FIX.md 3.68.0; Compose UI test for back navigation.

---

## Phase 5 — Features (~1–2 weeks) — FP P2, P3

### Offline province location — FP P2 (needs Phase 0 migration tests)
- [ ] Answer open questions: behaviour outside China; editable after posting; posts only or comments too.
- [ ] GPS via `LocationManager` (last-known first, 15–30 s timeout); bundled province boundary data + point-in-polygon; no `Geocoder`.
- [ ] `PostEntity.provinceCode` (GB/T 2260), migration to next schema version; never store lat/lng.
- [ ] Opt-in 📍 chip in composer (off by default), manual province picker fallback, footer display, optional Discover filter.
- [ ] Include in all export/import paths (avoid repeating BUG-001); update `BACKUP_ARCHIVE.md`, `FEATURE.md`, both READMEs.
- [ ] Tests: known-city lookups, border points, migration, backup round-trip.

### UI improvements — FP P3
- [ ] Feed thumbnails (≤3, Coil with `size()` limits).
- [ ] Empty / first-run states with clear actions.
- [ ] Composer: live character counter near 2,000; drag-to-reorder images.
- [ ] Accessibility: 200% font scale pass; content descriptions on icon-only buttons and media indicators.
- [ ] Optional dynamic color (Material You).
- [ ] Review `docs/screenshots/` for concrete visual suggestions.
- _Edge-to-edge / predictive back → Phase 6 (done with targetSdk 35)._

---

## Phase 6 — Toolchain & platform (~1 week → 4.0.0)

Per `AGENTS.md`, one deliberate upgrade on a branch, with Phase 0–1 tests in place.

1. [ ] Introduce `gradle/libs.versions.toml` (no version changes yet).
2. [ ] Gradle wrapper and AGP to current stable.
3. [ ] Kotlin 1.9.20 → 2.x with the Compose compiler plugin; matching KSP.
4. [ ] Compose BOM, Room, lifecycle, activity, Coil, DataStore bumps; re-evaluate the forced `androidx.core 1.12.0` pin.
5. [ ] compileSdk/targetSdk 34 → 35:
   - Edge-to-edge insets on every screen (title bar, bottom tab bar, sheets, fullscreen viewer); predictive back.
   - Re-verify exact-alarm flow for reminders and identity schedules.
   - Re-verify voice recording and boot receiver restrictions.
6. [ ] Update `AGENTS.md` toolchain section and CI SDK versions.

---

## Phase 7 — Quality tooling, security & hygiene (ongoing)

### CI & tooling — R3 §8
- [ ] `./gradlew lint` in `ci.yml` with a baseline (`abortOnError = true`).
- [ ] Detekt + ktlint/Spotless with baselines.
- [ ] Nightly emulator job for `connectedDebugAndroidTest`; expand instrumented tests (post, comment + voice, reminders, identity switch).
- [ ] String-parity test for `values/` vs `values-en/` strings and plurals. **R3 §7.4**
- [ ] Dependabot/Renovate in grouped, PR-only mode.

### Security & privacy — R3 §5
- [ ] Remove `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` if Photo Picker / `GetContent` covers all image picking (or add `maxSdkVersion="32"`). **§5.2**
- [ ] Request calendar permissions only when the mirror toggle is enabled (verify). **§5.3**
- [ ] Gate `DiagnosticLog` `d/i` logcat output in release; never log post body text (verify current call sites). **§5.4**
- [ ] Opt-in local crash log via uncaught-exception handler → `filesDir`. **§5.6**
- [ ] **Secrets:** consolidate `secrets/` to one release keystore + encrypted offline backup; delete stale keystores/base64 copies; check `git log --all -- secrets/ '*.keystore' '*.jks'` (needs Xcode Command Line Tools on this Mac); rotate anything ever committed.

### Build & performance — R3 §6, REVIEW-2
- [ ] Remove unused `navigation-compose` if Phase 4 is deferred; remove `android.enableJetifier=true`.
- [ ] Replace `material-icons-extended` with the few icons used.
- [ ] Avatar `getIdentifier` → `Map<String, Int>` of `R.drawable.*`; then enable `isShrinkResources`. **R3 §7.5**
- [ ] Baseline Profile module (Macrobenchmark).
- [ ] FTS4/FTS5 table for post + comment search when data grows. **R3 §6.4**
- [ ] Localize or per-locale seed `DataSeeder.kt` demo content (~109 hardcoded Chinese strings).

---

## Tracking

- Tick items here and mark fixes in `docs/BUGS.md`; record shipped changes in `FIX.md` / `FEATURE.md` per `RULES.md`.
- Bump `versionName`/`versionCode` per shipped phase (single-line literals — CI parses them).
