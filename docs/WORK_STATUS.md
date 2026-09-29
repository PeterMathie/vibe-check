# Work status and agent handoff

Updated: 29 September 2026. Owner of the current pass: GitHub Copilot release session.

Current release task: **Vibe Check 0.4.0 Beta 3 release preparation is in
progress** from exact merged `main` source
`51a88d57a1fe27a96ae11082f0b3a771fa617691`. The candidate identity is
`0.4.0-beta.3` (`versionCode 6`) with Room schema 15. Publication remains
gated on local validation, release-prep PR CI/merge, exact merged-main CI,
protected signed workflow verification and fresh/update API 35 validation.

Current implementation task: **ID-01 Vibe Check identity migration is complete** on `pmathie-cicpilot-persist-workout-drafts`. Checkpoint `0703a1d` changes the display name to Vibe Check, Android namespace/application ID to `com.petermathie.vibecheck`, Kotlin source/test package trees, Room schema FQCN path, launcher/workflow component names and app-owned filenames. The release identity is Vibe Check 0.4.0 Beta 1 (`versionName 0.4.0-beta.1`, `versionCode 4`). Vibe Check is intentionally a distinct Android install; Vibe Trainer app-private data does not transfer automatically. Legacy JSON `format: "vibe-trainer"` and catalogue source marker `vibe-trainer` remain stable compatibility identifiers. Focused identity, legacy-import and Room migration validation passed after a clean build.

Release preparation uses a persistent 3072-bit Vibe Check beta key outside the repository, Keychain-backed local credentials and four protected GitHub Actions secrets documented in [`SIGNING.md`](SIGNING.md). Release builds set `SEED_DEMO_DATA=false`; a fresh signed build-3 install visibly contained the complete exercise catalogue while Programmes, Habits, History and Body contained no personal/demo records or generated photos. Representative non-demo records were then imported through the public JSON UI. A same-key `adb install -r` update to final build 4 retained the programme, finished workout, habit completion and bodyweight.

The post-review gate passed 39 unit tests and all 70 API 35 instrumentation tests, plus debug and signed release assembly. The final APK installed and launched focused as `com.petermathie.vibecheck/.MainActivity`, reported `0.4.0-beta.1`/4 and label `Vibe Check`, and produced no AndroidRuntime fatal error. Local signed APK SHA-256: `666618d73ef0bd9986ebde7a3f7326789b446b41acaad8eadc5364816bb3949e`. Signing certificate SHA-256: `fe115398a3323fcfa54476e0c4714bea8807423fd1d2a3cf411c61bbf72f860a`. Portable JSON now round-trips unfinished workout-entry drafts; the signed workflow pins every Action commit and rejects any certificate other than that beta fingerprint.

Current documentation task: **DOC-01 reviewer-facing app showcase is complete** in [`APP_REVIEW.md`](APP_REVIEW.md). It covers product intent, a 12-image visual tour, complete feature areas, local storage/privacy, architecture/quality, honest external gates, installation and reviewer checklists. All screenshots are controlled generated demo data from source `762b96b`, 1080 × 2424, visually inspected, and total 1.9 MB. The exact APK SHA-256 is `603954816717f5c60304edcd04c0f5ab337b3a7e8b8a2b1edf2ab4e71605a61d`. The complete unit/build gate and all 67 API 35 instrumentation tests passed.

Current implementation task: **UX-29 archived content access is complete** on `pmathie-cicpilot-persist-workout-drafts`. More now exposes a dedicated Archive screen listing archived plans, custom exercises and habits with direct Restore actions. Focused `NavigationUiTest` passed both tests on API 35.

Current implementation task: **UX-28 variation-specific exercise settings and in-app Total Time stopwatch is complete** at `97d3028` and `e8c7d13` on `pmathie-cicpilot-persist-workout-drafts`.

Room schema 12 gives every variation its own tracking type, enabled inputs, targets, target RPE and rest duration. Migration 11→12 copies parent settings into existing variations, then applies the bespoke Wall/Freestanding handstand and assisted/bodyweight/weighted pull-up configurations. Workout rows switch inputs from the selected variation, reuse their durable set ID, and snapshot variation identity and configuration so later edits do not reinterpret history. Enabled Total Time fields provide a start/stop/reset/apply stopwatch. Exercise cards show concise bold Primary/Secondary muscle lines and support copied app-private reference videos with in-app playback and confirmed file-plus-metadata deletion.

Migration, import, editor, logging, recovery, no-duplicate-set, stopwatch, seeded-configuration and video-lifecycle regressions are included. At local source `e8c7d13`, `gradle testDebugUnitTest assembleDebug connectedDebugAndroidTest --no-daemon` passed with all 66 API 35 instrumentation tests. APK SHA-256 is `3c7362fe19ef0470dc2a83bce63e2c35482e13d55d80d15c12edc7370b171af4`. That exact APK installed and cold-launched on `vibe-log01-api35`; visible inspection confirmed the Handstand Primary/Secondary lines, its grouped Wall/Freestanding variations, and Wall settings enabling both Time Under Tension and Total Time.

Residual limits: reference videos are intentionally excluded from JSON backup and no hard attachment-count cap is enforced; explicit deletion provides user curation. Initial workout row count and its header rest action use the first-ranked variation. `97d3028` is pushed, but GitHub rejected the `e8c7d13` checkpoint push with HTTP 403 (`Permission to PeterMathie/vibe-check.git denied to pmathie_cicpilot`), so the gate fix and this status update are preserved locally and must be pushed when writable authentication returns. Nothing was merged and auto-merge remains disabled.

Current implementation task: **DEMO-01 complete demo-personal-data removal is complete** at `873a37c` and `27b8064` on `pmathie-cicpilot-persist-workout-drafts`.

Cleanup now removes demo workouts, programmes, trackers, body measurements and generated progress photos while preserving every catalogue/support table, custom catalogue records and real personal data. New generated photos receive explicit ownership markers; legacy demo photos are claimed only when their demo measurement ID, timestamp name, 720×960 dimensions and sampled deterministic colours all match. File failures are explicit and leave demo database roots intact. Seed metadata remains, so `seedIfNeeded()` does not recreate deleted personal demos. Settings and Style both require confirmation and display exact success or failure results.

`DemoDataRemovalTest` covers real-record/catalogue/photo preservation, cascaded demo removal, marker cleanup, restart suppression and a forced undeletable-photo failure. `SettingsUiTest` covers confirmation and exact result messaging. At source `27b8064`, `gradle testDebugUnitTest assembleDebug connectedDebugAndroidTest --no-daemon` passed with all 61 API 35 instrumentation tests. Exact installed APK SHA-256: `566a2d7c2446e407c8656055f882f833a6f1679f9fc7813b0b84d778cb2e82d3`.

