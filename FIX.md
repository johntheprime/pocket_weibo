# Fix log and change workflow

This file records **resolved requirements** and the **standard process** for every change (human or agent), so work stays traceable and releasable.

---

## Fixed requirements (ledger)

| Date (UTC) | Requirement | Resolution | Verified |
|-------------|---------------|------------|----------|
| 2026-05-08 | **写微博 · 提醒**：选好提醒时间后应 **直接发布**，不必再点「发送」 | **`ComposeScreen`**：`ReminderPickerDialog.onScheduleAt` 在有效时间下 **关闭对话框** 并 **`performSend()`**（与发送按钮相同前提）；不可发布时保留 **`pendingComposeReminder`** 并 Toast 提示补全后手动发送。更新 **`compose_remind_*`** 中英文案；`FEATURE.md` **F-047**。版本 **3.54.0 (185)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-08 | **评论**：写 **文字评论** 时不应再看到 **麦克风**（与文字输入抢注意力） | **`CommentVoiceComposerBar`**：`OutlinedTextField` 使用 **`collectIsFocusedAsState`**；**聚焦或正文非空** 时 **不渲染** 麦克风；**正文空且未聚焦** 时显示（纯语音入口）；**正在录音** 时 **始终显示** 麦克风以便停录。`FEATURE.md` **F-057** 同步。版本 **3.53.2 (184)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-08 | **写微博**：去掉 **语音 / 麦克风** 入口，发帖仅支持 **文字与配图**（双拍空白、摇一摇发送等逻辑同步不含语音）；**评论语音不变**。 | **`ComposeScreen`**：移除底栏麦克风、`VoiceRecordingController`、待发语音 UI 及发帖流程中的语音附件；`DisposableEffect` 仅清理临时图片。版本 **3.53.1 (183)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-07 | **评论语音**：开始录音后 **无界面提示**、过程中看不出在录 | **`CommentVoiceComposerBar`**：录音中显示 **浅红脉冲横幅**（「正在录音…」「点击结束」+ 三点动画）、麦克风 **圆形底 + 强调色**；`preparedVoice` 与录音中互斥展示；横幅 **`comment_voice_recording_semantics`**。版本 **3.52.2 (181)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-07 | **评论 / 写微博语音**：界面像在录音，但 **保存后无声音**；启动偏慢 | **根因**：`VoiceRecordingController.stopRecording` 要求 **≥700 ms 且 ≥4 KiB**；短句或刚停时 **M4A 未写完** 常被误判丢弃。另：权限弹窗返回后 **同一帧** 调 `MediaRecorder.start` 在部分机型上不可靠。**修复**：降至 **≥420 ms、≥512 B**；`stop` 后 **短轮询** 等文件落盘；显式 **单声道 / 44.1 kHz / 96 kbps**；`MODE_IN_COMMUNICATION` 路由；**`startRecordingOnNextMainFrame`**（主线程下一帧）用于授权回调与再次点麦。版本 **3.52.1 (180)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-07 | **图片 Tab**：**竖长配图** 在流里被 **上下裁切**，不能完整浏览 | **根因**：固定行高 + **`ContentScale.Crop`** 为铺满宽度会裁掉过长部分。**修复**：按 **解码后 intrinsic 尺寸** 与 **卡片宽度** 计算行高（**min～max** 夹逼），改用 **`ContentScale.Fit`**，暗底填边；极竖长图在 **最大行高** 内仍 **整图可见**。同版 **F-056** 双击还原缩放。版本 **3.51.0 (177)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-07 | **图片 Tab**：`LazyColumn` **竖滑不如加缩放前顺滑**（手指从 **配图** 起滑时尤甚） | **根因**：`detectTransformGestures` 在 **单指** 移动超过 touch slop 后即 **consume** 位移，**配图上的竖滑** 无法交给外层列表。**修复**：`PhotoScreen` 使用 **`detectPhotoFeedTransformGestures`**：在 **约 1×** 时仅当 **捏合 / 旋转 / 双指拖动** 过 slop 才接管；**已放大** 时与原先一致（单指可 **拖移图**）。保留 **`nestedScroll`** 在放大时减少列表抢滑。版本 **3.50.1 (176)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-06 | **CI**：`sdkmanager` 中 **platform / build-tools 版本** 写死，易与 **`app/build.gradle.kts`** 的 **compileSdk** 漂移 | **`.github/workflows/build-apk.yml`**：在 **`build` job** 设 **`ANDROID_SDK_COMPILE_API`**、**`ANDROID_SDK_BUILD_TOOLS`**，`sdkmanager` 引用环境变量；工作流头注释说明与 **compileSdk** 对齐。版本 **3.42.1 (167)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-05 | **发微博**：正文过长时 **不随键盘上移**，内容被键盘挡住、不便编辑 | **`ComposeScreen`** 根 **`Surface`** 增加 **`imePadding()`**；正文与身份/配图卡片区放入 **`Modifier.weight(1f).verticalScroll`**，底部图片/相机/@ **工具栏** 固定在其下，键盘弹出时可 **滚动查看全文**。版本 **3.38.4 (159)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-05 | **删除身份无 UI 入口**：列表项传入 **`onDelete`** 却从未调用，用户无法删除身份 | **`IdentityListItem`** 行尾增加 **`IconButton`(删除)**，确认框不变；**`IdentityDetailScreen`** 在已有身份时顶栏 **⋮** 菜单 **删除身份** + 同一确认文案。更新 **`identity_delete_message`** 说明微博与评论保留；**`identity_delete_cd` / `identity_detail_more_cd`**。`FEATURE.md` **F-040** 补入口说明。版本 **3.38.3 (158)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-05 | **设置 · 导出数据**：备份成功后 **导出对话框应自动关闭** | 在 **`MeSettingsScreen`** 导出协程成功路径（分享面板已唤起）后设置 **`showExportDialog = false`**；失败时 **`toast_export_fail`**（中英）且不关对话框。版本 **3.38.2 (157)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-05 | **F-040** 升级后 **应用启动崩溃**（6→7 迁移） | **根因**：`comments_new` 外键指向 **`posts_new`**，随后将 **`posts_new` 重命名为 `posts`**，SQLite 仍保留对已不存在父表名的引用，数据库无法正常打开。**修复**：6→7 改为先将 **`comments` 拷入临时表**、替换 **`posts`** 后再建 **`comments_new` 且 `REFERENCES posts(id)`**；新增 **7→8** **`MIGRATION_7_8`** 重建 **`comments`**，修复已执行过错误脚本的用户库。`AppDatabase` **version 8**。版本 **3.38.1 (156)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | **MainActivity** 调用 **`ComposeScreen(onPostPublished = …)`** 但 **ComposeScreen** 未声明该参数，**工程无法编译** | 为 **ComposeScreen** 增加 **`onPostPublished`**（默认空实现），发帖成功后 **`onPostPublished()`** 再 **`onDismiss()`**。版本 **3.37.2 (154)**（与 **F-039** 同批）。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 保存 **身份**（含头像）后 **该身份下微博全部消失** | **根因**：`IdentityDao.insert` 曾用 **`OnConflictStrategy.REPLACE`**；SQLite 先 **DELETE** 旧行再 INSERT，触发 `posts.identityId` 的 **ON DELETE CASCADE**，帖子被级联清空。`saveIdentityWithAvatarOptions` 第二次写入也曾 **`insert`**。**修复**：`insert` 改为普通 INSERT；已有 id 走 **`update`**；合并导入里自定义头像收尾改为 **`update`**。版本 **3.36.1 (151)**。**已丢数据** 需依赖用户此前 **导出备份** 重新导入，应用内无法从空库恢复。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
| 2026-05-04 | 身份 **萌系男卡通** 预设未在编辑页突出；**相册自定义头像** 未接入保存（仍调用 `insertIdentity`，无选图入口） | `IdentityDetailScreen`：`PickVisualMedia` 选图、**自定义 / 男卡通 / 更多预设** 分区、`saveIdentityWithAvatarOptions` + 移除照片；头图与 **`Avatar`** 一致。**`IdentityListScreen`** 行内改用 **`Avatar`**（含 `customAvatarUri`）。`FEATURE.md` **F-037**。版本 **3.36.0 (150)**。 | `./gradlew test assembleDebug` — BUILD SUCCESSFUL |
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
