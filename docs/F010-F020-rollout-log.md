# F-010–F-020 implementation rollout

Each row matches **one git commit** and a **version bump** in `app/build.gradle.kts`. Tests: `./gradlew test assembleDebug` before every commit.

| Step | ID   | Commit (after push) | versionName | versionCode | Summary |
|------|------|---------------------|-------------|---------------|---------|
| 1    | F-010 | `feat(F-010): home refresh, search, title & more menus` | 3.7.0       | 108           | Home: `pullRefresh` + indicator; search dialog filters body/name; title dialog = refresh; ⋮ → 设置 / 发现；`material` for pullRefresh；`WeiboTitleBar` 左侧 48dp 触控。 |
| 2    | F-011 | `feat(F-011): messages scoped to active identity` | 3.8.0       | 109           | 「收到」仅非本人评论；无当前身份时说明空态；横幅提示当前身份范围。 |
| 3    | F-012 | `feat(F-012): comment delete 48h window` | 3.9.0       | 110           | CommentBottomSheet：`isCommentWithinDeleteWindow`；超时显示 `comment_delete_locked_hint`。 |
| 4    | F-013 | `fix(F-013): remove dead compose icon on messages` | 3.9.1       | 111           | MessageScreen 标题栏去掉无点击行为的 Edit 图标。 |
| 5    | F-014 | `feat(F-014): compose location + @ mentions` | 3.10.0      | 112           | 定位权限 + last known + Geocoder → `extrasJson.location`；\@ 对话框插入其他身份；`insertPostWithPreparedGallery(locationLabel)`。 |
| 6    | F-015 | `feat(F-015): tap Me header to edit active identity` | 3.11.0      | 113           | MeScreen 身份区 `clickable` → `IdentityDetailScreen`。 |
| 7    | F-016 | `feat(F-016): weekday in formatted post times` | 3.12.0      | 114           | `time_full_format` / `time_short_format` / 消息列表过去态含 EEE；`RelativeTime` 使用 `appLocale()`。 |
| 8–11 | F-017–F-020 | `feat(F-017..F-020): share, comments DESC, reminders, detail menu` | 3.16.0      | 118           | `ComposeIntentViewModel` + `singleTop` + SEND；评论 `ORDER BY DESC`；`post_reminders` + `PostReminderReceiver` + `setAlarmClock`；详情 ⋮ 复制/分享/提醒/删除；通知渠道；`EXTRA_OPEN_POST_ID`。 |
| 12   | F-023 | `c2d2701` | 3.18.0      | 123           | 移除写微博「位置」：无定位权限；删 `ComposeLocationHelper`；`insertPostWithPreparedGallery` 不再写 `extrasJson.location`；\@ 与 `extrasJson` 列保留。 |

_Update the commit hash column after each `git commit` if you maintain this file by hand._