Visible API 35 inspection invoked cleanup through Settings and reported 160 workouts, 4 programmes, 6 habits, 52 body entries and 4 photos removed. Exercises retained Back extension, Back squat, Bench press, Cossack squat and the rest of the maintained catalogue; Programmes, Habits and History contained no demo entries, and Body showed no demo measurements. No Room migration was needed. Residual limitation: photos are deleted before the Room transaction to guarantee that file errors cannot produce false database success; an unlikely later database failure could leave demo rows without their generated photos, recoverable by retrying cleanup.

Current implementation task: **UX-27 compact controls, collapsed Progress and reorderable Body is complete** at `5593d4a` and `ed67c48` on `pmathie-cicpilot-persist-workout-drafts`.

Exercises use a Strength/Stretch filter with a compact search/add row; custom exercises inherit the active classification. Generic timing fields now read Time Under Tension and Total Time, while Handstand retains its specific labels. Programme creation uses the shared full-width bottom plus action. Settings uses aligned information icons, on-demand precise-timer guidance and consistent shared action buttons.

All major Progress cards start title-only and expand from their header while keeping persisted reorder handles available. Body pins today’s entry controls first; its linked trend, calendar and selected-day photo cards are reorderable with persisted layout. Unit tests, debug assembly and affected API 35 Exercise, Programme, workout logging, Settings, Style, Progress, Measurements and Navigation suites passed; evidence is under `files/ux-31-controls/`.

Follow-ups `f9d47b6` and `75ee13e` replace permission-like Settings buttons with state-reflecting switches, render open-source notices instead of exposing raw Markdown, and keep both per-habit indicators and muscle-map anatomy/recency colours outside Style palette previews. `1124763` replaces the platform habit-choice dropdown with an adaptive themed grid; the tile colour uses the same shared shade calculation as Progress, giving the nine-state Mood tracker a 3×3 light/medium/dark preview. `df8d043` centers tile labels. `SettingsUiTest`, `StyleUiTest`, `HabitUiTest` and `ProgressUiTest` pass on API 35. The exact installed APK SHA-256 is `99d1646a59d676c6c007dd5705af687954a827a724a21dfde7bbb99f4ee00d25`; MainActivity is foregrounded with PID 8776.

Current implementation task: **UX-26 habit, anatomy and secondary-screen polish is complete** at `f843253` and `c9bf07b` on `pmathie-cicpilot-persist-workout-drafts`. Habit setup uses an icon-only curated picker, full-width plus action, centered Cancel and title-row New habit control; Progress heat maps now show each persisted habit icon. Never-trained mapped muscles use the neutral recency token rather than disappearing into the transparent SVG background.

Exercises are visibly separated into Strength and Stretching sections. Exercise and History cards use shared surfaces with evenly spaced outlined actions, and exercise settings expose only the unit selected by the existing Pounds preference. More uses left-aligned destination icons, with a strength icon for Exercises and a cog for Settings. Style now offers exactly four curated presets—Ocean, Sunset, Forest and Mono—with complete light/dark Material roles and preset-derived Freshness colours.

Unit tests passed, including all built-in palette contrast checks. Focused API 35 instrumentation passed for Exercises, History, Navigation/More, Style, Habits, Progress and the muscle map. Exact installed APK SHA-256: `41f65144b47d7713371f9f21484d51a53d3fa7c1523583b45972f5f52b9b6f39`. Inspected evidence is under `files/ux-30-ui-modernization/`.

Previous implementation task: **UX-25 visual habit choice scales and icons is complete**, including inline settings at `4439ea3`, compact rows at `0e9e3ce`, simplified actions at `3f764a8`, the clean settings layout at `c96b8b0` and expanded icon picker at `6b872f9`. Choose-from-list configuration lives directly in the main habit settings dialog, where compact draggable rows cross fixed Light/Medium and Medium/Dark divider lines to change shade. New choices begin in Medium. Progress classifies choice heat maps from the persisted boundaries. The seeded Mood scale maps Sad/Tired/Irritated to light, Tense/Neutral/Calm to medium, and Alert/Happy/Excited to dark.

Habit cards now render a persisted icon from a reusable catalogue and place the accessible pencil in the title row. Daily choice, number and text input saves automatically; clearing text removes that day's value, and the separate daily Save action is gone. Room schema 11 and migration 10→11 add icon and choice-boundary columns without replacing stored trackers or fields. Follow-up `5f4e0b5` applies the revised nine-state Mood vocabulary and upgrades existing debug demo data through seed version 10.

The final compact-row build passed unit tests and API 35 `HabitUiTest` (2); the preceding inline-editor checkpoint also passed `ProgressUiTest` (2) and debug app/test assembly. Exact installed APK SHA-256: `f52a20948af92ce1268e1472cadfa39db8c2d9c4067d96c511dc5c42fd0987d8`. Inspected screenshots and UI dumps are under `files/ux-26-inline-habits/`. The original UX-25 Habits (2), Progress (2) and 10→11 migration gate remain covered under `files/ux-25-habit-choice/`.

Settings-action follow-up `3f764a8` removes per-measurement archive controls and redundant non-numeric heat-map copy. Archive, Cancel and Save now share the dialog action row; permanent deletion remains available only from Archived habits and retains the empty-habit safety check. Choice remove icons now match the reorder handles in footprint and neutral grey. Unit tests and API 35 `HabitUiTest` (2) passed. Exact installed APK SHA-256: `2eaedfc29338211c238440dd974880e8612c6cccf87da201a3effc3cd995191f`; inspected evidence is under `files/ux-27-habit-settings/`.

Clean-layout follow-up `c96b8b0` shows `Choose from a list` instead of the Mood field name, removes explanatory choice-editor headings, exposes the compact persisted icon picker without opening the colour palette and gives the action footer full width so Archive aligns with the content edge. Unit tests and API 35 `HabitUiTest` (2) passed. Exact installed APK SHA-256: `cc288f2bf8e1ce90feb77c0084646d7f49912ea98ba2dc3ecb05777c96dd1daa`; inspected evidence is under `files/ux-28-clean-habit-settings/`.

Icon-picker follow-up `6b872f9` turns icon selection into a compact button opening a scrollable grid of 62 curated exercise, wellbeing, food, creative, study, work, household and social icons. Selection remains persisted through the existing schema. The settings action is now `Save`, and list choices use the standard plus icon. Unit tests and API 35 `HabitUiTest` (2) passed; the exact installed APK assembled successfully with SHA-256 `515da56c8446d2c89c1fdd19169bc738e3abf64f71da26ca09a3857efb8bac5b`. Inspected evidence is under `files/ux-29-icon-picker/`.

