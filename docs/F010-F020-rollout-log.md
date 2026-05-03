# F-010–F-020 implementation rollout

Each row matches **one git commit** and a **version bump** in `app/build.gradle.kts`. Tests: `./gradlew test assembleDebug` before every commit.

| Step | ID   | Commit (after push) | versionName | versionCode | Summary |
|------|------|---------------------|-------------|---------------|---------|
| 1    | F-010 | `feat(F-010): home refresh, search, title & more menus` | 3.7.0       | 108           | Home: `pullRefresh` + indicator; search dialog filters body/name; title dialog = refresh; ⋮ → 设置 / 发现；`material` for pullRefresh；`WeiboTitleBar` 左侧 48dp 触控。 |
| 2    | F-011 | …                   | 3.8.0       | 109           | Messages: received = others on my posts only; empty copy for current identity |
| 3    | F-012 | …                   | 3.9.0       | 110           | Comment delete only within 48h (device time); strings ZH/EN |
| 4    | F-013 | …                   | 3.9.1       | 111           | Remove non-functional edit icon on message title bar |
| 5    | F-014 | …                   | 3.10.0      | 112           | Compose: location (coarse + Geocoder best-effort), @ opens identity picker to insert @name |
| 6    | F-015 | …                   | 3.11.0      | 113           | Me: tap identity header → edit identity |
| 7    | F-016 | …                   | 3.12.0      | 114           | Post time line shows weekday (shared formatter) |
| 8    | F-017 | …                   | 3.13.0      | 115           | ACTION_SEND text/plain → compose with prefill; singleTop |
| 9    | F-018 | …                   | 3.14.0      | 116           | Comments ORDER BY createdAt DESC in Room |
| 10   | F-019 | …                   | 3.15.0      | 117           | Post reminder: presets + AlarmManager.setAlarmClock + tap opens post |
| 11   | F-020 | …                   | 3.16.0      | 118           | Post detail ⋮: delete (confirm), copy body, remind; cancel reminder |

_Update the commit hash column after each `git commit` if you maintain this file by hand._
