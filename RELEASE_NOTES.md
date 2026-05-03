## Pic Path 0.1 line — first auto-published release

This is the first release cut by the new GitHub Actions pipeline (every push
to `main` → tests → APK → release). Version line resets to `0.1.x` and
auto-increments the build number per push. See [docs/release.md](docs/release.md)
for the release contract and [docs/i18n.md](docs/i18n.md) for the translation
process.

### What's new since v1.1.0

**Browse modes**
- Three-way mode selector replaces the old static header: **Images**, **Files**,
  **Folders**.
- *Files* mode enumerates any MIME type via `MANAGE_EXTERNAL_STORAGE`, with a
  wildcard search (`*` and `?`).
- *Folders* mode is hierarchical: tap to drill in, breadcrumb shows the path,
  long-press to copy a folder path. Leaf folders render greyish so navigable
  ones stand out.

**Image editor — much expanded**
- Crop with edge midpoint handles plus the existing corner handles, and wider
  hit zones so dragging is forgiving.
- New **freehand brush** tool with color picker (8-color palette).
- New **two-finger pan and zoom** on the editor canvas (1f drawing/cropping
  unaffected).
- Save creates a copy beside the original via MediaStore — never overwrites,
  and is queryable immediately so you can preview the edited result without
  waiting on MediaScanner.

**Clipboard**
- New **Copy Image** action (alongside Copy Path) puts the image content URI
  on the clipboard so chat/doc apps paste a bitmap. Path-copy stays as
  plain-text for terminal CLI auto-detection.
- Multi-image copy formats: space / comma / semicolon / `@`-prefix, picked in
  Settings. Bottom-bar Copy button shows the active format tag.

**System integration**
- Pic Path now appears in any app's "Edit with…" menu — registered as an
  `ACTION_EDIT image/*` handler. Returns the saved URI to the caller (e.g.,
  Google Photos picks up the edit) and a Copy Path / Copy Image / Done
  chooser appears after save.
- Share-target accepts any file MIME. Default behavior is "copy path and
  close"; toggle in Settings to open the editor for crop/annotate instead.
- Vertical-swipe dismisses the fullscreen viewer; single tap toggles
  controls; full path renders in 5 lines beneath the image.

**Performance / data layer**
- Migrated the entire grid to **Paging 3** with Room PagingSources — root-cause
  fix for an intermittent `CursorWindow` crash on rapid scroll.
- Scan-and-index is now upsert + delete-stale (no empty-grid flash on rescan).
- Auto-refresh on app resume.

**Internationalization**
- 10 first-tier languages fully translated: Chinese (zh), Spanish (es),
  Portuguese (pt), Russian (ru), Japanese (ja), German (de), French (fr),
  Korean (ko), Hindi (hi), Turkish (tr). Every UI string covered.
- 19 partial-tier locales fall back to English for newer keys; PRs welcome
  via [docs/i18n.md](docs/i18n.md).

**Build & release**
- Every push to `main` now publishes a release with the APK attached.
- `versionCode` is sourced from the workflow run number (auto-incrementing);
  `versionName` is `${baseVersion}.${run_number}` where `baseVersion` is the
  manual `0.1` knob in `app/build.gradle.kts`.
- Local debug builds get a copy at `/storage/emulated/0/builds/pic-path.apk`
  for easy on-device sideload.

### Notes for existing users

The version line reset from `1.1.x` to `0.1.x`. If you're on the old
`v1.1.0` install, the in-app updater won't see this as "newer" (it does a
dotted-version compare). Sideload the APK once from this release page to
move onto the new line; subsequent updates flow normally.
