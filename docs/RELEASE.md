# Release readiness

Vibe Check is currently a pre-release Android application. This document
defines the release gates; it does not authorise signing, publishing or merging.

## Current version

- Application ID: `com.petermathie.vibecheck`
- Release identity: **Vibe Check 0.4.0 Beta 3**
- Version name: `0.4.0-beta.3`
- Version code: `6`
- Room schema version: `15`
- Minimum Android SDK: `23`
- Target Android SDK: `36`

The Gradle values in `app/build.gradle.kts` are authoritative.

## Versioning policy

- Use semantic versioning for `versionName`.
- While the app is pre-1.0, increment the minor version for a user-visible beta
  milestone and the patch version for compatible fixes to that milestone.
- Increment `versionCode` for every APK or App Bundle distributed outside local
  development. Never reuse a version code, including for a rebuilt release.
- A database schema change requires a new version name and version code.
- Record the released commit, version values, Room schema, validation run and
  artifact checksum in the release notes.

## Data and upgrade gates

Before distributing a build:

1. Export the latest Room schema into `app/schemas/` and commit it.
2. Provide a forward migration from every database version that may exist on a
   supported user device to the current version.
3. Run migration tests that preserve user records, foreign keys, ordering and
   historical snapshots.
4. Run JSON backup/export, restore/import and CSV regression coverage.
5. Install over the previous distributed build and verify launch and data
   access on an emulator and a real phone.

The repository contains Room migrations through schema version 15 and tests for
the recent migration chain. Distribution to an authentic version-1 install is
blocked by DATA-01 until an authentic v1 schema/database fixture is available;
the repository must not substitute an invented fixture. The v5 historical
snapshot policy cannot reconstruct definition edits made before the upgrade:
it snapshots definitions present at migration/import time.

Users should make a JSON backup before an upgrade. Structured backups exclude
progress photographs, which must be exported separately.

## Identity transition

Version 0.4.0 Beta 1 changes the Android application ID from
`com.petermathie.vibetrainer` to `com.petermathie.vibecheck`. Android therefore
installs Vibe Check as a distinct app; app-private database, photo and video
files from Vibe Trainer do not update or transfer automatically. Export
structured JSON and photos from the old app before moving. Vibe Check continues
to import version-1 backups whose envelope is `format: "vibe-trainer"`; that
legacy value is intentionally stable compatibility data, not current branding.

## Vibe Check 0.4.0 Beta 3

Beta 3 streamlines programme editing and body tracking while expanding exercise
catalogue coverage. It includes:

- compact plus-only Add controls in programme editing;
- a simplified Body viewport with fitted comparison imagery and one combined
  bodyweight/measurement/photo entry modal;
- catalogue v2 muscle mappings for more accurate movement coverage; and
- the new heart-and-brain Vibe Check launcher icon.

The Room schema remains version 15. Existing Beta 2 app data is retained during
an in-place, same-signing-key upgrade. Private datasets remain separate from the
APK and repository and can be imported through Settings after installation.

## Vibe Check 0.4.0 Beta 2

Beta 2 fixes programme exercise creation and moves workout prescriptions to
their programme assignments. It also includes:

- a redesigned Freshness experience with audited palettes, a clear legend,
  motion and muscle detail sheets;
- haptic feedback and futuristic dashboard polish across Home, Progress, Body,
  Habits, History and workouts;
- explicit Light, Medium and Dark habit-choice groups, preserving existing
  choices through the schema 13 to 14 migration;
- stable pointer-anchored card reordering;
- denser active-workout controls with stricter numeric validation; and
- Home single-viewport layout and visual-regression fixes.

Schema 15 also preserves decimal rep inputs while retaining legacy integer
snapshots. The schema 14 to 15 migration and exported schema are committed.

The verified prerelease is available from the
[Vibe Check 0.4.0 Beta 2 release page](https://github.com/PeterMathie/vibe-check/releases/tag/v0.4.0-beta.2)
and as the direct
[signed APK download](https://github.com/PeterMathie/vibe-check/releases/download/v0.4.0-beta.2/Vibe-Check-beta.apk).
Its SHA-256 is
`dca788df81febba3ced761e46825344b84c086a44c071653d6aaa99cb6e76c8b`.

## Build readiness

Use JDK 17 and the Android 36 SDK:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest
gradle :app:assembleRelease
```

`assembleRelease` is a compilation, shrinking/packaging and manifest check. It
produces an unsigned artifact unless all four beta-signing environment
variables documented in [`SIGNING.md`](SIGNING.md) are present. A distributable
APK must pass `apksigner verify` and match the recorded beta certificate.

Before a signed release:

- Load signing only from the protected Keychain or GitHub Actions secrets.
- Build and verify the signed App Bundle or APK in the release environment.
- Regenerate the dependency inventory, reconcile the bundled
  `THIRD_PARTY_NOTICES.md` with the exact resolved release runtime graph, and
  complete legal review of required full licence/NOTICE distribution.
- Complete QA-01 on a real Android phone, including locked-screen timer,
  notification-denial and vibration behavior.
- Resolve DATA-01 for any release expected to upgrade an authentic v1 install.
- Review store listing, privacy/data-safety declarations, screenshots and
  support/contact details.

No signing credentials, signing configuration or publishing task should be
committed to source control.

## Download-link publication order

Do not merge a live-looking README asset link before its matching release asset
exists. Before publication, label the download as unavailable without a
clickable URL. Publish and verify the signed release asset first, then add the
direct link and verify it returns HTTP 200. This avoids presenting a planned
release URL as an available download.
