# PocketWeibo — project rules for features, fixes, and releases

These rules apply whenever you ship a **new feature** or a **fix**. Keep the app **bilingual** (Chinese default + English) and keep **versioning** aligned with the kind of change.

---

## 0. Workflow for every request (fix, feature, or both)

Treat **each** user request as one delivery unit. **Always** follow this sequence end to end; do not skip verification or logging.

| Step | What to do |
|------|------------|
| **1. Plan** | Restate the goal, scope (files/areas), and risks. For multi-step work, use a **todo list** in the editor and keep it updated. |
| **2. Implement** | Make the smallest change that satisfies the request; match existing patterns (`RULES.md`, nearby code). |
| **3. Verify** | Run **`./gradlew test assembleDebug`** (and `assembleRelease` when signing/release behavior changes). **Fix failures before you commit.** A passing build is required to treat the request as done. |
| **4. Record** | Update **`FEATURE.md`** for user-facing product changes and **`FIX.md`** for notable fixes, per the tables/workflows in those files. Bump **`versionName` / `versionCode`** in `app/build.gradle.kts` when shipping (see §2). |
| **5. Commit** | Create **one git commit per user request** (one fix, one feature, or one combined fix+feature if the user asked for both together). Do **not** mix unrelated requests into the same commit. The commit should reflect a **green** verify step. |
| **6. Todos** | Mark planning/todo items **completed** (or cancelled if dropped) before ending the session so the next session sees an accurate state. |

**Mixed fix + feature:** If a single request includes both, one commit covering that request is correct. If the user sends **separate** requests, use **separate** commits (each passing tests).

See also the checklist in **`FIX.md`** (“Workflow for every request”) for the human-facing ledger style.

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

Aligns with **§0**; use this as a quick pass before commit:

1. Strings: **zh + en** parity for all new/changed UI.
2. Version: bump **minor** or **patch** (and **versionCode**) per section 2.
3. Build: **`./gradlew test assembleDebug`** green (and `assembleRelease` when touching release/signing behavior).
4. Product log: **`FEATURE.md`** and/or **`FIX.md`** updated when the change is user-visible or notable.
5. **Git:** one commit per request, after tests pass.

---

## 4. Where this is enforced for Cursor

`.cursor/rules/pocket-weibo-shipping.mdc` is set to **always apply** so agents load these expectations on new features and fixes. This file remains the full reference.
