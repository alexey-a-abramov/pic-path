# Pic Path

> Capture, annotate, and hand off screenshots to console-based AI agents on Android.

Pic Path is an Android app for developers who run AI coding assistants (Claude Code,
Gemini CLI, Codex) inside [Termux](https://termux.dev/) on their phone. It turns the
"I want to point my agent at this screenshot" workflow into a single tap: open or
share the image, optionally crop and annotate it, then drop the path (or the image
itself) into the clipboard in a format the CLI you're using will pick up.

---

## Why this exists

Running an AI agent on a phone is a different problem than running one on a laptop.
You don't have drag-and-drop. The "share" sheet usually drops a `content://` URI that
no command-line tool can read. The clipboard's image-vs-text distinction is fuzzy.
Different CLIs auto-detect different syntaxes (`/sdcard/...` paths, `@path` tokens,
comma-separated lists).

Pic Path is the missing UI for that workflow:

- Find the screenshot fast, browse by folder or category.
- Annotate it (arrows, freehand, text, crop) so the agent sees exactly what you mean.
- Copy the **path** for terminal CLIs that auto-attach files, or copy the **image
  content** for chat/document apps that paste bitmaps.
- Pick a clipboard format (space / comma / semicolon / `@`-prefix) that matches your
  CLI — set once in Settings, applied to every multi-select copy.

If that workflow doesn't apply to you, this is just a perfectly fine but unusually
opinionated image gallery — most users will be happier with the system gallery.

---

## Features

- **Three browse modes** — Images, Files (any MIME, requires `MANAGE_EXTERNAL_STORAGE`),
  Folders (hierarchical drill-down).
- **Multi-select with format-aware copy** — long-press to enter selection, the bottom
  bar shows `Copy N paths · space-sep` (or whichever joiner you've configured).
- **Image editor** — crop with edge/corner handles, arrow, freehand brush, text. Color
  picker for annotations. Two-finger pan and zoom on the canvas.
- **Save edits as copies** — never overwrites the original. New file lands beside the
  source via MediaStore (queryable immediately, no MediaScanner round-trip).
- **Two clipboard actions** — Copy Path (plain text, terminal-CLI-safe) and Copy Image
  (content URI, for chat/doc paste targets). Distinct icons in the viewer.
- **Registers as a system image editor** — appears in any app's "Edit with…" menu via
  `ACTION_EDIT`; returns `RESULT_OK` with the saved URI so callers like Google Photos
  pick up the edit.
- **Share target** — share an image or any file to Pic Path; default is "copy path
  and close," toggle in Settings to open the editor instead.
- **Search** — wildcard-aware (`*`, `?`) in Files mode; substring in Images and Folders.
- **Auto-update from GitHub releases** — built-in update checker, one-tap install.
- **i18n** — English plus 29 partial locales; 10 fully translated. See
  [docs/i18n.md](docs/i18n.md) to contribute.

<!-- TODO: screenshots -->

---

## Workflow examples

**Annotated screenshot → Claude Code:**

1. Take a screenshot of the bug.
2. Share it to Pic Path (or open Pic Path and tap the image).
3. Tap Edit, draw an arrow at the broken element, tap Done.
4. Edited copy lands in `Pictures/PicPath/`; chooser offers Copy Path / Copy Image.
5. Tap Copy Path → switch to Termux → paste into your `claude` prompt.

**Multiple screenshots → one prompt:**

1. Long-press the first screenshot to enter selection mode (auto-copies it too).
2. Tap the rest.
3. Tap `Copy N paths · space-sep` in the bottom bar.
4. Paste in Termux. Claude Code / Gemini CLI / Codex auto-attach all of them.

**Folder of references → agent context:**

1. Switch to Folders mode.
2. Drill into the folder.
3. Long-press the folder card to copy its path (with or without trailing slash, set
   in Settings).
4. Pass it to your agent.

---

## Install

### From releases

Download the latest APK from the
[Releases page](https://github.com/alexey-a-abramov/pic-path/releases) and install it.
You'll need to allow "Install from Unknown Sources" the first time.

### Build from source

Requires the Android SDK and JDK 17+.

```bash
git clone https://github.com/alexey-a-abramov/pic-path.git
cd pic-path
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

The debug build is also copied to `/storage/emulated/0/builds/pic-path.apk` after
`assembleDebug` for on-device installs without `adb`. Override the destination with
`-PbuildsDir=...`.

---

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Room (with Paging 3) · Coil · DataStore
Preferences · Coroutines + Flow · OkHttp (update check). Min SDK 29, target SDK 34.

---

## Contributing

Pull requests welcome — bug fixes, small features, and especially translation
improvements (see [docs/i18n.md](docs/i18n.md)).

For larger changes, open an issue first to discuss the direction.

---

## License

MIT — see [LICENSE](LICENSE).
