# Product features (backlog and shipped)

Use this file for **new product behavior** (not small bugfixes; those go in [FIX.md](FIX.md)).

---

## Workflow for every new feature

1. **Plan** — Add a row to the table below with status **Todo** (or unchecked `[ ]` in checklist form).
2. **Implement** — Ship the smallest change that matches the row; reuse existing patterns (`ClipboardUtils`, `SelectableCopyDialog`, etc.).
3. **Test** — Run `./gradlew test assembleDebug` (and `assembleRelease` if you touched signing or release build). Manually try the flows on a device or emulator when UI behavior is involved.
4. **Mark done** — Set status to **Done** only after tests pass and the behavior matches the row.
5. **Commit & push** — One commit per logical feature (or a tight group). Do not push until the **Test** step is green.

### Quick checklist (copy per feature)

- [ ] Row added here with status **Todo**
- [ ] Implemented
- [ ] `./gradlew test` and `./gradlew assembleDebug` succeed
- [ ] Status set to **Done**
- [ ] Committed and pushed

---

## Feature log

| ID | Feature | Status |
|----|---------|--------|
| F-001 | **评论长按复制**：在评论列表/详情/底部评论表中，长按一条评论将**整条评论正文**复制到剪贴板并提示。 | Done |
| F-002 | **微博长按选择复制**：在首页/我的发布卡片、发现页热门与搜索结果、微博详情中，长按微博区域可**选择部分正文并复制**（详情页正文为内联可选中；列表/发现为对话框内 `SelectionContainer`）。 | Done |
| F-003 | **消息页评论长按**：在「消息」收到的评论/发出的评论列表行上长按，复制该条**评论正文**。 | Done |
| F-004 | **我 · 设置**：右上角设置图标可进入**设置**页；**导入/导出**迁入设置；**关于本软件**展示版本号、简介、作者/贡献说明与 **GitHub** 相关链接。 | Done |
| F-005 | **中英界面**：默认中文 `values`；英文 `values-en`；设置中可选**跟随系统 / 简体中文 / English**（`UiPreferences` + `AppCompatDelegate.setApplicationLocales`）；主要界面文案与相对时间、剪贴板、导出 Markdown 标签等均走资源。 | Done |
| F-006 | **摇一摇发微博**：在写微博界面，除点「发送」外，**用力摇一摇**也可发布；通过输入停顿、进入页面暖机、发送冷却、线性加速度/加速度阈值与短时多脉冲识别，降低走路打字时的误发。 | Done |
| F-007 | **微博配图与备份**：发布时保存配图到应用私有目录并在首页/详情/发现等展示；导出支持 **ZIP（data.json + post_attachments）** 与原有 JSON/Markdown；导入支持 JSON 文本与 ZIP；数据库增加 `extrasJson` 预留扩展（如可见范围）。 | Done |
| F-008 | **配图体积控制**：超过约 **1.5 MB** 的待存图片（非 GIF）经 **高质量 JPEG** 重编码（最长边 **2560**、质量 **92**，参见 **F-022**）；**发微博**可从 **拍照** 添加图片（经同一预处理）；可按本条开启 **原图（不压缩）**（仅影响本条中新添加的图片）。 | Done |
| F-009 | **配图体验**：写微博选图后在后台 **预先处理**（压缩/复制），发送时仅移动文件并 **防重复发送**（发送中/处理中禁用按钮与摇一摇）；详情页配图可 **全屏查看**（滑动换图、双指缩放；**单击 / 双击** 手势见 **F-021**）。 | Done |
| F-010 | **主导航与首页控件**：首页 **下拉**、**搜索**、**首页** 按钮、**更多** 等当前无实际行为 — 实现明确能力或（短期）改为隐藏/禁用并附说明，避免永久“死控件”。 | Done |
| F-011 | **消息页定位**：将「消息」改为 **仅当前身份相关**（收到/发出的评论与 @ 等），或经产品确认后 **移除入口**；若保留，统一空态与导航文案。 | Done |
| F-012 | **评论删除时间窗**：评论 **删除** 仅在发布后 **48 小时内**可用；超时后 **仅隐藏删除**（无额外说明文案；规则仍基于 `createdAt` 与设备时间）。 | Done |
| F-013 | **消息页 UI**：去掉消息页上 **异常的笔形图标**（若无对应功能则移除；若有草稿入口则换为明确文案/图标）。 | Done |
| F-014 | **发微博 · 位置与 @**（历史）：曾实现 **@** 与 **位置**；**位置** 已按 **F-023** 移除，本条仅保留 **@** 能力说明。 | Done |
| F-015 | **我 · 身份可编辑**：在 **我** 页点击 **当前身份** 进入与身份列表一致的 **编辑**（名称、头像等），保存后全局刷新展示。 | Done |
| F-016 | **发帖时间展示增强**：在卡片与详情中，在现有相对/绝对时间旁增加 **星期**（如「周一」/ `EEE`），并走 **中英资源** 与设置语言一致。 | Done |
| F-017 | **从其他应用分享文本为新微博**：注册 `ACTION_SEND` / `text/plain`（及合理 MIME 变体），将分享正文 **预填写微博** 并进入发布流程；处理超长文本与草稿冲突策略。 | Done |
| F-018 | **评论排序**：评论列表统一为 **最新在上**（按创建时间降序）；确认详情页、底部评论表、消息列表等所有入口一致。 | Done |
| F-019 | **提醒 / 闹钟**：为单条内容设置 **系统闹钟**（`AlarmManager.setAlarmClock` + `Notification`，声明 **`SCHEDULE_EXACT_ALARM`**）；详情 ⋮ **设置提醒**：快捷 **15 / 30 分钟、1 / 3 / 6 小时、明天 9:00** 与 **自选日期时间**（任意未来日）；文案说明 **系统调度**、**精确闹钟** 与 **电池优化** 入口；**我** 页 **待处理提醒** 列表；通知标题 **不含「微博」**；**DB 为准** + **开机 / 更新** 重挂、过期补投；**Android 13+** 通知权限；**F-024** 诊断导出。 | Done |
| F-020 | **微博详情 · 右上角菜单**：详情页 **⋮** 提供 **删除微博**（二次确认）、以及其它合适操作（如 **编辑**（若数据模型支持）、**复制全文**、**导出/分享** 等按现有能力迭代）。 | Done |
| F-021 | **详情附图预览手势（单击 / 双击）**：详情缩略图 **单击** 打开全屏预览；预览内 **双击** 在约 **1×** 与约 **2.5×** 间切换，放大以 **触点为缩放中心**（非始终屏幕中心；双指缩回约 1× 时恢复中心锚点）；预览内 **单击** 关闭（仍保留关闭按钮与左右切图、双指缩放）。 | Done |
| F-022 | **配图压缩质量优先**：大图用 **BitmapFactory** 采样解码 + 必要时缩放，输出 **JPEG 质量 92**、最长边 **2560**；若输出体积 **不小于** 原文件则放弃重编码、保留原拷贝；**小于约 1.5 MB** 或 **GIF** 不转码。 | Done |
| F-023 | **移除发微博「位置」**：去掉写微博区 **位置** 图标与条、**定位权限**声明与请求；新帖不再写入 `extrasJson.location`（`extrasJson` 列仍保留作其它扩展）；**@** 与配图等不变。 | Done |
| F-024 | **设置 · 诊断日志导出**：设置中可开启 **内存诊断捕获**（提醒等 `DiagnosticLog` 写入环形缓冲），**导出** 为文本经系统分享保存；**清空** 或关闭开关即停止累积（缓冲不跨进程持久化）。 | Done |
| F-025 | **提醒 · 重复**：详情 **设置提醒** 可选 **一次 / 每天 / 每周 / 每月**（`post_reminders.repeatRule`）；到时通知后 **更新 `fireAtMillis` 并重挂** 下一响，取消仍删行。 | Done |
| F-026 | **列表返回保持滚动位置**：`MainScreen` 为 **首页 / 发现（热门与搜索各一）/ 我的发布** 持有 `LazyListState`，从帖子详情返回时 **不丢列表滚动位置**。 | Done |
| F-027 | **我页与首页信息架构**：**我** 页顶部为简介与资料区；**身份管理** 迁入 **设置** 首项；**我的发布** 从 **我** 移除，改在 **首页** 标题栏中部入口；**待处理提醒** 固定在 **我** 页底部、列表限高可滚动；**重复提醒** 仅展示 **下一次** 与重复说明；**首页 / 发现列表** 微博卡片 **不展示配图缩略图**（详情仍可看图）。 | Done |
| F-028 | **首页标题快捷面板**：点击 **首页** 标题打开 **底部面板**（取代原「刷新 / 取消」对话框）；**我的发布** 与 **各身份最近微博**（每身份最多 **3** 条预览，更多显示「还有 n 条…」）同列；点身份行 **切换当前身份**，点预览行 **打开对应微博**。 | Done |
| F-029 | **列表附图提示**：首页/我的发布等 **PostCard** 与 **发现** 列表行在隐藏缩略图时，对含配图的微博展示 **浅色「附图」角标**（图标 + 文案），无障碍说明可在详情查看；**详情页** 仍为完整图库。 | Done |
| F-030 | **提醒 · 工作日与快捷排序**：重复规则增加 **工作日（周一至周五）**；详情快捷时长 **按本地使用次数** 将 **最常用 3 个** 排在前面，**第 4 个固定为「明天 9:00」**，其余时长排在后面（仍横向滚动）。 | Done |

