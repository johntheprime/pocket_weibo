# PocketWeibo — Bug Findings (2026-10-18)

Cross-check of the implementation against `FEATURE.md` and `FIX.md`. Severity: **High** = data loss / wrong data shown; **Medium** = functional defect in realistic use; **Low** = robustness, hygiene, or doc-vs-code mismatch.

---

## BUG-001 (High) — Restore wipes `isPinned` on every post
- **Where**: `data/repository/WeiboRepository.kt`
  - `exportAllData()` (post JSON block) omits `isPinned`.
  - `exportTextOnlyForAutoBackup()` (`{"pocket_weibo_text_auto_backup"}`) omits it too.
  - `postFromJson()` never reads `isPinned` → import always inserts `isPinned = false` (`PostEntity` default).
- **Impact**: Any JSON import, ZIP backup restore (settings → import, merge **or** replace), and F-038 encrypted auto-backup restore silently un-pins everything. Contradicts F-022/F-060 (pin feature) and F-038 ("可恢复").
- **Fix**: In both export writers add `put("isPinned", post.isPinned)`, and in `postFromJson` add `isPinned = postJson.optBoolean("isPinned", false)`. Bump the export `version` and keep the field optional for old v1/v2 files.

## BUG-002 (High) — Comment like-state wrong with multiple identities (substring + wrong default id)
- **Where**: `data/local/dao/CommentDao.kt`, `data/repository/WeiboRepository.kt`
- Repo calls `commentDao.getCommentsByPost(postId)` → `currentIdentityId` uses the DAO default **`0`**. The SQL computes
  `isLikedByMe = CASE WHEN c.likedBy LIKE '%' || :currentIdentityId || '%' …`
  i.e. substring matching: id `1` matches `likedBy "11,21,101,"`; default `0` matches **any** `likedBy` containing “0” (`"10,"`, `"105,"`, …) → comments show as liked-by-me when they are not, and not when they are.
- Secondary problems in the same DAO:
  - `likeCommentById` blindly appends `<id>,` with no dedup → double-tap doubles the entry and `likeCount` (counter is `likedBy`-derived) drifts.
  - `unlikeCommentById` uses `REPLACE(likedBy, '<id>,', '')` → replaces the substring anywhere, leaves dangling commas/out-of-order remainder; combined with substring matching it can corrupt the list (e.g. unlike id 1 on `"11,1,"` yields `",1,"` → id 1 stays “liked”).
- **Impact**: F-006 comment likes misbehave whenever more than one identity has liked a comment (core scenario, app is identity-centric).
- **Fix**: (a) repository `getCommentsByPost` must accept the active identity id and pass it in; (b) make DAO methods atomic: `UPDATE comments SET likedBy = :newList WHERE id=:id` computed in Kotlin (split → set add/remove → sorted → tostring) inside `@Transaction`, or a proper many-to-many table. Drop `LIKE` matching entirely.

## BUG-003 (Medium) — `clearAllData()` leaves AlarmManager alarms orphaned
- **Where**: `WeiboRepository.clearAllData()` deletes DB rows (FK cascade takes `post_reminders`/`identity_schedules` with the posts/identities) but never calls `PostReminderAlarmScheduler.cancel / IdentityScheduleAlarmScheduler.cancel` for the affected rows.
- **Impact**: After 设置 → 清空数据 or a replace-import, previously armed alarms keep firing. Receivers look the row up (`getById` → `?: return@launch`) so nothing is displayed, but alarms wake/pin the device uselessly (battery, background starts) and can persist across days until a reboot (boot receiver only re-arms DB rows, which are gone).
- **Fix**: in `clearAllData()` (and in the replace-import path before wiping) read `postReminderDao.listAll()` + `identityScheduleDao.listAll()` first and cancel each alarm, or add `cancelAllPendingIntents(context)` on the two schedulers that re-cancels by canonical request codes.

## BUG-004 (Medium) — Negative (BC) years are corrupted when editing an identity
- **Where**: `ui/screens/identity/IdentityDetailScreen.kt` `birthYear`/`deathYear` fields use
  `onValueChange = { birthYear = it.filter { c -> c.isDigit() }.take(4) }`.
- **Impact**: Seed data (`DataSeeder`) legitimately stores negative years (孔子 `-551/-479`, 苏格拉底 `-470/-399`). The field initializes with `"-551"`, but **any** user edit in the field strips the minus → next save flips it to `551` (or a wrong value), silently corrupting the timeline. Also users cannot type negative years at all (F-002 identity details).
- **Fix**: allow an optional leading `-`, e.g. keep only first `[-0-9]` chars; or switch to signed-year input with 纪元前 toggle; minimum: guard the seed data round-trip (`"−551"` preserved when untouched).

## BUG-005 (Low) — F-059 doc claims 每天/工作日/周末 quick-select; dialog only has 每天 + 7 day chips
- **Where**: `ui/screens/me/IdentityScheduleScreen.kt` (add/edit dialog rows under `schedule_days_label`).
- **Impact**: FEATURE F-059 says “支持每天/工作日/周末/自定义日”; the list row labeller (`formatScheduleTime`) recognizes week-day/week-end strings, but the picker cannot produce them with one tap. Doc-vs-code mismatch.
- **Fix**: add two more `FilterChip`s filling `selectedDays = "1,2,3,4,5"` / `"6,7"` (same pattern as 每天).