Previous task: **UX-24 linked bodyweight calendar and habit lifecycle is complete** at `386a598` on `pmathie-cicpilot-persist-workout-drafts`. Body now reuses the Progress line-chart component and replaces its long measurement/photo lists with a monthly calendar. Recorded days display their bodyweight; chart points and calendar cells drive the same selected date, whose card owns weight entry/editing and photos. Both pages observe the same Room stream, so edits update both.

Archived habits no longer appear in daily entry or Progress and are available in an Archived habits section for restoration. Permanent deletion is exposed only when no daily values exist and is rechecked transactionally before deletion. Unit tests and debug app/test assembly passed; focused API 35 Habits (2), Measurements (1) and Progress (2) suites passed. Exact APK SHA-256 is `1e02d004f6686c715d7959a8d5c253027982265386299745e84df7293194ae25`; installed evidence is `files/ux-24-body-calendar.png`.

Previous task: **UX-23 simplified habits and representative body photos is complete** at `272f902`. Habits always record against today and show their colour beside the card title. New measurements use Yes/no, Number with a user-set unit, Written note or Choose from a list. Ordered values map from light to dark, and demo data adds Mood, Journal, Reading and four linked progress-image placeholders.

Previous task: **UX-22 tracking display refinements is complete** at `6f3519e`. Home’s white and never-trained anatomy fills are transparent, the figures are 12% larger, and the activity card is titled Work tracker. Progress renders RPE as bars against an explicit 0–10 scale. Habit settings use 16dp vertical spacing, while the reduced-motion explanation is available on demand through `(i)`.

Previous task: **UX-21 fitted Home dashboard is complete** at `3d29f5a`. The Home screen no longer uses a vertical lazy list: its recency and five-week activity cards share the available viewport, with Home-only compact heat-map cells and the fixed navigation always visible. The recency slider uses a neutral grey track with only the current-position thumb in the accent colour.

Previous task: **UX-20 compact habit cards and progressive settings is complete** at `3062c46`. Default habit cards now show only the habit title, reorder handle, daily inputs and `Edit settings`. Colour, threshold, measurement, rename and archive controls live in a settings dialog; the colour palette appears only after `Change colour`. Thresholds read `Light below` and `Dark from` with a derived medium-range sentence. Debug seed version 5 gives Piano, Meditation and Protein representative light, medium and dark annual values. Unit tests and debug app/test assembly passed; focused API 35 Habits (1) and Progress (2) suites passed.

Previous task: **UX-19 distinct Body navigation and dedicated bodyweight entry is complete** at `18e2b48`. Plans retains the dumbbell icon while Body uses the raised-arms torso/person icon. The Body page no longer exposes an editable metric: new entries are always stored as `Bodyweight`. Body, Progress selection details and active-workout bodyweight labels display exactly two decimal places while preserving full stored precision.

Previous task: **UX-18 reorderable Progress cards, configurable habit intensity and direct navigation is complete** at `9b4e07f`.

Every major Progress card—overall trend, bodyweight, each habit and training progress—has a top-right six-dot handle and a persisted custom order. Exercise and variation selectors now live inside Training progress. Habit heat maps classify the actual daily numeric total against per-habit boundaries, using light/medium/dark alpha shades of the selected habit color. Piano and Meditation default to 7/15 minutes; Protein defaults to 140/160 grams.

Room schema 10 persists habit order and thresholds, with a preserving 9→10 migration. Habit setup now says Yes or no, Number, Count, Duration, Rating, Written note, Choose from a list and Date and time, and goal comparisons read At least, At most, Exactly or Between two values. Missing muscle snapshots are backfilled for all workout rows, restoring selected historical-day map highlighting. Habits and Body are direct destinations in the fixed Home/Plans/Progress/Habits/Body/More bar.

Unit tests, debug app/test assembly and focused API 35 instrumentation passed: Progress (2), Habits (1), migrations (8), History (2) and Navigation (1). Exact APK SHA-256 is `e51a218809c807f0636cdfab20f5190116887abc2df1391c34d9c73294374814`; installed evidence is under `files/ux-18-final/`.

Previous task: **UX-17 shared card spacing and sharper corners is complete** at `bdccd30`. Progress uses shared 16dp spacing and all shared cards use the centralized 16dp radius.

Each active habit has its own heat map using a persisted user-selected color. Room schema 9 and migration 8→9 add `trackers.colourArgb` without replacing existing tracker data. Progress demo version 4 retains the 156 annual workouts and adds 52 bodyweight records plus year-long Piano, Meditation and Protein values.

Validation passed 33 unit tests, debug app/test assembly, `ProgressUiTest` (2), `HabitUiTest` (1), `VibeDatabaseMigrationTest` (7) and `WorkoutLoggingUiTest` (6). The exact installed APK SHA-256 is `49b3154d80adc5727d4fbea3b0c051776fa82e13083978ae43036f6b277df96a`; `files/ux-16-final/progress-top.png` and `progress-lower.png` record the inspected charts and three distinct habit heat maps. Photo lookup deliberately reuses timestamp-named files and matches local calendar dates; photos captured on another date are not linked automatically.

Previous task: **UX-15 single-line Handstand labels and representative annual history is complete** at `f17d605`. Handstand uses single-line `Freestanding s` and `Total wall s` labels at 9sp. Progress demo version 3 established 156 deterministic workouts across all three strength programmes, with improving, stable and declining trajectories.

Current implementation task: **UX-14 compact autosaving workout rows and curated catalogue is complete** at `1753c9e` on `pmathie-cicpilot-persist-workout-drafts`. Active set rows no longer have a check button: every valid edit autosaves through the row's durable set ID, and later corrections update that set rather than creating duplicates. Clearing the final performance metric removes the saved set and retains recoverable draft state.

Handstand's Freestanding sec, Total wall sec and compact 68dp RPE inputs now share one row. RPE accepts at most two integer characters and rejects values above 10. The redundant Start hold timer and generic Workout notes controls are removed; the header rest-timer icon and per-exercise notes remain. Exercise and picker lists no longer stop at 100 records.

Fresh databases now seed only the 17 maintained exercises actually referenced by programmes. Existing unused free-catalogue rows and the unused Lat pulldown seed are archived only when no programme references them, keeping historical records valid. Validation passed 33 unit tests, debug app/test assembly and 14 focused API 35 instrumentation tests across logging, durable drafts and workflow/catalogue behavior. The exact installed APK SHA-256 is `1e769f52c0318c710ac3a63e5ed609ca427bd6d961ef457a39356cde71a364c6`; installed bounds and `files/ux-14-final/compact-handstand.png` confirm the three compact Handstand fields share one line without the removed controls.

