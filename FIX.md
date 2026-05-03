# Fix log and change workflow

This file records **resolved requirements** and the **standard process** for every change (human or agent), so work stays traceable and releasable.

---

## Fixed requirements (ledger)

| Date (UTC) | Requirement | Resolution | Verified |
|-------------|---------------|------------|----------|
| 2026-05-03 | **我** page: settings icon opens **设置**; move import/export there; **关于** with version & links. | [FEATURE.md](FEATURE.md) F-004. `WeiboTitleBar.onRightIconClick`, `MeSettingsScreen`, `MainActivity` overlay `showMeSettings`. | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | Long-press copy for **comments** and selectable copy for **posts**; track new product work in **FEATURE.md** with todo → test → done → push. | See [FEATURE.md](FEATURE.md) F-001–F-003. `ClipboardUtils`, `SelectablePostBody`, `SelectableCopyDialog`; updates in `PostCard`, `PostDetailScreen`, `DiscoverScreen`, `MessageScreen`, `CommentBottomSheet`. | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | CI APKs must **upgrade in place** (same Android signature); avoid data loss from uninstall/reinstall. Use **semantic versioning** and bump **versionCode** each release. | CI runs `assembleRelease` with `keystore.properties` + `ci-release.keystore` decoded from **GitHub Actions secrets** (fixed keystore). `app/build.gradle.kts`: `versionName` semver (e.g. `3.0.0`), `versionCode` monotonic (`100`). APK artifact renamed `pocket-weibo-release-v…`. See **CI release signing** below. | `./gradlew assembleDebug` and `./gradlew assembleRelease` — BUILD SUCCESSFUL |
| 2026-05-03 | GitHub Actions: **each** successful publish should create a **new** release (do not reuse/overwrite one rolling tag). APK **filename** must include an app **version** (from Gradle). | Same workflow file; release APK naming + unique tag per run. | Same as above |
| 2026-05-03 | Discover search: tapping a **微博** search result opens that post’s **detail and comments** (same as home / trending). | Wired `onPostClick` through `SearchResultsContent` → `PostSearchItem`; row uses `Modifier.clickable` and navigates via `postDetailId` → `PostDetailScreen`. File: `app/src/main/java/com/pocketweibo/ui/screens/discover/DiscoverScreen.kt`. | `./gradlew assembleDebug` — BUILD SUCCESSFUL (confirmed before commit) |
| 2026-05-03 | Maintain **FIX.md** as the fix ledger and **plan → implement → verify → record → ship** checklist for all future work. | Added this file; links CI expectation to Gradle builds. | Same build as above |

_Add new rows above this line for each shipped fix._

---

## Workflow for every request (plan → implement → verify → record → ship)

1. **Plan** — Restate the goal, list files or areas to touch, and note risks (navigation, DB, CI).
2. **Implement** — Make the smallest change that satisfies the requirement; match existing patterns.
3. **Verify** — Run `./gradlew assembleDebug`. If you changed signing, `app/build.gradle.kts`, or CI, also run `./gradlew assembleRelease` (local release may be unsigned without `keystore.properties`; CI signs with secrets). Fix failures before recording “done”.
4. **Record** — Add or update a row in **Fixed requirements (ledger)** above; set **Verified** to the exact command and outcome (e.g. `BUILD SUCCESSFUL`).
5. **Ship** — Commit with a clear message and push to the branch used for CI (e.g. `apk-build`).

### Checklist (copy for each task)

- [ ] Plan written (goal + scope)
- [ ] Code / resources updated
- [ ] `./gradlew assembleDebug` succeeds (and `assembleRelease` when relevant)
- [ ] **FIX.md** ledger row added or updated (bugs); **FEATURE.md** updated for product/UX features
- [ ] Committed and pushed

---

## Notes

- **Product features** (new behavior, UX) are tracked in [FEATURE.md](FEATURE.md) with a **todo → implement → test → mark done → commit/push** flow.
- **APK on GitHub:** Pushes to `apk-build` run [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml). CI builds a **signed release** APK so upgrades keep app data. The workflow **fails fast** if signing secrets are missing (see workflow file header).
- **Semantic version:** Maintain `versionName` as `MAJOR.MINOR.PATCH` in `app/build.gradle.kts`. Increase **`versionCode` by at least 1** before every release you want Android to accept as an upgrade over the previous CI APK.
- **One-time migration:** APKs built earlier on CI with the **default debug** key cannot upgrade to the new signed release; uninstall once, install the new APK, then future CI builds upgrade normally.

## CI release signing (repository secrets)

Create a keystore **once** and add it to GitHub (**Settings → Secrets and variables → Actions**):

| Secret | Required | Meaning |
|--------|----------|---------|
| `ANDROID_KEYSTORE_BASE64` | Yes | `base64 -w0` (Linux) of your `.keystore` / `.jks` / `.p12` file |
| `KEYSTORE_PASSWORD` | Yes | Keystore password |
| `KEY_PASSWORD` | No | Defaults to `KEYSTORE_PASSWORD` if omitted |
| `KEY_ALIAS` | No | Defaults to `pocketweibo` (must match the alias in the keystore) |

Example key generation (PKCS12, alias `pocketweibo`):

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore pocket-weibo-release.keystore \
  -alias pocketweibo -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 pocket-weibo-release.keystore   # paste value into ANDROID_KEYSTORE_BASE64
```

- If a change is **reverted** or **superseded**, add a short note in the ledger row or a new row pointing to the follow-up fix.
