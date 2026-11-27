<!-- One user request per PR. See AGENTS.md and docs/RULES.md §0. -->

## Summary

<!-- What user-visible behavior changed, and why. -->

## Screenshots / recordings

<!-- For UI changes; delete this section when not applicable. -->

## Checklist

- [ ] One user request only; no unrelated changes bundled in
- [ ] `./gradlew test assembleDebug` passes locally
- [ ] `versionCode` +1 and `versionName` bumped (MINOR for a feature, PATCH for a fix)
- [ ] New/changed UI strings exist in all three locales (`values/`, `values-en/`, `values-zh-rTW/`)
- [ ] `docs/FEATURE.md` (next `F-xxx` row) or `docs/FIX.md` entry added, with commit + date; `docs/REVIEW.md` status updated if a finding is resolved
- [ ] Room schema change: new migration added and registered in `AppDatabase`
- [ ] No `local.properties`, `secrets/*`, keystores, or generated build output committed
- [ ] `./gradlew assembleRelease` run when touching signing, `app/build.gradle.kts`, or CI