Current implementation task: **UX-13 canonical exercise settings and flat Stretching programme is complete** at `d442e52` on `pmathie-cicpilot-persist-workout-drafts`. Exercise definitions now own resistance inputs, repetition targets, target RPE and rest duration. Programme editing no longer exposes conflicting copies; starting any programme snapshots the canonical exercise settings into the workout, preserving historical behavior if definitions later change. Room schema 8 deterministically migrates existing programme settings and leaves historical workout snapshots untouched.

Stretching is now one programme with one `Stretching` workout containing Front split, Forward fold, Side split and Bridge as ordered exercises. Fresh seed data uses this shape, and migration 7→8 consolidates the prior four seeded workout/category rows without changing historical logged workouts. Handstand keeps independent freestanding and total-wall time inputs.

The final gate passed 33 unit tests, debug app/test assembly, and focused API 35 instrumentation: `ExerciseEditorUiTest` (1), `ProgrammeUiTest` (4), `WorkoutWorkflowTest` (3), `VibeDatabaseMigrationTest` (6), `WorkoutLoggingUiTest` (6) and `WorkoutEntryDraftTest` (4). The exact installed APK SHA-256 is `f2b3f5cfd4230030e5227edcdf377604706838ad0f12c32911423f352b543b43`. Installed UI inspection expanded one Stretching card containing all four exercises; `files/ux-13-final/stretching-expanded-d442e52.png` records the result.

UX-13 residual scope is explicit: exercise catalogue reduction/curation was intentionally not attempted. The existing catalogue remains intact for a later dedicated task.

Current implementation task: **UX-12 correct programme start and structured set logging is complete** at `4826194` on `pmathie-cicpilot-persist-workout-drafts`. Programme-level Play starts the first ordered workout for that exact programme rather than opening edit context. An unrelated active draft produces an explicit Resume versus Discard and start decision; replacement transactionally discards the prior workout and clears its entry drafts only after confirmation. Maintained demo day names no longer imply weekday scheduling, while historical workout names remain untouched.

Active logging now renders the programmed set count as separate type-aware rows: weighted exercises expose Resistance, Reps and RPE; hold exercises expose Seconds and RPE; irrelevant resistance is absent. The rest timer is in the exercise header, notes sit below rows, and a full-width plus-only action adds recoverable extra rows. Room schema 6 changes unfinished entry drafts to a `(workoutExerciseId, ordinal)` key. Migration 5→6 preserves the prior draft, and per-ordinal submission consumes only that row while saved set IDs prevent duplicates.

Focused `ProgrammeUiTest`, `WorkoutLoggingUiTest`, `WorkoutEntryDraftTest`, `VibeDatabaseMigrationTest` and `WorkoutWorkflowTest` validation passed. The final combined gate passed 35 unit and 47 API 35 instrumentation tests plus debug assembly. The exact installed APK from `4826194f6b85271ae1d0cfda1ffb5825a7b5863a` has SHA-256 `842a97fc0a745a463929285744b3bbd1145a74aa557f7847c4d455ea546ce3ab`. `files/ux-12-final/home.png`, `programmes.png`, `start.png` and `legs-rows.png` confirm the visible fixed navigation, direct schedule-free Legs + Mobility launch, common Handstand warm-up, subsequent Back squat/Lunge content, structured rows, timer, notes and plus action. MainActivity is resumed on `vibe-log01-api35` with PID 9090; the crash buffer is empty.

UX-12 residuals are explicit: programme-level Play chooses the first ordered workout when a programme contains several rather than introducing a chooser, and exact historical workout names are intentionally not rewritten.

UX-11 exercise-aware personal records is complete at `a990214`. A distinct Personal records card now sits above the charts. Best performance exposes the highest existing domain score with its contributing set; the repetition-only and opaque calculated-performance rows are removed. Tracking metadata controls relevance: weighted exercises show available weight and estimated-1RM records, hold exercises show hold records, and unavailable or inapplicable metrics are absent rather than rendered as placeholders.

Focused domain and Compose coverage passed. The final clean full gate passed 35 unit tests and 43 API 35 instrumentation tests. Two preceding full attempts hit the known asynchronous in-memory Room teardown race in different unrelated tests; every Progress test passed on all attempts. The exact APK SHA-256 is `8972fd690469e8b335520ef89f34c0e94075be32b4e5dec037e11e823c737fbb`. Installed screenshots `files/ux-11-final/weighted-records-a990214.png` and `hold-records-a990214.png` confirm the visible Bench press versus Handstand filtering. MainActivity remains resumed with PID 29387 and no AndroidRuntime crash.

UX-10 compact Progress controls and annual demo is complete at `945117b`. Equal-width exercise and variation controls share one row; the variation control displays the selected variation name. The large inline methodology paragraph has moved behind a compact info action.

Debug progress seed version 2 supplies 52 weekly Planche and Bench press sessions across roughly one year, upgrading prior debug seed data by stable IDs while respecting deliberate demo removal. Compose coverage verifies the Handstand → Wall handstand dropdown label, on-demand methodology and the 52-session seed. The final combined gate passed with 34 unit tests and 43 API 35 instrumentation tests.

The exact APK SHA-256 is `66ce2582f2ff6858a031c4119c9ed1391250ac038fd4cd815b31fe6e313ac14d`. Installed screenshots under `files/ux-10-final/` show the compact selectors, selected variation and a year-scale chart from 24 September 2025 to 22 September 2026. MainActivity remains resumed with PID 27370 and no AndroidRuntime crash.

UX-09 Progress exercise eligibility is complete at `5b6c7ec`. Progress now uses a dedicated lazy picker containing only exercises backed by a finished workout and either a valid completed working set or ROM measurement. Catalogue-only and archived exercises are excluded, while eligible name/alias/muscle search remains. The shared catalogue picker’s 100-result cap no longer applies.

`ProgressUiTest` excludes an explicitly seeded no-history exercise, opens an eligible chart and scrolls through 105 eligible exercises to the final item. The final combined gate passed with 34 unit tests and 43 API 35 instrumentation tests. Exact APK SHA-256 is `76c8bb5c6250de2a1f69bec5925a7037079a079640ee4f48424de0840ea73782`. Installed screenshot `files/ux-09-final/progress-picker-5b6c7ec.png` shows the eight demo exercises with qualifying history; MainActivity remains resumed with PID 26663 and no AndroidRuntime crash.

UX-08 stable drag and programme preview is complete at `bbe3818`. Direction-change hysteresis prevents boundary hover from immediately reversing a drag. Tapping a programme name smoothly expands every workout/day exercise and target summary read-only, with decorative motion disabled by the reduced-motion preference. The programme editor’s Back control is now a left chevron with an accessible label.

