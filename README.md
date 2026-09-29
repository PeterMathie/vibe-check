# Vibe Check

**Vibe Check 0.4.0 Beta 2**

Native, local-first Android strength, flexibility and open-ended activity tracking.

## Download Vibe Check Beta APK

[Download Vibe Check 0.4.0 Beta 2](https://github.com/PeterMathie/vibe-check/releases/download/v0.4.0-beta.2/Vibe-Check-beta.apk)
or review the [Beta 2 release notes](https://github.com/PeterMathie/vibe-check/releases/tag/v0.4.0-beta.2).

APK SHA-256: `dca788df81febba3ced761e46825344b84c086a44c071653d6aaa99cb6e76c8b`

This is beta software distributed outside Google Play. Android will ask you to
allow installation from the browser or file manager used to open the APK.
Download the file, open it, approve that one source when prompted, then install.

Updates signed with the same Vibe Check beta key install over this beta and
retain its app-private data. Uninstalling Vibe Check or clearing its storage
deletes that data. Make a JSON backup before updating; structured JSON excludes
progress photos and copied reference videos, which require separate handling.
The app is local-only and has no cloud account or automatic cloud backup.

See the [reviewer showcase](docs/APP_REVIEW.md) for the feature tour, privacy
model, limitations and review checklist.

## Current direction

The greenfield architecture uses Kotlin, Jetpack Compose, Room, Hilt and an explicit domain layer. Objective workout history drives recency maps, activity heatmaps and progress calculations.

Visual styling is isolated behind semantic tokens, interchangeable palettes, and an independent visual-style layer. **Standard** remains the unchanged default; the opt-in **Retro Futuristic** style adds original space-console geometry, instrument graphs, and a holographic anatomy scanner without changing data meaning or interaction. Production builds seed application knowledge but no personal programmes or history; debug builds add removable representative data.

See [`docs/PRODUCT_REQUIREMENTS.md`](docs/PRODUCT_REQUIREMENTS.md) for the product contract.
The current Android identity is `com.petermathie.vibecheck`, version `0.4.0-beta.2`
(`versionCode 5`). This is a distinct install from the former Vibe Trainer
package; use its JSON export/import flow to move structured records.

For the current implementation, verified builds and agent-ready tasks, start with
[`docs/WORK_STATUS.md`](docs/WORK_STATUS.md). See
[`docs/BETA_TESTING.md`](docs/BETA_TESTING.md) for the tester walkthrough.

### Anatomy

The muscle diagram uses real male/female front/back SVG muscle path data adapted from the MIT-licensed [Jsplice/MuscleMap](https://github.com/Jsplice/MuscleMap) project. The paths are parsed and drawn natively in Compose; they are not hand-sketched placeholders.

## Development

- Stable baseline: `main`
- Active development: `develop`
- Native stack: Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, Coroutines/Flow
- Default units: kg
- Storage: local-first on-device SQLite

Pushing to `develop` triggers a GitHub Actions debug APK build.
