# Fix log and change workflow

This file records **resolved requirements** and the **standard process** for every change (human or agent), so work stays traceable and releasable.

---

## Fixed requirements (ledger)

| Date (UTC) | Requirement | Resolution | Verified |
|-------------|---------------|------------|----------|
| 2026-05-03 | Discover search: tapping a **微博** search result opens that post’s **detail and comments** (same as home / trending). | Wired `onPostClick` through `SearchResultsContent` → `PostSearchItem`; row uses `Modifier.clickable` and navigates via `postDetailId` → `PostDetailScreen`. File: `app/src/main/java/com/pocketweibo/ui/screens/discover/DiscoverScreen.kt`. | `./gradlew assembleDebug` — BUILD SUCCESSFUL (confirmed before commit) |
| 2026-05-03 | Maintain **FIX.md** as the fix ledger and **plan → implement → verify → record → ship** checklist for all future work. | Added this file; links CI expectation to `assembleDebug`. | Same build as above |

_Add new rows above this line for each shipped fix._

---

## Workflow for every request (plan → implement → verify → record → ship)

1. **Plan** — Restate the goal, list files or areas to touch, and note risks (navigation, DB, CI).
2. **Implement** — Make the smallest change that satisfies the requirement; match existing patterns.
3. **Verify** — Run `./gradlew assembleDebug` (matches [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)). Fix failures before recording “done”.
4. **Record** — Add or update a row in **Fixed requirements (ledger)** above; set **Verified** to the exact command and outcome (e.g. `BUILD SUCCESSFUL`).
5. **Ship** — Commit with a clear message and push to the branch used for CI (e.g. `apk-build`).

### Checklist (copy for each task)

- [ ] Plan written (goal + scope)
- [ ] Code / resources updated
- [ ] `./gradlew assembleDebug` succeeds
- [ ] **FIX.md** ledger row added or updated
- [ ] Committed and pushed

---

## Notes

- **APK on GitHub:** Pushes to `apk-build` trigger the workflow that builds the debug APK; keeping `assembleDebug` green keeps releases unblocked.
- If a change is **reverted** or **superseded**, add a short note in the ledger row or a new row pointing to the follow-up fix.