Strength and Stretch share the same `ProgrammeEditor`, reorder state and persistence code. Their seeded data shapes differ: Stretch has one programme with four reorderable workout/day rows, each containing one exercise, so only genuinely singleton handles are hidden. Dedicated Compose coverage proves the Stretch preview and persisted day reorder. The final combined gate passed with 34 unit tests and 42 API 35 instrumentation tests; an initial mode-specific test wait and one known asynchronous History assertion flake passed after the wait correction and clean rerun.

The exact APK SHA-256 is `1c19e3338287da2698d4840c75426def10c889239f63b1159a2c476c002a1f12`. Installed screenshots `files/ux-08-final/programme-expanded-bbe3818.png`, `programme-editor-chevron-bbe3818.png` and `stretch-expanded-bbe3818.png`/`stretch-editor-reorder-bbe3818.png` show the requested behavior. MainActivity remains resumed with PID 25277 and no AndroidRuntime crash.

UX-07 drag feedback and motion is complete through `02608b9`. During a drag, the exercise row follows the thumb, displaced rows ease into place and the active handle shows a subtle 36dp dark-grey circle inside its unchanged 48dp touch target. The treatment is visible without clipping or becoming an oversized primary-green control. Reduced motion keeps direct manipulation while suppressing decorative transitions; accessibility reorder actions remain.

Focused `ProgrammeUiTest` and the final full validation gate passed with 33 unit tests and 41 API 35 instrumentation tests. The exact APK SHA-256 is `eca60be25baa16211a2677d64a0003cbc6892cfb50e9d637e424ac587980413c`. Installed interaction evidence is in `files/ux-07-final/active-drag-02608b9.png` and `settled-drag-02608b9.png`; the on-device database confirms the reordered positions. MainActivity remains resumed with PID 24088 and no AndroidRuntime crash.

UX-06 direct handle dragging is complete through `ca70b76`. The shared six-dot handle no longer requires a long press or sits behind an empty click target. A dedicated vertical `draggable` modifier claims normal thumb movement and commits once its stop callback runs; accessibility Move earlier/Move later actions remain.

Focused `ProgrammeUiTest` now drags without a hold and proves programme/day/exercise Room order, followed by full validation with 33 unit tests and 41 API 35 instrumentation tests. The exact APK SHA-256 is `5c052cf9f065fad606af57fa1333c2b4a3de4e260a499a06f7b40ac47187d34f`. A real `adb input swipe` moved Handstand below Back squat, and a direct read of the on-device Room database confirmed Back squat at position 0 and Handstand at position 1. Screenshot `files/ux-06-final/persisted-order-ca70b76.png` records the installed result; MainActivity remains resumed with PID 20421 and no crash.

UX-05 duplicated workout-card controls is complete through `372f22a`. Single-workout cards now start directly with Start workout and contain no repeated workout heading, Rename workout or Delete workout. The programme editor also no longer shows Add workout. Exercise edit/remove/reorder controls and the full-width plus remain directly available; multi-workout programmes retain compact titled reorder rows to distinguish their workouts.

Focused `ProgrammeUiTest` passed, followed by a clean full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` rerun with 33 unit tests and 41 API 35 instrumentation tests. An initial full run hit the existing `WorkoutLoggingUiTest` closed-connection teardown race; the unchanged test passed in the rerun. The exact APK SHA-256 is `55ff7035fb4bf69a3eb7c6d4dc76343c510b8a73280672d528263df746533455`. It is installed on visible `vibe-log01-api35`, where MainActivity remained resumed with PID 17232 and no AndroidRuntime crash. Screenshot `files/ux-05-final/programme-editor-a63b898.png` verifies the requested simplification without clipping or loss of the retained controls.

The Add workout follow-up received another focused and full passing gate (33 unit and 41 API 35 instrumentation tests). Its exact APK SHA-256 is `bfef1e2dd568929a3eb7c6d4dc76343c510b8a73280672d528263df746533455`; it is installed and resumed with PID 18170. Screenshot `files/ux-05-final/programme-editor-372f22a.png` and UI semantics confirm Add workout is absent and Start workout remains present.

UX-04 programme editor hierarchy polish is complete and remote-synced at `7d96f8b`. Visible drag handles lead their items and disappear for singleton lists; programme names are directly editable; Duplicate/Delete/Archive form a bottom management row; and each workout’s Add exercise action is a full-width accessible plus control. Programme deletion removes the definition and cascading templates while retaining historical workouts. Strength/Stretch appears only in the Home recency-card heading and Programmes title row.

Focused `ProgrammeUiTest`, `HistoryUiTest`, `EndUserControlsTest` and `HabitUiTest` passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` validation with 33 unit tests and 41 API 35 instrumentation tests. The exact APK SHA-256 is `626cf6d3ce834fbeaaf823822eb0578c7f0d894a218b4ea836d83069aff0259e`. It installed and cold-launched on visible `vibe-log01-api35` (Android 15/API 35); MainActivity remained resumed with PID 15321 and no AndroidRuntime crash was present. Exact-build screenshots under `files/ux-04-final/` cover Home, programme list, programme editor/day layout and bottom management controls, and historical details. Inspection found the requested hierarchy and placement intact with no unresolved visual defect. QA-01 real-phone behavior remains outside this emulator-backed slice.

UX-03 is complete at source commit `98a38ea`. The date slider now sits directly beneath the Home muscle-recency maps, displays its effective date and previews earlier recency without opening the detailed historical screen. Heat-map selection still opens that screen. Its reconstruction copy and slider are removed; Previous day and Next day are distinct far-left/far-right chevrons around the unchanged Strength/Stretch selector in one semantic row. The date, activity cards and back behavior remain intact.

Focused `HistoryUiTest` and `EndUserControlsTest` passed, followed by exact-final `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` with 33 unit tests and 41 API 35 instrumentation tests. The exact APK SHA-256 is `d743948ee1d9f515709d05b60c3ea63f2c29be5bf5b38e06249dd735b631b3bd`; it is installed on visible `vibe-log01-api35`, where MainActivity is focused with PID 13082. Screenshots `files/ux-03-final/home-98a38ea.png` and `history-day-98a38ea.png` were inspected and match the requested placement with no observed clipping or overlap.

Checkpoint 1 removes the repeated header and specified Home copy, establishes reusable visible action controls, and replaces every textual ordered-list control with a stable-key six-dot drag interaction plus accessibility reorder actions and completion-only persistence. Checkpoint 2 removes Workout from top-level navigation and introduces a fixed Home/Programmes/Progress/More bar whose More screen visibly exposes Exercises, Habits, History, Body, Settings and Style. Checkpoint 3 replaces the fragmented programme/day flow with compact list affordances and a coherent editor where workout exercise summaries are visible by default. Each checkpoint requires focused end-user evidence and a pushed status boundary; final acceptance additionally requires the full test/build suite and installed-emulator screenshot inspection.

