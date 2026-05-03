# F-010–F-020 implementation rollout

Each row matches **one git commit** and a **version bump** in `app/build.gradle.kts`. Tests: `./gradlew test assembleDebug` before every commit.

| Step | ID   | Commit (after push) | versionName | versionCode | Summary |
|------|------|---------------------|-------------|---------------|---------|
| 1    | F-010 | `feat(F-010): home refresh, search, title & more menus` | 3.7.0       | 108           | Home: `pullRefresh` + indicator; search dialog filters body/name; title dialog = refresh; ⋮ → 设置 / 发现；`material` for pullRefresh；`WeiboTitleBar` 左侧 48dp 触控。 |
| 2    | F-011 | `feat(F-011): messages scoped to active identity` | 3.8.0       | 109           | 「收到」仅非本人评论；无当前身份时说明空态；横幅提示当前身份范围。 |
| 3    | F-012 | `feat(F-012): comment delete 48h window` | 3.9.0       | 110           | CommentBottomSheet：`isCommentWithinDeleteWindow`；超时显示 `comment_delete_locked_hint`。 |
| 4    | F-013 | `fix(F-013): remove dead compose icon on messages` | 3.9.1       | 111           | MessageScreen 标题栏去掉无点击行为的 Edit 图标。 |
| 5    | F-014 | `feat(F-014): compose location + @ mentions` | 3.10.0      | 112           | 定位权限 + last known + Geocoder → `extrasJson.location`；\@ 对话框插入其他身份；`insertPostWithPreparedGallery(locationLabel)`。 |
| 6    | F-015 | …                   | 3.11.0      | 113           | Me: tap identity header → edit identity |
| 7    | F-016 | …                   | 3.12.0      | 114           | Post time line shows weekday (shared formatter) |
| 8    | F-017 | …                   | 3.13.0      | 115           | ACTION_SEND text/plain → compose with prefill; singleTop |
| 9    | F-018 | …                   | 3.14.0      | 116           | Comments ORDER BY createdAt DESC in Room |
| 10   | F-019 | …                   | 3.15.0      | 117           | Post reminder: presets + AlarmManager.setAlarmClock + tap opens post |
| 11   | F-020 | …                   | 3.16.0      | 118           | Post detail ⋮: delete (confirm), copy body, remind; cancel reminder |

_Update the commit hash column after each `git commit` if you maintain this file by hand._
