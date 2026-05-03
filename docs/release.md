# Release pipeline

Every push to `main` produces a GitHub Release with a fresh APK attached. Most
of the time you don't have to think about it — push, wait for green, the release
appears at <https://github.com/alexey-a-abramov/pic-path/releases>. The app's
in-app update checker reads `releases/latest`, so users see the new build the
next time they tap "Check for Updates."

This document explains the contract so you know which knobs are manual.

## Versioning

Two parts:

- **`baseVersion`** in [`app/build.gradle.kts`](../app/build.gradle.kts) — the
  major.minor (e.g., `"0.1"`). **Manual.** Bump it when you intend a meaningful
  release line.
- **Build counter** — the patch component, sourced from
  `${{ github.run_number }}` in CI. Monotonic, set automatically.

Resulting version: `${baseVersion}.${run_number}`. So with `baseVersion = "0.1"`
and run #47, the published version is `0.1.47`, tag `v0.1.47`,
`versionCode = 47`.

Local builds (`./gradlew assembleDebug` on your machine) use `versionCode = 1`
and `versionName = "${baseVersion}.1"` — stable for development, never collides
with CI versions because run numbers start above 1 quickly.

### Bumping the base version

Edit `app/build.gradle.kts`:

```kotlin
val baseVersion = "0.2"  // was "0.1"
```

Commit. The next CI run publishes as `0.2.${run_number}` instead of `0.1.${run_number}`.
The build counter does **not** reset — it's repo-wide.

### Why not store the counter in a GitHub repo variable?

The user asked about this. Repo variables (`${{ vars.X }}`) are settable via
the API, but the default `GITHUB_TOKEN` lacks the `workflow` scope needed to
write them — you'd have to introduce a Personal Access Token, store it as a
secret, and accept a new failure mode (write succeeds, release fails, counter
out of sync). `github.run_number` gets us the same monotonic-counter behavior
with zero setup. If you ever need to reset or explicitly set the counter, you
can: GitHub exposes "re-run with new attempt" but not "set run_number to N." In
practice the counter just doesn't need touching.

## Release notes

Two paths, picked automatically:

### Default — auto-generated from commits

If `RELEASE_NOTES.md` doesn't exist (or is empty), CI calls
`generate_release_notes: true` on the GitHub Releases API. GitHub aggregates
commit subjects since the last release plus any merged PRs. Good commit
messages → good auto-notes; one-line throwaway commits → throwaway notes. No
extra work.

### Opt-in — hand-crafted

For releases that need a real summary (a feature ships, a behavior changes),
write `RELEASE_NOTES.md` at the repo root before pushing. Anything in that
file becomes the release body verbatim. After the release ships, CI deletes
the file in a follow-up commit (marked `[skip ci]` so it doesn't trigger
itself) — so each hand-crafted notes file is single-use.

You can ask Claude (this very agent) to write `RELEASE_NOTES.md` for you
based on the diff before pushing — there's no git hook for it because hooks
that call out to a network LLM are a bad UX (slow, fail offline, require every
contributor to install `claude` CLI). Just do it on demand:

> "Write RELEASE_NOTES.md summarizing the changes since the last release."

## Triggers

- **Triggers**: push to `main`.
- **Skipped**: paths matching `docs/**`, `README.md`, `LICENSE`, `.gitignore` —
  doc-only commits don't cut releases.
- **Skipped**: any commit whose subject contains `[skip ci]`. Useful for
  in-progress work you've pushed for safekeeping, and for the workflow's own
  cleanup commit.

To force-skip a release on a code push, include `[skip ci]` in the commit
message.

## Tests

CI runs `./gradlew test` (unit tests) before `assembleDebug`. If tests fail,
the release is not created — the workflow exits non-zero on the test step.

Instrumented tests (`connectedAndroidTest`) need an Android emulator; not run
in CI to keep the cycle under a minute. Add an emulator step (e.g.,
`reactivecircus/android-emulator-runner`) when you have tests that need one.

## APK signing

The released APK is built as `assembleDebug` and signed with the platform
debug key. It installs fine via "Install from Unknown Sources." The in-app
update checker just looks for a `.apk` asset on the release — naming and
signing don't matter to it.

When you're ready to ship a real signed release, the upgrade is small:

1. Generate a release keystore: `keytool -genkey -v -keystore release.jks ...`.
2. Base64-encode it: `base64 -w0 release.jks > release.jks.b64`.
3. Add four GitHub Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
   `KEY_ALIAS`, `KEY_PASSWORD`.
4. In `app/build.gradle.kts`, add a `signingConfigs.release` block that reads
   from environment variables.
5. In the workflow, decode the keystore to disk before `assembleRelease`, and
   change the build step from `assembleDebug` to `assembleRelease`.

That's a separate change; do it when you have something worth signing.

## Failure modes & recovery

- **Tests fail.** Workflow exits red, no release. Fix the test, push again.
- **Build fails.** Same.
- **Release step fails after a successful build.** The tag might exist without
  a release attached. Delete the orphan tag (`git push --delete origin v0.1.X`)
  and push again to retry.
- **Workflow accidentally re-triggers itself** (cleanup commit didn't include
  `[skip ci]`, or paths-ignore was wrong). Symptom: an immediate second release
  with no real changes. Fix the workflow, force-push the fix, manually delete
  the duplicate release.

## Local debug builds vs. CI builds

| | local (`./gradlew assembleDebug`) | CI (`push to main`) |
|---|---|---|
| versionName | `0.1.1` (build=1) | `0.1.${run_number}` |
| versionCode | 1 | `${run_number}` |
| APK location | `app/build/outputs/apk/debug/app-debug.apk` and `/storage/emulated/0/builds/pic-path.apk` | GitHub release asset |
| Update checker sees it as | the local install | the latest published release |