_Add new rows for upcoming work; keep IDs incrementing._

---

## Backlog detail (F-010–F-020 — shipped)

以下为实现前对齐用草稿说明；**对应功能已交付**，保留作设计追溯。

### F-010 主导航与首页控件

- **现状**：下拉、搜索、「首页」按钮、「更多」无功能。
- **方向**：优先产品决策 — **实现**（例如下拉刷新时间线、本地全文搜索、更多里放设置子集）或 **移除/灰显** 并配 `contentDescription` / 提示，避免误导长期用户。

### F-011 消息页

- **选项 A**：按 **当前活跃身份** 过滤「与我相关」线程。
- **选项 B**：移除 Tab/入口，将评论通知类能力并入「我」或详情。
- 需统一与 **F-003**（消息页长按复制）的交互是否保留。

### F-012 评论删除 48 小时

- 删除入口仅当 `now - comment.createdAt <= 2 days`（建议用 `Instant`/epoch 毫秒；设备时间影响仍适用）。
- 超窗后 **不展示删除**，无额外灰色说明文案。

### F-013 消息页笔形图标

- 定位具体 `Composable`/资源，无功能则删除；若本意为「写微博」则改为与主导航一致的发布入口规范。

### F-014 位置与 @（历史）

- **位置**：已于 **F-023** 从产品移除（不再请求权限或写入 `extrasJson.location`）。
- **@**：插入 `@` 与身份列表选择（保留）。