UX-02 checkpoint 1 is complete locally. The repeated app header and specified Home headings are removed; the recency maps and activity heat map remain central, with Earlier/range/Later in one semantic row. `ActionControls.kt` now supplies the shared action hierarchy and six-dot reorder handle. Programme, programme-day, programme-exercise, tracker-field and variation ordering previews stable-key movement and invokes persistence once at gesture completion; Move earlier/Move later accessibility actions provide the non-touch equivalent. Focused `EndUserControlsTest`, `ProgrammeUiTest` and `HabitUiTest` passed on the API 35 emulator. Checkpoint 2 navigation is the next executable step.

UX-02 checkpoint 2 is complete locally. The fixed bottom `NavigationBar` shows Home, Programmes, Progress and More at 320dp with no horizontal scrolling or Workout destination. Home continuation and programme starts retain contextual access to the active workout editor. More visibly lists Exercises, Habits, History, Body, Settings and Style; back from those secondary screens returns to More. `NavigationUiTest` passed on API 35 and opens the actual Habits screen through More. Checkpoint 3 programme flow is the next executable step.

UX-02 checkpoint 3 is complete. Programme list cards now contain only the name, edit pencil, play/choose affordance and six-dot handle. The edit pencil opens one programme surface with rename/duplicate/archive, Add workout, every workout and its exercise/target summary visible by default, prominent Start workout, and an inline workout-edit mode for rename/delete/add exercise/edit targets/remove/reorder. The focused `ProgrammeUiTest`, `NavigationUiTest`, `EndUserControlsTest` and `WorkoutLoggingUiTest` suites passed on API 35; tests exercise the visible edit/start flow and physical touch-drag persistence for programmes, workouts and exercises.

UX-02 final verification at source commit `0a61560`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 33 unit tests and 40 API 35 instrumentation tests. The exact debug APK SHA-256 is `2d6997316bacd4f52b0113448c842de77a8265e4c17f09a23057b2035ccf08e9`; it installed and cold-launched on the visible `vibe-log01-api35` Android 15/API 35 emulator. MainActivity remained focused with PID 9389 and the post-launch AndroidRuntime log was empty. Exact-build screenshots are under the session artifact directory `files/ux-02-final/`: `home-0a61560.png`, `more-0a61560.png`, `programmes-0a61560.png` and `programme-editor-0a61560.png`. Inspection confirmed:

- Home has no repeated product header or removed explanatory headings; the mode selector clears the status bar, maps and heat map remain central, and Earlier/range/Later share one row.
- The fixed four-item navigation fits without scrolling; More visibly exposes Exercises, Habits, History, Body, Settings and Style.
- Programme cards show only name, pencil, play and six-dot drag handle.
- The programme editor shows readable secondary management controls, a prominent Start workout action and every workout exercise/target summary by default.
- An initial device screenshot exposed an Archive label wrapping mid-word; commit `9e9d570` corrected the action layout, and the final screenshot has no observed clipping or overlap.

UX-02 is complete. The emulator remains visible with the final app running. No merge, PR, auto-merge, signing or publication occurred.

ARCH-01 remains complete at its safe behavior-preserving boundary, and repository-controlled RELEASE-01 readiness remains complete. UX-02 does not reopen either effort. Generated `.gradle/` and `app/build/` content must remain ignored and untracked; QA-01 still requires a real Android phone.

The detailed, claimable checklist is [TODO.md](TODO.md). RUN-02 completed successfully on 22 September: compilation, unit tests, emulator tests, APK installation and launch all passed. Its screenshot was inspected and runtime-error log was empty. Outstanding implementation and real-device tasks remain open.

## Start here

- Repository: PeterMathie/vibe-check.
- Long-lived working branch: `pmathie-cicpilot-persist-workout-drafts`. Keep all continuation work on this branch.
- Historical draft PR: https://github.com/PeterMathie/vibe-check/pull/1 (targets `develop`). Do not merge it or enable auto-merge. A future PR may be draft/status-only unless the user explicitly authorises merging.
- Product contract: `docs/PRODUCT_REQUIREMENTS.md`; tester walkthrough: `docs/BETA_TESTING.md`.
- Native Kotlin / Compose / Room / Hilt app. This branch has no runnable web version.
- User has authorised replacing the old implementation and completing the beta. No further approval is needed for ordinary implementation or tests.
- Latest locally verified implementation commit: `562bb0b`.
- Latest successful branch run: https://github.com/PeterMathie/vibe-check/actions/runs/35749861428 at final architecture/repository-readiness checkpoint `cf2c8ba`.
- APK artifact: `vibe-check-debug`; reports and generated Room schemas: `validation-reports`.

## What is implemented

- Full bundled exercise catalogue plus curated skills/stretches, canonical muscle mappings, aliases, tags, tracking types, search, custom definitions, duplication and archival. Custom variations can be added/reordered; seeded ordering is protected.
- Programme/day/exercise creation, editing, duplication, ordering, archival/removal, targets, rest and circuit groups.
- Room-backed workout drafts and saved sets; finish-gated maps/progress; compact performance entry, RPE, exercise/workout notes, warm-ups, failed attempts, unilateral fields, bands, holds, substitutions and previous-session sets.
- Actual exercise identity owns its graph after substitution. Name, tracking type, targets and muscle mappings are snapshotted for real workouts and demos.
- Separate strength/stretch recency maps using male/female front/back vectors, exact selected-path outlines and accessibility actions; historical day navigation, back navigation, mode switch and muscle explanations.
- Exercise session scoring, baseline/rolling index, variation filters, raw performance/notes, aligned RPE graph, PR summaries and separate ROM view.
- Progress charts expose value/date axes and pointer detail, ROM is charted per unit, PR types are explicit, and skill-index copy explains its heuristic and personal baseline.
- Historical progress and tracker activity are deterministic after Room v5: submitted sets retain variation rank and band definitions, while tracker days retain their target outcome.
- Curated preset changes/restores refresh immediately, legacy custom values normalize safely to Ocean, and reduced motion is available throughout the semantic theme.
- Homepage activity heatmap, historical calendar navigation, editable/searchable finished workouts, custom multi-field habits and daily totals with numeric targets.
- Habit fields support configured choices, date/time pickers, inclusive range targets, archival/restoration and reordering while retaining historical values.
- Body measurements and photo import/removal/export; structured JSON merge/restore, CSV including band stacks and notes, last-backup indicator, backed-up profile preferences.
- AlarmManager rest alerts, notification/precise-alarm permission controls, hold timer, units/plate calculator, haptics and reduced-motion ripple control.
- Central palette tokens, four curated light/dark presets, debug demo history and removable demo records. Production personal data starts empty.

