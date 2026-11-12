# Structured import — format version 1

This format currently targets database schema 16. Historical notes must be converted into these structured records before import; the app does not parse prose. [historical-workout.json](examples/historical-workout.json) is an executable, fictional example tested by Android CI. It imports a custom exercise, structured variation, finished session, 10-second hold, exercise notes and a Yellow + Black + Purple band stack. Durable unfinished workout-entry drafts are included in backups. App-private exercise reference videos are deliberately separate from JSON backup data.

## Envelope and merge rules

The root object contains `format: "vibe-trainer"`, numeric `version: 1`, and a `tables` object. The legacy format identifier is intentionally unchanged so Vibe Check can import Vibe Trainer backups after the Android identity change. Optional `preferences` contains the supported profile/palette settings. Unknown root fields, table names or preference keys are rejected.

Palette preferences use the stable IDs `ocean`, `sunset`, `forest` and `mono`. Older built-in IDs, unknown IDs and the former `custom` value are normalized to `ocean`; legacy custom colour integers are accepted during import but intentionally discarded. The optional `themeMode` preference accepts `system`, `dark` or `light`; missing and unknown values resolve safely to `system`, while explicit dark/light choices remain unchanged. New exports include only the stable preset and theme-mode IDs.

Each supplied table is an array of row objects. A table can be omitted to leave it untouched. Every included row must supply exactly its database columns, including explicit `null` for optional values. Export a backup from the app for the complete schema and field names; definitions also live in `data/local/Entities.kt`. Exported schema JSON is included in CI's validation artifact.

Rows merge by stable primary key: an existing row is updated; a new row is inserted. Omitted rows are retained, including existing relationship rows. Import is not an exact replacement or deletion format. Duplicate primary keys within a supplied table are rejected. Reimporting an unchanged file is idempotent. Keep stable IDs when correcting a prior import; use new IDs for new records.

All database writes occur in one transaction. Invalid types, values, references or catalogue changes cause rollback of the whole import. Preference validation occurs before database writes; the Settings screen applies valid preferences after the database transaction succeeds. File size is limited to 50 million characters.

## Primitive values

- Text must be a JSON string; numeric strings are not silently converted.
- Integer columns require whole numbers within their storage range. Counts, positions and other Int-backed fields cannot exceed 2,147,483,647.
- Boolean fields accept JSON `true`/`false` or integer `1`/`0`; backups export SQLite's integer form.
- Decimal values must be finite numbers. NaN, infinity and numeric overflow are rejected.
- Weights, rep counts, holds, rest durations, positions and timestamps cannot be negative. RPE is optional and must be between 0 and 10. Recorded bodyweight must be positive.
- Timestamps are Unix milliseconds, e.g. `1789461600000` = 15 September 2026, 08:40 UTC. Tracker `epochDay` is a local calendar date represented as days from 1 January 1970. Hold durations are milliseconds; programme hold/rest targets are seconds.
- Custom measurements/ROM may use signed values where appropriate. Their units must be preserved; no unit conversion occurs during import.

## Enumerations

| Field | Allowed values |
|---|---|
| mode | STRENGTH, STRETCHING |
| tag | STRENGTH, STRETCHING, BOTH |
| trackingType | WEIGHT_REPS, BODYWEIGHT_REPS, ASSISTED_REPS, HOLD, SKILL_HOLD, REPS, ROM_MEASUREMENT |
| role | PRIMARY, SECONDARY |
| workout status | DRAFT, FINISHED, DISCARDED |
| setType | WORKING, WARM_UP |
| result | COMPLETED, FAILED |
| tracker valueType | BOOLEAN, NUMBER, COUNT, DURATION, RATING, TEXT, CHOICE, DATETIME |
| targetComparison | AT_LEAST, AT_MOST, EXACTLY, or null |

Legacy workout snapshots may have an empty `trackingType`; new imports should supply the actual type. Completed zero attempts are permitted and excluded by progression calculations. Failed/partial attempts use `FAILED` and zero/null outcomes, never positive reps or holds.

## Definitions and relationships

The app must have seeded its catalogue before import. Seeded exercises, their aliases/muscle mappings, seeded variations, muscles and bands are read-only: identical rows from a backup are accepted, but changed or new seeded definitions are rejected. The one exception is exercise/variation `inputConfig`: user-editable measurement settings (including the one/many TUT toggle) can be restored without changing catalogue identities or historical snapshots. To customise an exercise, use a new ID, `isCustom: true` and its own aliases/mappings. Custom variations use `isSeeded: false`. `core:` and `free:` exercise ID prefixes are reserved. Cross-version catalogue reconciliation is not implemented; conflicting definitions require reconciliation before import.

