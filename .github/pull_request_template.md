<!-- One user request per PR. See AGENTS.md and docs/RULES.md §0. -->

## Summary

<!-- What user-visible behavior changed, and why. -->

## Screenshots / recordings

<!-- For UI changes; delete this section when not applicable. -->

## Checklist

- [ ] One user request only; no unrelated changes bundled in
- [ ] `./gradlew test assembleDebug` passes locally
- [ ] `versionCode` +1 and `versionName` bumped (MINOR for a feature, PATCH for a fix)
- [ ] New/changed UI strings exist in both `values/strings.xml` and `values-en/strings.xml`
- [ ] `docs/FEATURE.md` (next `F-xxx` row) or `docs/FIX.md` entry added
- [ ] Room schema change: new migration added and registered in `AppDatabase`
- [ ] No `local.properties`, `secrets/*`, keystores, or generated build output committed
- [ ] `./gradlew assembleRelease` run when touching signing, `app/build.gradle.kts`, or CI