## BUG-006 (Low) — F-060 pinned-first ordering not applied to the home identity quick-access preview
- **Where**: `ui/screens/home/HomeScreen.kt` `postsByIdentity = remember(posts) { posts.groupBy … sortedByDescending { createdAt } }` (used by the quick-access sheet preview per identity).
- **Impact**: FEATURE F-060 says lists (首页 / 身份筛选) order pinned first; the main feed DAO does, the per-identity preview doesn't — inconsistent UX rather than data issue.
- **Fix**: `list.sortedWith(compareByDescending { it.isPinned }.thenByDescending { it.createdAt })`.

## BUG-007 (Low) — `PostAudioPlayerBar` does heavy work & leaks on prepare failure
- **Where**: `ui/components/PostAudioPlayerBar.kt` — `remember(relativeAudioPath) { MediaPlayer().apply { setDataSource…; prepare() } }` runs **inside composition on the main thread**; `getOrNull()` discards the player object without `release()` if `prepare()` throws (still leaks between setDataSource and prepare failure).
- **Impact**: visible jank/possibility of ANR with large/corrupt files; MediaPlayer leak on failure.
- **Fix**: create+prepare on IO (or `prepareAsync`), `setOnPreparedListener` flips a `ready` state; on failure call `release()` before nulling.

## BUG-008 (Low) — Notification-id collisions in `PostReminderNotificationIds`
- **Where**: `reminder/PostReminderNotificationIds.kt` — `Objects.hash(postId, reminderId) and Int.MAX_VALUE`; different (post, reminder) pairs can hash to the same notifyId, so an arriving reminder silently replaces a still-untapped notification (and its actions, targeting the other post).
- **Fix**: separate `notify()` by using the reminder row long as id low-bits with a distinct channel (reminders per row are few) or rely on `Int` id = `reminderDbId` (per-post uniqueness already guaranteed by `cancelPostRemindersInternal` one-row-per-post scheduling).

## BUG-009 (Low) — `runBlocking` on the UI thread at app start
- **Where**: `MainActivity.onCreate` → `runBlocking { UiPreferences.applyStored(applicationContext) }`.
- **Impact**: SharedPreferences/DataStore read blocks main thread before first frame; with app-link/share handling cold start adds jank. Applies theme before compose only — an async read + subsequent `PocketWeiboTheme(state)` would be equivalent, but today this is a (small) ANR-class risk.

## BUG-010 (Low) — Unstructured fire-and-forget coroutines in identity screens
- **Where**: `ui/screens/identity/IdentityListScreen.kt` (`CoroutineScope(Dispatchers.IO).launch { app.repository.setActiveIdentity(…) }` on activate **and** delete).
- **Impact**: exceptions vanish (unhandled in a custom scope), deletion may outlive the screen without user feedback, cancellation on destroy non-guaranteed. Same pattern exists nowhere else (screens use `rememberCoroutineScope`) — inconsistency.
- **Fix**: `rememberCoroutineScope().launch { withContext(Dispatchers.IO) { … } }`.

## BUG-011 (Low) — Post-detail intent landing while a stack screen is open appears ignored
- **Where**: `MainActivity.kt` `MainScreen` — reminder-notification tap sets `openPostId` → `postDetailId = id`, but the `when` block renders `IdentityDetail/IdentityList/MeSettings/IdentitySchedule/Compose…` first. Users inside one of those (e.g. Me → Settings) see nothing until they back out manually, then the post detail pops unexpectedly.
- **Fix**: when `openPostId` arrives, drop the overlay states (or queue the detail until top-level, with a toast/snackbar).

## Observations (design debt, not defects)

- **OBS-01 Dark theme**: wired via `isSystemInDarkTheme` + `LocalDarkTheme`, but most components (`PostCard`, dialogs, `Surface(color = Color.White)`) hardcode light palette → inconsistent dark rendering. Either finish MaterialTheme color routing or document dark mode as unsupported.
- **OBS-02** `DatabaseMigrations`: `PRAGMA foreign_keys` inside the migration transaction is a no-op; keep migrations inside `db.use {}` only for schema work.
- **OBS-03** Exact-alarm denial on Android 14 silently degrades reminder precision (`set()`-style fallback); consider a settings-level hint when the permission is absent.
- **OBS-04** Export `version` field (JSON v2 / auto-backup v1) is written but never checked at import; old backup forward-compat is currently implicit.
- **OBS-05** The only automated test is `androidTest/.../IdentityDeleteKeepsPostsTest.kt` — see `REVIEW.md` §6/S-6 for the highest-value test targets.

---

**Not bugs (checked and cleared)**: the 8 Open Peeps sketch preset PNGs *do* exist (earlier pass mis-listed them; they live in `app/src/main/res/drawable-nodpi/` and match F-045); `values`/`values-en` string parity is exact (374/374); `IdentityScheduleReceiver`/`PostReminderReceiver` both no-op safely on missing rows; zip-import replace path doesn't double-clear (`skipClearOnOverride=true`).