### F-015 我页身份编辑

- 与身份管理页复用同一编辑界面或导航到该屏并带回结果。

### F-016 星期

- 与 `F-005` 语言一致；列表与详情共用一时间格式化工具避免分叉。

### F-017 分享文本发微博

- `AndroidManifest` `intent-filter` + `onNewIntent`/`Nav` 参数；注意多任务栈与已打开 Compose 时的合并策略。

### F-018 评论最新在上

- 数据库查询 `ORDER BY`、内存列表反转、或两者 — 以单一数据源为准，避免详情与列表顺序不一致。

### F-019 闹钟

- **一次性** 提醒 + 通知点击打开对应帖子；系统 `setAlarmClock` + DB 重挂；自选日期时间；精确闹钟 / 电池说明入口。

### F-020 详情 ⋮ 菜单

- **删除**：硬删除或软删除（`extrasJson` 标记）需与备份/导出一致。
- 其它动作按现有仓库能力挑选，避免空菜单项。

---

## Reusable pieces

- `com.pocketweibo.ui.util.copyPlainToClipboard` — label + text + optional toast.
- `com.pocketweibo.ui.components.SelectablePostBody` — inline selectable post body (detail).
- `com.pocketweibo.ui.components.SelectableCopyDialog` — dialog with scroll + selection for compact rows.
- `com.pocketweibo.ui.screens.me.MeSettingsScreen` — settings hub (backup / about / external links); opened from **我** via `WeiboTitleBar.onRightIconClick`.
- `com.pocketweibo.ui.screens.compose.ShakeToSendEffect` — optional shake-to-send while composing (guarded; see F-006).
