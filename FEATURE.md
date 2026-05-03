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
| F-006 | **摇一摇发微博**：在写微博界面，除点「发送」外，**用力摇一摇**也可发布；通过输入停顿、进入页面暖机、发送冷却、线性加速度/加速度阈值与短时多脉冲识别，降低走路打字时的误发；附简短说明文案。 | Done |

_Add new rows for upcoming work; keep IDs incrementing._

---

## Reusable pieces

- `com.pocketweibo.ui.util.copyPlainToClipboard` — label + text + optional toast.
- `com.pocketweibo.ui.components.SelectablePostBody` — inline selectable post body (detail).
- `com.pocketweibo.ui.components.SelectableCopyDialog` — dialog with scroll + selection for compact rows.
- `com.pocketweibo.ui.screens.me.MeSettingsScreen` — settings hub (backup / about / external links); opened from **我** via `WeiboTitleBar.onRightIconClick`.
- `com.pocketweibo.ui.screens.compose.ShakeToSendEffect` — optional shake-to-send while composing (guarded; see F-006).
