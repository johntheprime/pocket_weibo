# PocketWeibo — Engineering Review, Second Pass (2026-10-18)

Deeper review that goes beyond `REVIEW.md` into build/dependency configuration, CI/CD, security/privacy, startup & runtime performance, data/migration risk, and test quality. Findings here are additive; severity scale as in `BUGS.md`. Verification performed on the working tree at commit `1f49e5b` (+this session's changes).

---

## 0. Corrections to the first pass (`REVIEW.md`)

The first pass contained three factual errors, corrected here:

1. **Test coverage is not "one test".** There are **9 test sources**: 8 JVM unit tests under `app/src/test/...` (`EntityTest`, `AutoBackupCryptoTest`, `ReminderQuickPresetStatsTest`, `ShakeReminderTimeTest`, `IdentityScheduleNextFireTest`, `PostReminderAlarmSchedulerTest`, `PostReminderNotificationIdsTest`, `ReminderRepeatRuleTest`) plus 1 instrumentation test. `./gradlew testDebugUnitTest` **passes** (verified, `BUILD SUCCESSFUL`). Coverage of the *data/export* layer is still zero, which is what matters for BUG-001/002 — but the blanket "no test suite" statement was wrong.
2. **Kotlin/Compose toolchain is Kotlin 1.9.20, not Kotlin 2.x.** Root `build.gradle.kts` pins AGP 8.3.0 + Kotlin 1.9.20 + KSP 1.9.20-1.0.14, and `app/build.gradle.kts` uses the classic `composeOptions.kotlinCompilerExtensionVersion = "1.5.5"`. There is no Kotlin 2.0 compose-compiler-plugin migration yet.
3. **`isPinned` loss (BUG-001) is the only export gap I originally listed as a pin issue** — that stands. The retracted sketch-avatar claim in `REVIEW.md` §3 is already annotated there.

---

## 1. Build & dependency audit

| Area | Finding | Severity |
|---|---|---|
| Unused dependency | `androidx.navigation:navigation-compose:2.7.5` is declared but **0 imports** exist (navigation is manual state in `MainScreen`). Dead weight in every debug APK and a maintenance lie. | P2 |
| Jetifier | `android.enableJetifier=true` in `gradle.properties`. The dependency set is fully AndroidX; Jetifier only adds build time and rewrite risk. | P2 |
| Forced versions | `configurations.configureEach { resolutionStrategy.force("androidx.core:core-ktx:1.12.0", "androidx.core:core:1.12.0") }` globally pins core. This is a compileSdk-34 workaround; it silently defeats upgrade suggestions and can hide real conflicts. | P2 |
| Version catalog | No `gradle/libs.versions.toml`; versions are hardcoded across files (and CI/`sed` parses `app/build.gradle.kts`). Fine at this size, but a catalog removes the sed fragility. | P3 |
| Icon bloat | `material-icons-extended` pulls thousands of vectors; only a handful are used. Debug APK/build time suffer (release is R8-shrunk, so release is acceptable). | P3 |
| R8 config | `proguard-rules.pro` only keeps Room `INSTANCE` + line numbers. With `isShrinkResources=false`, runtime `getIdentifier` avatars keep working (names stay in `resources.arsc`), so current state is **correct** — but it means resource shrinking can never be enabled without a keep-list for `avatar_*`/`ic_stat_*`. | P3 |
| No baseline profile | No `BaselineProfile` module/generator; cold-start JIT cost is paid every launch. | P3 |
| Lint | No `lint {}` block; `lintVitalRelease` still runs in `assembleRelease`, but there is no explicit policy/CI lint gate. | P2 |
| Gradle/JDK | Wrapper Gradle 8.5, AGP 8.3.0, JDK 17, compileSdk/targetSdk 34 — a coherent, current set. | OK |

## 2. CI/CD & release pipeline audit (`.github/workflows/build-apk.yml`)

**Good**: stable signing from secrets (no per-run debug keys), scoped `permissions: contents: write`, `concurrency` cancel-in-progress, version parsed from Gradle, APK + source tarball + GitHub Release with unique tags, `workflow_dispatch` inputs.

**Gaps**:

- **CI never runs tests or lint.** The only Gradle invocation is `./gradlew assembleRelease` (line 130). `RULES.md` §3/§62 mandates `./gradlew test assembleDebug` before commit, yet the pipeline can publish a release with failing unit tests and does no `lint`. **P1** — add a `./gradlew test lint` (or `check`) step before packaging.
- **Single branch trigger** (`push` to `apk-build`): PRs and other branches are never validated, so regressions only surface on the release branch. **P2** — add a `pull_request`/`push` build+test job.
- **`sed` version parsing** duplicates logic in `scripts/build-pocketweibo-release.sh`; brittle to formatting changes. Move to a version catalog + a tiny Gradle task that prints the version. **P3**
- **`git archive HEAD`** for the "source at this commit" tarball: on `push` this is fine, but on `workflow_dispatch` HEAD may not be the checked-out ref's tip if new commits land — harmless today because checkout is default ref. **P3** note.

## 3. Security & privacy audit

- **Signing material is correctly ignored.** `git ls-files` shows only `*.example` files tracked; `.gitignore` covers `release.keystore`, `ci-release.keystore`, `pocket-weibo-github-release.keystore`, `*.jks`, `keystore.properties`, and the base64/secret `.local.*` files. **No secret is committed.** (Positive; keep it that way.)
- **`AutoBackupCrypto` key derivation is obfuscation, not secrecy.** `SHA-256(salt ‖ ANDROID_ID ‖ packageName)` with a constant salt. Android 8+ scopes `ANDROID_ID` per app-signing-key+user, so it is not trivially cross-app readable, but it is **not user-secret**: anyone with root/backup access to the same package can recompute the key, and the `?: "unknown"` fallback in `keyFor` collapses to a **fixed, guessable key** on the rare devices returning null. For F-038 "encrypted" backups, this protects against casual file inspection only. **P2** — acceptable if documented; better to wrap a keystore-backed secret (`AndroidKeystore`) or derive from a user passphrase; at minimum, never use the `"unknown"` fallback (fail closed).
- **`android:allowBackup="true"` with no `dataExtractionRules`/`fullBackupContent`.** On Android 12+ the app should declare `android:dataExtractionRules`; as written, the Room DB, media tree and (already-encrypted) auto-backup files are eligible for cloud auto-backup with platform defaults. For a private-notes app this is a privacy surprise and untested restore path. **P2** — decide intent and declare rules explicitly (exclude or include deliberately).
- **`READ_EXTERNAL_STORAGE` has no `android:maxSdkVersion`.** On API 33+ it is superseded by `READ_MEDIA_IMAGES`; leaving it unscoped is flagged by lint and exposes an over-broad legacy permission on old devices. **P3**.
- **`SCHEDULE_EXACT_ALARM`** is declared. This is a Play-policy–sensitive permission (only appropriate for user-facing alarms) — it is used for reminders so likely justifiable, but the store listing/review needs that justification, and Android 14 denial must degrade gracefully (tracked as OBS-03). **P2** for a Play release.
- **FileProvider scope** is `cache-path name="cache" path="."` — the whole cache dir is shareable via the provider. Since cache holds the export ZIP/`pw_import_*` staging, this is broader than needed. Narrow to the export subpath. **P3**.
- Component exposure is correct: both `*Receiver`s `exported=false`, boot receiver `exported=true` (required) with only BOOT/MY_PACKAGE_REPLACED, activity exported but only MAIN/SEND filters. **OK**.

## 4. Startup & runtime performance

- **`runBlocking` twice on cold start.** `PocketWeiboApp.onCreate` blocks the main thread on `Dispatchers.IO` to read the diagnostic flag and **re-register every reminder + identity schedule** (`rescheduleAll…FromDb`, which iterates all rows and touches AlarmManager) — see `MyWeiboApp.kt:54-59`; `MainActivity.onCreate` additionally `runBlocking`s the stored UI prefs (`MainActivity.kt:52`). Cold-start latency therefore scales with the number of stored reminders. **P1** — reschedule in a structured `applicationScope` coroutine (with a documented ordering guarantee) rather than blocking `onCreate`; apply UI prefs asynchronously via state.
- **All flows collected non-lifecycle-aware.** 28 `collectAsState(` vs **0** `collectAsStateWithLifecycle`. Room flows keep emitting while the app is backgrounded, doing needless work; also a correctness smell on config change. **P2** — adopt `collectAsStateWithLifecycle` (needs `lifecycle-runtime-compose`).
- **`MediaPlayer().prepare()` inside composition** in `PostAudioPlayerBar` (see BUG-007) — main-thread I/O + leak on failure. **P2**.
- **`runCatching` used 19×**; several wrap suspend/IO work where errors are silently swallowed to `null`/`false` (export/import return `false`, players go silent, avatar writes return `false`) with no user-visible signal. Not inherently wrong, but combined with a diagnostic log that is opt-in, failures are near-invisible. **P2** — surface user-facing errors for backup/import at least.
- **No baseline profile / no macrobenchmark**, icon-extended dependency, and a 14k-LOC Compose app built with the classic compiler — cold start and first-scroll are the likely rough spots. **P3**.

## 5. Data layer & migration risk

- **`exportSchema = false` with a 10-step migration chain (2→12).** No schema JSONs are committed, so migrations cannot be validated by `MigrationTestHelper` and there is no diff guard against accidental schema drift. For an app that is *already at v12*, this is the highest-value data-integrity gap. **P1** — enable `room.schemaLocation`, commit `schemas/`, add migration instrumentation tests.
- **Migration chain starts at 2→3** (`AppDatabase.kt:42`). A device still on schema version 1 (if such ever shipped) would hit `IllegalStateException` on open — no 1→2 migration and no fallback. Confirm v1 never shipped publicly; otherwise add the migration or a guarded path. **P1/P2** pending confirmation.
- **`DataSeeder.seedIfEmpty` re-seeds whenever all identities are gone.** After a user "clear all data" (or an override-import that ends with zero identities) the historical figures silently return on next launch (`DataSeeder.kt:200-205`). That may be intended, but it conflicts with the mental model of a destructive clear. **P2** — gate seeding on a "first run" flag rather than "table empty".
- **Export `version` field is written but never checked on import** (JSON v2 / auto-backup v1). Forward/backward compatibility is implicit. **P3**.
- **`importData(override=true)` inserts are row-by-row, non-transactional.** A mid-import exception leaves a partially imported dataset (`importData` catches and returns `false`, but the rows already inserted stay). **P2** — wrap replace/override import in a single Room transaction (or `withTransaction`), so failure is atomic.
- Relationship/query inspection: FK cascades and index usage look deliberate; pinned ordering is implemented in `PostDao` for the main lists (BUG-006 covers the one preview path).

## 6. Test-quality audit

- **`EntityTest.kt` contains a tautological test.** `CommentTimeFormattingTest.formatCommentTime` is a **private local re-implementation** that hardcodes Chinese strings and is asserted against itself — it exercises **no production code**. Production formatting lives in `ui/util/RelativeTime.kt` (`Resources.formatRelativeTime`). This inflates the test count without adding coverage. **P2** — delete or rewrite against `RelativeTime` (needs `Resources`/Robolectric or a pure extraction).
- **No data/DAO tests.** There is no Robolectric and no in-memory Room test; `CommentDao` like sematics (BUG-002), export/import round-trips (BUG-001), and all migrations are untested. This is exactly where the two High bugs live. **P1**.
- **No async test tooling** (`kotlinx-coroutines-test`, `turbine`) and no `mockk`; suspend paths are currently untestable without it. **P2**.
- **`PostReminderNotificationIdsTest.notifyId_differsForDifferentPairs`** only checks the `(1,2)` vs `(2,1)` ordering case; it does not probe the actual hash-collision risk described in BUG-008. **P3**.
- What is tested well: reminder repeat rules, next-fire computation, notifications ids' non-negativity/stability, quick-preset stats, shake-time parsing, crypto round-trip, and identity-delete-keeps-posts. These are the trickiest pure functions — good instincts. **OK**.

## 7. Localization & accessibility

- **Default resources are Chinese** (`values/` is zh, `values-en/` is English) — so a device in any third locale (e.g. French) falls back to **Chinese**, not English. If English is meant to be the international fallback, the defaults should be swapped (`values/` = en, `values-zh/` = zh). **P2** (product decision).
- `values` / `values-en` string parity is exact (374/374) — good discipline.
- `supportsRtl="true"` is set, but several layouts hardcode LTR assumptions (leading/trailing via `padding(start/end)` is mostly fine; check icon placement in bubbles). Not audited deeply. **P3**.
- Accessibility: content descriptions are broadly present (good), but 40 hardcoded `Color.White`/gray constants bypass dark theme (OBS-01) and there is no dynamic-font/large-text verification. **P3**.

## 8. Code-health metrics (measured)

- Source: **14,076 LOC** across 79 Kotlin files in `main`.
- Hotspots: `WeiboRepository.kt` 1,222; `MeSettingsScreen.kt` 1,136; `PostDetailScreen.kt` 862; `IdentityDetailScreen.kt` 838; `ComposeScreen.kt` 801; `HomeScreen.kt` 781. Several screens are large enough that a change to one feature risks the whole file.
- 7 unstructured `CoroutineScope(…)` launches (no `GlobalScope`, which is good), 0 TODO/FIXME comments (clean or under-documented — lean to clean), 19 `runCatching`.
- No dependency-injection framework; construction is manual via `PocketWeiboApp`. Acceptable for this size, but it is why ViewModels/screens reach into `app.repository` directly.

## 9. Prioritized remediation roadmap

**P0 (correctness / data loss)** — already in `BUGS.md`: BUG-001 (pinned lost on restore) and BUG-002 (comment like substring). Add the `importData` transaction wrap (§5) since it compounds restore safety.

**P1 (high leverage)**
1. Move reminder/prefs rescheduling off `onCreate`'s `runBlocking` (§4).
2. Enable Room schema export + migration tests; confirm no v1-in-the-wild path (§5).
3. Add `./gradlew test lint` to CI so RULES.md is actually enforced (§2).
4. Add Room/DAO + export round-trip unit tests (Robolectric/in-memory) targeting BUG-001/002 (§6).

**P2 (quality/security)**
5. Decide and declare `allowBackup`/`dataExtractionRules`; harden `AutoBackupCrypto` (no `"unknown"` key) (§3).
6. `collectAsStateWithLifecycle` migration (§4); fix `PostAudioPlayerBar` (BUG-007).
7. Remove `navigation-compose` + Jetifier; reconsider the global `core` force; add explicit lint config (§1).
8. Delete/rewrite the tautological time-format test (§6); gate seeding on a first-run flag (§5); `scope.launch` cleanup (BUG-010).

**P3 (polish)**
9. Version catalog; baseline profile; narrow FileProvider cache path; `READ_EXTERNAL_STORAGE maxSdkVersion`; icon-set trim; localization default-locale decision; dark-theme color routing (OBS-01).

---

**Bottom line**: the build is green, tests pass, signing/secrets hygiene is genuinely good, and the reminder/backup feature surface is unusually thorough. The second pass shifts the emphasis from "there are few tests" to *where* the risk concentrates: an **unverified migration chain (`exportSchema=false`)**, **non-transactional destructive import**, **blocking startup work**, and **a CI pipeline that publishes releases without running the tests RULES.md mandates**. Fixing P0+P1 removes essentially all data-loss exposure.
