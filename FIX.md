# Fix log and change workflow

This file records **resolved requirements** and the **standard process** for every change (human or agent), so work stays traceable and releasable.

---

## Fixed requirements (ledger)

| Date (UTC) | Requirement | Resolution | Verified |
|-------------|---------------|------------|----------|
| 2026-05-04 | **我** 页 **待处理提醒** 不应压在 **身份简介**（国籍/职业/代表作）之上；**固定资料在上、可变列表在下** | `MeScreen` `LazyColumn` 将 **简介 `InfoRow` 区块** 移到 **待处理提醒** `item` **之前**（顺序：身份卡片 → 简介 → 提醒）。版本 **3.35.3 (149)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 从 **微博详情** 返回或 **切换 Tab** 再回首页时，不应再出现 **下拉刷新动效**（仅应在 **双击首页 Tab** / **发帖成功回顶** 时播放） | `HomeScreen` 的 `LaunchedEffect(scrollToLatestSignal)` 在离开再进入组合时会 **带着旧的正数 signal 重跑**；在 `MainScreen` 增加 **`homeScrollToLatestConsumed`**，仅在 **`scrollToLatestSignal > scrollToLatestConsumedSignal`** 时播放动效并在结束后 **`onScrollToLatestConsumed`**。版本 **3.35.2 (148)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 发博成功后 **首页列表不“刷新”**（新帖在顶部，列表仍停在旧滚动位置） | `ComposeScreen` 增加 **`onPostPublished`**；`MainActivity` 在发帖成功回调里 **`selectedTab = HOME`**、**`homeListState.scrollToItem(0)`** 并递增 **`homeScrollToLatestSignal`**（与首页双击 Tab 一致的下拉动效 + 清筛选）。版本 **3.35.1 (147)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 提醒 **点按通知进详情后通知消失**；**我** 页需集中查看 **待处理** | **通知**：`PostReminderReceiver` 使用 **`setAutoCancel(false)`**（可选 **`setOnlyAlertOnce(true)`**），点按打开帖子后 **仍留在通知栏** 直至用户划掉。**我**：`observePendingRemindersWithPreview` + 列表（与资料区同 **LazyColumn** 滚动、限高 **verticalScroll**）、**取消** / **点行进详情**、约 **30s** 刷新剩余时间文案。`FEATURE.md` F-019 同步。版本 **3.35.0 (146)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | 提醒对话框 **重复选项占屏**；小时文案改 **时**；杀进程后 **不响** | **UI**：默认 **一次**，重复迁入「重复提醒（可选）」展开；快捷时长 **单行横向滚动**。**文案**：中文「小时」改为 **时**（含 Me 相对文案 plurals）。**可靠性**：`Application.onCreate` 内 **`runBlocking(IO)`** 先完成诊断开关 + **`rescheduleAllPostRemindersFromDb()`**，再投递 `BroadcastReceiver`，避免 **补投递与闹钟回调并发** 竞态。说明文案区分 **划掉后台** 与 **强制停止**（后者会清闹钟）。版本 **3.27.1 (138)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 诊断开启仍 **无日志** / 提醒似未启动；需更 **稳妥** 的提醒 | **原因**：`DiagnosticLogBuffer` 在 `Application` 里 **异步** 恢复，首屏调度可能早于 `captureEnabled=true`；系统重启或杀进程后 **AlarmManager 与 DB 不同步**。修复：`runBlocking(IO)` 同步读诊断开关；**每次进程启动** `rescheduleAllPostRemindersFromDb()`；`PostReminderBootReceiver` 处理 **BOOT_COMPLETED**、**MY_PACKAGE_REPLACED**；**过期** 提醒行 **立即 `sendBroadcast`** 补投递；`setAlarmClock` **`SecurityException`** 时回退 **`setExactAndAllowWhileIdle`**；`PendingIntent` **requestCode** 改为 `(reminderId, postId)` 哈希。版本 **3.22.1 (132)**；`FEATURE.md` F-019；`PostReminderAlarmSchedulerTest`；升级后 **取消旧式 PendingIntent** 避免双闹钟。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 已授权通知仍 **无提醒**；需 **3 分钟验证**、**Me 待处理列表**、**日志** | **根因**：`notify()` 失败被吞后仍 **`deleteById`**，提醒从 DB 消失却无通知；通知 id 可能为负。修复：`PostReminderNotificationIds.notifyId`（非负）；**仅成功 `notify` 后**删库行；`PW_Reminder` **Logcat**（`onReceive`、调度、`notify` 成败）。`PostReminderAlarmScheduler` / `WeiboRepository.schedulePostReminder` 打调度日志。详情增加 **3 分钟后（验证）**。**我** 页 `observePendingRemindersWithPreview` 列表 + 取消 + 进详情。单元测试 `PostReminderNotificationIdsTest`。版本 **3.21.0 (129)**；`FEATURE.md` F-019。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 微博详情 **本地提醒** 不触发；提醒时长选项需改为 **每 30 分钟一档**（并保留 **15 分钟**） | **原因**：Android 13+ 未授予 **POST_NOTIFICATIONS** 时 `notify` 抛错/无提示；旧通知渠道为 **IMPORTANCE_DEFAULT** 且系统不自动升重要性。`PostDetailScreen`：选时长前请求通知权限，拒绝时 Toast 说明；修正 `scheduleReminder` lambda 的非法 `return@` 标签。`MyWeiboApp`：新渠道 `post_reminders_high`（HIGH + 振动），并删除旧 `post_reminders`。`PostReminderReceiver`：`ic_stat_reminder` 小图标、`CATEGORY_REMINDER`、捕获非 `SecurityException` 的 `notify` 失败。详情提醒对话框：**15 分钟**快捷项 + **48 档（30 分钟～24 小时）**。`FEATURE.md` F-019 同步；版本 **3.20.1 (128)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | 发微博配图压缩观感差；需在发布流程支持 **拍照** 加图 | `PostAttachmentStorage`：大图（>约 1.5 MB，非 GIF）改为 **高质量 JPEG**（最长边 2560、质量 92，`BitmapFactory` 采样 + 缩放）；若编码后不小于原文件则回退为原拷贝。`ComposeScreen`：`TakePicture` + `FileProvider` + `CAMERA` 权限。`androidx.core` 仍强制 **1.12.0**（避免传递依赖要求 compileSdk 36）。`FEATURE.md` F-008 / F-022 同步。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 去掉评论 48h 灰字说明、写微博「摇一摇」「本帖原图」说明性副文案，减少视觉干扰 | `CommentBottomSheet`：超窗仅不显示删除，移除 `comment_delete_locked_hint`。`ComposeScreen`：移除 `compose_shake_hint` 与 `compose_per_post_original_subtitle`；原图行保留标题 + `Switch`。删除对应 `strings.xml` / `values-en` 键。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | 他人发评论后当前身份多出一条「自己的」评论或评论串错乱 | **HomeViewModel / PostDetailViewModel** 的 `addComment` 误用 `activeIdentity.collect`，每次 Flow 发射都会再 `insertComment`，产生多条同一正文、归属当前身份的记录；**PostDetailViewModel.loadPost** 每次进入未取消旧的 `allPosts` / `getCommentsByPost` 收集协程，多帖并发写同一 `_comments`。已改为 `activeIdentity.first()` 单次插入；评论收集用 `Job` 在 `openComments`/`loadPost`/`closeComments`/`onCleared` 取消。`WeiboRepository.insertComment` 改为返回 `commentDao.insert` 的真实 row id。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | **我** page: settings icon opens **设置**; move import/export there; **关于** with version & links. | [FEATURE.md](FEATURE.md) F-004. `WeiboTitleBar.onRightIconClick`, `MeSettingsScreen`, `MainActivity` overlay `showMeSettings`. | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | Long-press copy for **comments** and selectable copy for **posts**; track new product work in **FEATURE.md** with todo → test → done → push. | See [FEATURE.md](FEATURE.md) F-001–F-003. `ClipboardUtils`, `SelectablePostBody`, `SelectableCopyDialog`; updates in `PostCard`, `PostDetailScreen`, `DiscoverScreen`, `MessageScreen`, `CommentBottomSheet`. | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-03 | CI APKs must **upgrade in place** (same Android signature); avoid data loss from uninstall/reinstall. Use **semantic versioning** and bump **versionCode** each release. | CI runs `assembleRelease` with `keystore.properties` + `ci-release.keystore` decoded from **GitHub Actions secrets** (fixed keystore). `app/build.gradle.kts`: `versionName` semver (e.g. `3.0.0`), `versionCode` monotonic (`100`). APK artifact renamed `pocket-weibo-release-v…`. See **CI release signing** below. | `./gradlew assembleDebug` and `./gradlew assembleRelease` — BUILD SUCCESSFUL |
| 2026-05-03 | GitHub Actions: **each** successful publish should create a **new** release (do not reuse/overwrite one rolling tag). APK **filename** must include an app **version** (from Gradle). | Same workflow file; release APK naming + unique tag per run. | Same as above |
| 2026-05-03 | Discover search: tapping a **微博** search result opens that post’s **detail and comments** (same as home / trending). | Wired `onPostClick` through `SearchResultsContent` → `PostSearchItem`; row uses `Modifier.clickable` and navigates via `postDetailId` → `PostDetailScreen`. File: `app/src/main/java/com/pocketweibo/ui/screens/discover/DiscoverScreen.kt`. | `./gradlew assembleDebug` — BUILD SUCCESSFUL (confirmed before commit) |
| 2026-05-03 | Maintain **FIX.md** as the fix ledger and **plan → implement → verify → record → ship** checklist for all future work. | Added this file; links CI expectation to Gradle builds. | Same build as above |