`seed_metadata` is validated but ignored on import: it describes this installation's seeding state and must not control a different installation.

For historical workouts, retain `plannedExerciseId` for the intended movement and put the performed movement in `actualExerciseId`. A set's variation must belong to that performed exercise. Exercise and per-set variation name/type/input snapshots plus `workout_muscles` preserve history independently of future definition edits. Older version-1 documents that omit variation configuration or variation snapshots inherit them during import. Referenced muscles, exercises, sets, bands and other foreign-key targets must exist in the database or the same import.

Programme prescriptions (`targetSets`, rep/hold targets, `targetRpe` and `restSeconds`) belong to each `programme_exercises` assignment. Catalogue exercise and variation rows retain legacy prescription columns only so older version-1 backups round-trip without silently discarding data; current UI and workout creation ignore those legacy values. Schema 13 introduced assignment-owned prescriptions: it preserves every non-null assignment value, backfills only completely empty assignment prescriptions from legacy exercise rows, and snapshots structured assignment targets onto new workout exercises. Existing assignment rest is always preserved because older schemas cannot distinguish an intentional 120-second rest from a default.

Finished workouts require `finishedAt >= startedAt`. The merged database may contain at most one DRAFT. Supplying a second active draft is rejected; finish/discard the existing one or update its ID intentionally.

Deleting or adding a set slot in the logger persists the current slot count in that workout exercise's existing `targetSets` column, including zero. Surviving sets and entry drafts are renumbered without changing their IDs or measurements. Programme prescriptions and the original textual `targets` remain unchanged. The one-time Planche band correction changes configuration only in catalogue/variation defaults, assignments and DRAFT snapshots; finished snapshots and band selections are untouched. Neither behaviour changes the schema or version-1 envelope. Import remains a merge, not a way to propagate deletions.

Band link IDs are `band-yellow` (0.6 cm), `band-red` (1.2 cm), `band-black` (2.2 cm) and `band-purple` (3.1 cm). Each stacked band has a separate `workout_set_bands` row. No force conversion occurs during import.

Logger tick/done locks are device-local UI preferences keyed by stable set ID, not a new database status or backup preference. They survive app restart but are not transferred by JSON export/import; pre-existing local locks are not inferred from or rewritten by imported `result` values. New installations and sets without an explicit local tick start unlocked, including historical records. Band abbreviations and the logger's RPE-last presentation do not rename stored bands or rewrite saved measurement orders.

Habit values must match the field: a numerical field has only `numericValue`, a Boolean field only `booleanValue`, and text/choice/date-time fields only `textValue`. Other value columns are null. Counts are non-negative whole numbers; durations are non-negative. Numeric targets require both comparison and target value, or both null. There is one value per `(fieldId, epochDay)`.

Photos are not part of this JSON format; export them separately. Legacy set `notes` remains a database field for compatibility; new notes should be stored at exercise or workout level.

## Set timing (schema 16)

Exercise and variation `inputConfig` may include an optional `measurementOrder` entry, for example `measurementOrder=rpe,reps,weight`. Keys are `bodyweight`, `weight`, `reps`, `tut`, `totalTime`, `rpe`, `addedWeight` and `bands`. Unlisted measurements follow in that default order; disabled measurements are not shown. An omitted variation order inherits the exercise default for new sets. Recorded configurations retain their own order, including the legacy default when no order was saved. Duplicate entries, duplicate keys and unknown measurement keys are rejected transactionally. This uses existing configuration/snapshot columns; there is no additional schema or backup-version change.

`inputConfig` supports `manyTuts=true` or `manyTuts=false`. When omitted, new Handstand entries default to many and other exercises to one; saved variation configurations freeze the chosen setting. Exercise measurement settings supply the default; an explicit variation setting takes precedence.

`workout_sets.tutEntriesMillis` is a JSON-array string containing zero to eight non-negative integer millisecond durations, for example `"[1500,2000]"`. A non-empty array must sum to `holdMillis`; the scalar total keeps existing progress consumers compatible. `timeUnderTensionMillis` remains the separate total-time measurement and is not reinterpreted. Legacy version-1 rows may omit the new column: import supplies `"[]"` and retains both original scalar durations unchanged.

`workout_entry_drafts.tutEntries` is a JSON-array string of up to eight text inputs, retaining incomplete decimal entry. Older draft rows may omit it; import supplies `""`. Changing many to one does not remove existing attempts; the logger retains and displays them but does not offer additional slots. The envelope format/version remain unchanged.
