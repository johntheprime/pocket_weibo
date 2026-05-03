# PocketWeibo — project rules for features, fixes, and releases

These rules apply whenever you ship a **new feature** or a **fix**. Keep the app **bilingual** (Chinese default + English) and keep **versioning** aligned with the kind of change.

---

## 1. Bilingual app (required)

PocketWeibo is a **bilingual** UI: default locale strings live in `app/src/main/res/values/strings.xml`; English in `app/src/main/res/values-en/strings.xml`. Per-app language is controlled in settings (`UiPreferences` / `AppCompatDelegate.setApplicationLocales`).

**For every user-visible change:**

- Add or update the **same string keys** in **both** `values/strings.xml` and `values-en/strings.xml`. Do not leave English-only or Chinese-only keys used by the UI.
- Prefer `stringResource(R.string.*)` in Compose and `context.getString(R.string.*)` where a `Context` is available.
- Covers **all** UI chrome: titles, tabs, placeholders, content descriptions, toasts, dialogs, empty states, relative times, clipboard labels, markdown export headings/labels, etc. (Seed/demo **data** in `DataSeeder.kt` does not need translation.)

**Parity check:** any `R.string.*` referenced in code must exist in **both** resource files before you merge.

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

1. Strings: **zh + en** parity for all new/changed UI.
2. Version: bump **minor** or **patch** (and **versionCode**) per section 2.
3. Build: `./gradlew test assembleDebug` (and `assembleRelease` when touching release/signing behavior).
4. Product log: add or update a row in `FEATURE.md` for notable features; use `FIX.md` for notable fixes if that is the project convention.

---

## 4. Where this is enforced for Cursor

`.cursor/rules/pocket-weibo-shipping.mdc` is set to **always apply** so agents load these expectations on new features and fixes. This file remains the full reference.
