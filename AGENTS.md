# AGENTS.md

Compact, repo-specific guidance for coding agents. Read `docs/RULES.md` §0 and `.cursor/rules/pocket-weibo-shipping.mdc` before shipping user-visible work.

## Project shape

- Single-module Android app (`:app`), Kotlin + Jetpack Compose (Material 3) + Room. No backend, network API, or auth; all data is local.
- Entry points: `MainActivity` / `MainScreen()` in `app/src/main/java/com/pocketweibo/MainActivity.kt`, `PocketWeiboApp`, and `WeiboRepository`.
- Navigation is manual: `MainScreen` uses remembered booleans/IDs and a `when` block. There is no `NavHost` despite the navigation dependency.
- Screens/ViewModels live under `ui/screens/<area>`; reusable Compose UI under `ui/components`; time/clipboard/format helpers under `ui/util`.
- `WeiboRepository` wraps Room DAOs and exposes `Flow`s; screen ViewModels expose `StateFlow`. Prefer extending these over introducing new data layers.
- UI is bilingual, default Chinese (`values/`) with English (`values-en/`). Every user-visible string must exist in both files.
- Background work is `AlarmManager` + `BroadcastReceiver` (`reminder/`), not WorkManager. `PocketWeiboApp.onCreate` reschedules reminders and identity schedules from Room; boot/package-update receivers re-register alarms.
- Automatic daily text backup runs at app startup (`AutoDailyBackup`) and retains the latest 3 local calendar days.

## Toolchain and environment

- Required: JDK 17+, Android SDK platform 34 + build-tools 34.0.0. Gradle wrapper 8.5, AGP 8.3.0, Kotlin 1.9.20, KSP 1.9.20-1.0.14, compileSdk/targetSdk 34, minSdk 26.
- `local.properties` is gitignored and must exist on every machine with `sdk.dir=...`.
- macOS: bare `./gradlew` does **not** find Java even if Android Studio is installed. Export the bundled JBR first:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
  The helper scripts (`scripts/*.sh`) do this fallback themselves; direct `./gradlew` invocations do not.
- Termux/Linux: set `JAVA_HOME` to a JDK 17+ and create `local.properties` with the SDK path. Use the same Gradle commands below. The release script defaults its APK copy destination to `/storage/emulated/0/Download` on non-macOS systems.
- Keep `scripts/*.sh` bash 3.2 compatible: no `mapfile`, no `${var^}`, etc. macOS system bash is used.
- Keep `versionName = "x.y.z"` and `versionCode = N` as single-line literals in `app/build.gradle.kts`; CI and `scripts/build-pocketweibo-release.sh` parse them with `sed`.
- Do not upgrade the dependency graph casually. `androidx.core` is intentionally forced to 1.12.0 to stay compatible with compileSdk 34.

## Commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS direct Gradle only

./gradlew test assembleDebug          # required verification before commit
./gradlew :app:testDebugUnitTest --tests 'com.pocketweibo.ui.util.ComposeContentTest'  # focused test
./gradlew assembleRelease             # when touching signing, app/build.gradle.kts, or CI
./gradlew :app:connectedDebugAndroidTest  # needs a connected device; not part of the normal check

./scripts/build-pocketweibo-release.sh [--no-copy]  # signed release; needs secrets/github-keystore-secrets.local.txt
./scripts/adb-wireless.sh status
./scripts/adb-wireless.sh connect [ip]              # auto-discovers the dynamic port and caches it
./scripts/adb-wireless.sh install-debug
./scripts/adb-wireless.sh install-release
```

- Use `install-release` when the phone already has a release-signed build; debug and release signatures cannot upgrade over each other.
- CI (`.github/workflows/build-apk.yml`) only builds an APK; it does **not** run tests or lint. A green CI run does not replace `./gradlew test assembleDebug`.
- Pushing to `main` or `apk-build` triggers a workflow that publishes a signed GitHub release when signing secrets are present. Do not push unless the user asks.

## Shipping workflow (mandatory for this repo)

- One commit per user request; do not bundle unrelated changes.
- Bump `versionCode` by 1 for every shippable change. Bump `versionName`: MINOR for a new user-facing feature, PATCH for a fix/small correction.
- Record user-facing features in `docs/FEATURE.md` (next `F-xxx` row) and notable fixes in `docs/FIX.md`. The ledgers are written in Chinese; match the existing table style.
- Verify with `./gradlew test assembleDebug` before committing. Also run `./gradlew assembleRelease` when touching signing, `app/build.gradle.kts`, or CI.
- Never commit `local.properties`, `secrets/*`, keystores, passwords, or generated build output. `secrets/README.md` documents the signing layout.

## Data and migration gotchas

- Room database is version 12 in `AppDatabase.kt`, with `exportSchema = false` and **no** destructive fallback. Any entity/schema change needs a new migration in `DatabaseMigrations.kt` and must be registered in `AppDatabase`.
- Do not use `OnConflictStrategy.REPLACE` for identity inserts. SQLite deletes the old row first, which has caused posts/comments to be cascade-deleted in this project; use insert/update paths instead.
- Posts and comments keep their content when an identity is deleted (`identityId` becomes null); comments cascade when their post is deleted.
- Media paths are stored relative to app-private storage (`filesDir`), not absolute paths. Backups and imports assume this layout.
