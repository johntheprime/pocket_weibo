# PocketWeibo — Feature Proposals & Investigations

_Drafted 2026-11-26 against v3.72.0 (Room schema v16). Companion to `ROADMAP.md`._

| # | Topic | Type | Priority | Depends on |
|---|---|---|---|---|
| P1 | Cache ≈ user data size | Bug / design fix | High | — |
| P2 | Offline location (province level) | New feature | Medium | Roadmap Phase 0 (schema export + migration tests) |
| P3 | UI improvements | Enhancements | Medium–Low | Some tie into Roadmap Phase 5 (targetSdk 35) |

---

## P1 — Cache is as large as user data

### Finding
User data is stored correctly under `filesDir` (`post_attachments/`, `comment_attachments/`, `identity_avatars/`, auto-backups). The issue is **`cacheDir` is written by several features and never swept**: there is no cache cleanup at startup (`PocketWeiboApp` never touches `cacheDir`).

| Leftover in cache | Source | Size impact |
|---|---|---|
| `pocket_weibo_backup_<ts>.zip` — one per full export, never deleted | `WeiboRepository.exportAllDataZip()` (~L796) | **≈ full copy of all images, voice clips, avatars + data.json.** Main cause. Multiple exports → cache can exceed user data. |
| `pocket_weibo_backup.json` / `.md`, `pw_decrypted_<ts>.json`, `pocket_weibo_diag_<ts>.txt` | `MeSettingsScreen.kt` (~L455, 473, 504, 773) | Small–medium; timestamped ones accumulate |
| `compose_prep/*`, `compose_capture_<ts>.jpg`, `pw_src_*`, `pw_voice_*.m4a` | `PostAttachmentStorage`, `ComposeScreen`, `VoiceRecordingController` | Normally deleted; orphaned if process dies mid-draft |
| `pw_import_<ts>/` staging dirs | `WeiboRepository.kt` (~L976) | Orphaned if import throws before `deleteRecursively()` |

**Conclusion:** cache ≈ data is a direct symptom of the backup ZIP being staged in cache and never removed — not a coincidence.

### Proposed fix
- [ ] **Startup sweep** (in `PocketWeiboApp`, on `Dispatchers.IO`, non-blocking): delete files in `cacheDir` matching `pocket_weibo_backup_*`, `pocket_weibo_diag_*`, `pw_decrypted_*`, `pw_import_*`, `pw_src_*`, `pw_voice_*`, `compose_capture_*` older than ~1 h; clear `compose_prep/` when no draft references it.
- [ ] **Export ZIP via SAF**: use `ACTION_CREATE_DOCUMENT` and stream the ZIP directly to the user-chosen location (no cache copy). If share-sheet export is kept, delete the previous ZIP before creating a new one and after the share returns.
- [ ] Use fixed filenames (overwrite) for one-shot share files where possible instead of timestamped ones.
- [ ] Wrap import staging in `try/finally { staging.deleteRecursively() }`.
- [ ] **Settings → Storage panel**: "Data X MB · Cache Y MB · [Clear cache]".
- [ ] Test: export twice → cache contains ≤ 1 backup ZIP; after sweep → none.

---

## P2 — Offline location, province level only

### Current state
No location permission or location code exists. New feature.

### Approach (works with no internet, no Google Play Services)
1. **Coordinates**: `LocationManager` GPS provider (offline-capable).
   - Try `getLastKnownLocation()` first (instant); else `getCurrentLocation()` with a 15–30 s timeout.
   - Coarse/network location often fails offline → requires `ACCESS_FINE_LOCATION`, but only the province is retained.
2. **Reverse geocoding — offline**: bundle simplified boundary polygons for China's 34 provincial-level divisions (~100–300 KB GeoJSON or a compact binary) in `assets/`; point-in-polygon lookup with bounding-box pre-filter.
   - Do **not** rely on `android.location.Geocoder` — usually needs network and is often absent on Chinese ROMs without GMS.
   - Outside China: decide between "unknown / manual pick" or adding a country-level layer (open decision).
3. **Storage**: new nullable `PostEntity.provinceCode: String?` (GB/T 2260 code, e.g. `"440000"`). Room migration 16 → 17 (requires Roadmap Phase 0 tests). Display names come from `values/` + `values-en/` strings.

### UX
- [ ] Opt-in per post: 📍 chip in composer, **off by default**.
- [ ] Never store raw latitude/longitude.
- [ ] Tap chip → manual province picker (covers GPS failure indoors / permission denied).
- [ ] Show in post footer: "📍 广东" / "📍 Guangdong".
- [ ] Optional: province filter in Discover / Home filter sheet.
- [ ] Permission rationale dialog explaining only province is saved.

### Must-haves
- [ ] Include `provinceCode` in `exportAllData()`, `exportTextOnlyForAutoBackup()`, and `postFromJson()` (avoid a repeat of BUG-001).
- [ ] Bump export `version`; field optional for older archives.
- [ ] Update `docs/BACKUP_ARCHIVE.md`, `docs/FEATURE.md`, README (both languages).
- [ ] Tests: point-in-polygon for known cities (Beijing, Guangzhou, Urumqi, Haikou, border points), migration 16→17, backup round-trip.

### Open questions
- Behaviour outside China? Country name


  
- Allow editing location after posting? no, but before post, edit location should be possible
- Also allow on comments, or posts only? comment with location as well

---

## P3 — UI improvements

Based on code review (not yet validated against screenshots/device).

| Item | Detail | Notes |
|---|---|---|
| Feed image thumbnails | Replace "image" badge with a strip of up to 3 small thumbnails | Coil with `size()` limits to keep memory low |
| Edge-to-edge & predictive back | Insets for `WeiboTitleBar`, `WeiboBottomTabBar`, bottom sheets, fullscreen viewer | Required by targetSdk 35 (Roadmap Phase 5) — do together |
| Dynamic color (Material You) | Optional theme next to light/dark | Android 12+ |
| Empty / first-run states | No posts, no search results, no reminders → helpful action | |
| Accessibility | Test at 200% font scale; content descriptions on icon-only buttons, image/voice indicators | |
| Composer polish | Live character counter near the 2,000 limit; drag-to-reorder attached images | |
| Storage panel | See P1 | Doubles as cache fix UI |

- [ ] Review `docs/screenshots/` for concrete visual suggestions (pending).

---

## Suggested sequencing

1. **P1 cache fix** — independent, low risk, immediate user benefit (can ship alongside Roadmap Phase 1).
2. **Roadmap Phase 0** — schema export + migration tests.
3. **P2 location** — first feature to use migration 16 → 17 (could share it with BUG-002's `comment_likes` table).
4. **P3 UI** — small items anytime; edge-to-edge with Roadmap Phase 5.