_Add new rows above this line for each shipped fix._

---

## Planned backlog (historical)

**F-010–F-020** 已全部在 **FEATURE.md** 标为 **Done** 并已合入 `apk-build`（见 `docs/F010-F020-rollout-log.md` 与对应 git 提交）。后续新需求请重新在 **FEATURE.md** 开行。

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

### Local signed APK (same inputs as CI)

Create **`github-keystore-secrets.local.txt`** at the repo root (gitignored) with at least **`KEYSTORE_PASSWORD`** (and optional `KEY_PASSWORD` / `KEY_ALIAS`). For **`ANDROID_KEYSTORE_BASE64`**, use any one of: a **companion file** `github-keystore-secrets.local.ANDROID_KEYSTORE_BASE64` (same prefix as the `.txt`, gitignored), or **`ANDROID_KEYSTORE_BASE64_FILE=path`** to a base64-only file under the repo, or an inline **`ANDROID_KEYSTORE_BASE64=`** single line. See **`github-keystore-secrets.local.txt.example`**.

Then run **`scripts/build-pocketweibo-release.sh`**: it decodes the keystore to `ci-release.keystore`, writes `keystore.properties`, runs `./gradlew assembleRelease`, renames the APK under the repo root, then by default **`cp SOURCE DEST`** into `/storage/emulated/0/Download` and **removes the root copy** so only the copied file remains (`--no-copy` keeps the APK in the repo root only). Root `*.apk` files are gitignored.

- If a change is **reverted** or **superseded**, add a short note in the ledger row or a new row pointing to the follow-up fix.