## Fixes completed in this continuation

- Added Room-backed recovery for unfinished compact and Bands/details workout input. Added a v2 → v3 migration, stable idempotent set submission and cleanup on cancellation/finish/deletion.
- Fixed CI KVM access so emulator verification actually runs.
- Added programme ordering and history date corrections.
- Serialised editor writes; retained historical exercise/muscle snapshots.
- Added transactional backup tests, invalid-import rollback test and a draft-gating/history-preservation regression test.
- Kept band-assisted and unassisted reps on the same relative scale for assisted-rep exercises; added a regression test for removing the last band.
- Corrected PR search to inspect all valid sets, not only session-score winners.
- Kept missing RPE as chart gaps at the correct session positions.
- Fixed left/right results retaining obsolete shared reps and validated numeric input.
- Added full previous-session sets, exercise-note history search and immediate map refresh after Finish.
- Added editable measurements, removable photos and photo I/O error reporting.
- Wrote the beta walkthrough and this handoff.

## Latest verification

Local ARCH-01l verification at commit `562bb0b`: `HoldTimerButtonTest` and focused `WorkoutLoggingUiTest`/`WorkoutEntryDraftTest` passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease` validation with 33 unit tests and 36 API 35 instrumentation tests. GitHub Actions run `35749861428` also passed at `cf2c8ba`.

Local ARCH-01k verification at commit `d5e0fcd`: three `CompactEntryFormTest` cases plus `SetDetailsFormTest`, `WorkoutLoggingUiTest` and `WorkoutEntryDraftTest` passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` validation with 33 unit tests and 35 API 35 instrumentation tests.

Local ARCH-01j verification at commit `3ad34a8`: three `TrackerEditorStoreTest` cases and focused `HabitFieldFormTest`/`HabitUiTest`/`HabitFieldTest` passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` validation with 30 unit tests and 35 API 35 instrumentation tests.

Local ARCH-01i verification at commit `01fe0ce`: four `HabitFieldFormTest` cases and focused `HabitUiTest`/`HabitFieldTest` passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` validation with 30 unit tests and 32 API 35 instrumentation tests.

Local ARCH-01h verification at commit `6d5923e`: focused `HabitUiTest` and `HabitFieldTest` plus debug assembly passed, followed by full `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` validation with 26 unit tests and 32 API 35 instrumentation tests.

Local ARCH-01g verification at commit `89bb498`: focused `SetDetailsFormTest`, `WorkoutLoggingUiTest` and `WorkoutEntryDraftTest` passed, then `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease` passed with 26 unit tests and 32 API 35 instrumentation tests. History date navigation subsequently passed three consecutive focused emulator runs at `3ab8eee`; GitHub Actions run `35744137335` passed for that commit.

Local ARCH-01f verification at commit `2ef436f`: five `SetDetailsFormTest` cases plus `SessionProgressTest` passed; focused `WorkoutLoggingUiTest` and `WorkoutEntryDraftTest` passed; then `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest :app:assembleRelease` passed with 26 unit tests and 32 API 35 instrumentation tests.

Local RELEASE-01b verification at commit `f6174cd`: `releaseRuntimeClasspath` and `coreLibraryDesugaring` resolved successfully, the repository and asset notices matched byte-for-byte, `gradle :app:assembleRelease` passed, and APK inspection found `assets/THIRD_PARTY_NOTICES.md` plus `assets/FREE_EXERCISE_DB_LICENSE.md`.

GitHub Actions runs `35740721923` and `35742800627` exposed two stages of the same `HistoryUiTest` race: the navigated date required the unmerged semantics tree and an explicit wait for asynchronous `StateFlow` recomposition. Commit `3ab8eee` applies both corrections; three consecutive focused local runs and replacement GitHub Actions run `35744137335` passed.

Local RELEASE-01a verification: `gradle :app:assembleRelease` passed for version `0.3.0`/code `2`, including release lint, and produced `app/build/outputs/apk/release/app-release-unsigned.apk`. The artifact is intentionally unsigned and untracked.

Local ARCH-01e verification at commit `a92bb2c`: focused `OrderedItemsTest`, `ProgrammeUiTest` and `HabitFieldTest` passed, then the full unit/instrumentation/build command passed with 21 unit tests and 32 API 35 instrumentation tests.

Local ARCH-01d verification at commit `adec0d0`: focused `PaletteContrastTest` and `StyleUiTest` passed, then the full unit/instrumentation/build command passed with 19 unit tests and 32 API 35 instrumentation tests.

Local ARCH-01c verification at commit `8fe0eb6`: focused `HistoryUiTest` and `MuscleMapUiTest` passed, then the full unit/instrumentation/build command passed with 19 unit tests and 32 API 35 instrumentation tests.

Local ARCH-01b verification at commit `41fef6c`: focused `ProgrammeEntryFormTest` and `ProgrammeUiTest` passed, then the full unit/instrumentation/build command passed with 19 unit tests and 32 API 35 instrumentation tests. Generated XML reports contain zero failures or errors.

Local ARCH-01a verification at commit `dd1beb9`: focused `ProgrammeUiTest`, `WorkoutLoggingUiTest` and `HistoryUiTest` passed, then the full unit/instrumentation/build command passed with 17 unit tests and 32 API 35 instrumentation tests. The removed private screens and helpers had no callers, and the reachable editors retained their existing UI coverage.

Local UI-01e verification at commit `8af0d1a`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 17 unit tests and 32 API 35 instrumentation tests. `MuscleMapUiTest` inventories all 13 front and 14 back region actions on male/female maps, invokes representative chest/lat actions, and verifies TalkBack selection state follows the selected outline.

Local UI-01d verification at commit `dd3bf1a`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 17 unit tests and 31 API 35 instrumentation tests. `HistoryUiTest` navigates seeded strength and stretching history, verifies selected mode semantics, changes historical day and returns from the historical view.

Local UI-01c verification at commit `6ad1a68`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 17 unit tests and 30 API 35 instrumentation tests. `WorkoutLoggingUiTest` covers keyboard-open notes, unilateral values, stacked bands, hold correction, substitution after saved history, and a 320dp-wide/2.0-font-scale layout. Persisted Room assertions prove corrected/substituted entries do not rewrite or duplicate the previously saved set.

Local UI-01b verification at commit `708129e`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 17 unit tests and 26 API 35 instrumentation tests. `ProgrammeUiTest` now covers the complete top-level programme lifecycle, including order and archive persistence; generated Gradle XML/HTML reports remain ignored build artifacts.

Local STYLE-01 verification at commit `94cb902`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 17 unit tests and 25 API 35 instrumentation tests. `PaletteContrastTest` checks WCAG ratio calculation and accepted/rejected custom combinations; `StyleUiTest` provides API 35 interaction evidence for immediate edit and restored-preference refresh, invalid contrast feedback, restored-palette rejection, and live reduced-motion state.

Local DATA-03 verification at commits `e6f969f` and `885a6f6`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 15 unit tests and 24 API 35 instrumentation tests. Coverage proves later variation/band/target edits cannot rewrite saved history, validates Room v4 → v5 backfill/schema, and keeps the published legacy import example idempotent. Pre-v5 definition edits cannot be reconstructed; migration/import snapshots the definitions available at upgrade/import.

Local PROGRESS-01 verification at commit `9dbb4c7`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 14 unit tests and 22 API 35 instrumentation tests. Two new session-progress tests cover ROM unit separation and explicit repetition versus calculated-performance records; `ProgressUiTest` selects seeded Planche history and verifies chart semantics, heuristic explanation and explicit PR labels.

Local HABIT-01 verification at commit `eb4d9d8`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 12 unit tests and 21 API 35 instrumentation tests. New evidence includes `HabitUiTest` exercising configured choice creation and daily selection, two `HabitFieldTest` cases for inclusive ranges and archived history, strengthened invalid-import rollback, and `VibeDatabaseMigrationTest` coverage for v3 → v4 data/schema preservation.

Local LOG-01 verification at commit `f868c50`: `gradle :app:testDebugUnitTest :app:assembleDebug :app:connectedDebugAndroidTest` passed with 12 unit tests and 17 API 35 emulator tests. The instrumentation total includes three `WorkoutEntryDraftTest` cases for process/database recreation, transactional no-duplicate submission, cancellation/finish/deletion cleanup and finished-workout stale-write rejection, plus `VibeDatabaseMigrationTest` for v2 → v3 data preservation and schema validation. Commits through `e00cdee` are pushed to the long-lived remote branch; a branch CI run is the next remote verification step.

Run `35705645513` at commit `39739ff` passed unit tests, debug assembly, API 35 instrumentation tests, APK installation and MainActivity launch. Its instrumentation reports include all seven `ImportValidationTest` cases, establishing DATA-02's scoped domain validation, transactional rollback, seeded-definition protection and published-example import. The captured home screenshot was inspected and shows readable light status-bar icons on the dark palette, establishing UI-01a only. The remaining programme, compact logging, history, SVG and TalkBack checks under UI-01 are still unverified.

Earlier run `35639647804` at commit `c071717` passed compilation, unit tests and instrumentation tests, then failed the added launch command because the test runner had uninstalled the target APK. The verified revision explicitly installs the built APK before launching it. That launch-harness failure is resolved.

Downloads for the verified build:
- APK ZIP: https://github.com/PeterMathie/vibe-check/actions/runs/35705645513/artifacts/10684382510
- Reports, schema and launch screenshot: https://github.com/PeterMathie/vibe-check/actions/runs/35705645513/artifacts/10684731891

The documentation-only handoff update after this verified commit does not alter application code.

## Remaining external or evidence-blocked work

The repository-controlled beta implementation and readiness work is complete. These gates cannot be truthfully closed by emulator or repository changes alone:

| ID | Priority | Task and acceptance criteria | Main files |
|---|---|---|---|
| QA-01 | High | Install the newest APK on a real Android phone. Check background rest notification/sound/vibration while locked, denied permissions, timer replacement and auto/manual circuits. Record device/version/results. | `ui/RestTimer.kt`, `ui/WorkoutEditor.kt` |
| DATA-01 | High | Add an actual v1 → v2 upgrade test using an exported v1 schema/fixture. Verify user records survive, Room validates the migrated schema, and snapshots/ordering exist. Current tests use fresh in-memory databases. | `di/DataModule.kt`, `data/local/Entities.kt`, `androidTest` |
| Release external gates | High | Review exact dependency licence/NOTICE distribution requirements, obtain legal approval, provide protected signing credentials, configure the store listing/data-safety declaration, verify a signed artifact and publish only with explicit authorization. | `docs/RELEASE.md`, `THIRD_PARTY_NOTICES.md`, external CI/store configuration |

Future architecture work is not an active task. Splitting `WorkoutEditor` exercise-card orchestration or the remaining cross-aggregate `EditorViewModel` operations would alter lifecycle/transaction boundaries and should begin only with a new bounded claim and purpose-built regression coverage.
| DATA-03 | Medium | Review history immutability for variation ordering/band definitions and habit-target edits. Exercise names/types/muscle mappings are snapshotted, but some derived history still reads live definitions. Decide and test historical policy before widening editors. | `data/local/*`, `domain/progress/SessionProgress.kt` |
| STYLE-01 | Complete | Exactly four curated presets replace free-form custom colours; legacy values normalize to Ocean. Contrast, live switching and reduced motion are covered. | `ui/theme/VibeDesignSystem.kt`, `ui/VibeCheckApp.kt` |
| ARCH-01 | Medium | Refactor dense editor composables into smaller screens/state holders and move persistence/validation out of UI-facing code. Remove superseded private screens in `VibeCheckApp.kt`. Preserve behaviour and avoid a new visual redesign. | `ui/*`, `ui/EditorViewModel.kt` |
| RELEASE-01 | Later | Release signing/versioning, complete dependency-license inventory, migration policy and store preparation. Asset notices are bundled; that is not a complete release/legal audit. | Gradle, manifest, notices |

Cloud provider selection/sync and detailed habit metric charts are deliberately deferred by the user. Do not choose a provider or build those charts as an incidental change.

## Non-negotiable product decisions

- Maps describe recency, not measured fatigue. Primary/secondary weighting describes dose and does not gate recency.
- Draft workouts do not update derived views. Zero/failed/warm-up sets do not affect strength progression.
- No per-set notes UI; notes belong to exercises/workouts.
- No programme scheduling. No missing-set warning on Finish.
- Bands are Yellow 0.6 cm, Red 1.2 cm, Black 2.2 cm, Purple 3.1 cm; stack widths. Compare only within the same exercise variation. No claims of calibrated force.
- Substitutes keep their own progress graph; never combine lat-pulldown weights with pull-up results.
- One profile/database. Fresh production install has no personal routines. Demo data is for test builds.
- Keep the existing app structure with Strength/Stretching mode selection; do not split it into unrelated dashboards.

## Working and verification

Use JDK 17, Gradle 9.6 and Android SDK 36 (see CI). Commands:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
gradle :app:connectedDebugAndroidTest
```

This workspace currently has Android SDK/adb and the `vibe-log01-api35` emulator available. Run the local gate above before relying on older GitHub Actions evidence.

Update this document after each task: owner, commit, actual verification result and remaining limits. Do not mark a feature complete merely because a button exists or a unit test passes.
