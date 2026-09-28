# Vibe Check product requirements

## Product truth

Vibe Check is a local-first native Android training log. It records objective history and derives explainable views from that history. Version one must not claim to know muscle fatigue or recovery. The strength SVG displays **freshness** derived from training recency; set-equivalents describe recent training dose. A future, separately labelled fatigue estimate may use RPE, subjective readiness and wearable data.

## Installation and test data

Production installs contain the exercise/muscle catalogue, anatomy vectors, band definitions and calculation rules, but no user programmes, routines, workouts, trackers, measurements or photographs.

Debug builds automatically load removable representative data: three strength programmes, four stretching routines, Piano/Meditation/Protein trackers, and representative history. Removing demo data preserves the exercise catalogue.

## Exercise catalogue

- Proper seeded database, canonical names, aliases, Strength/Stretching/Both tags and tracking types.
- Search by name, alias or muscle.
- Exercise-to-muscle mappings use the same canonical IDs as every tappable SVG region.
- Seeded definitions are read-only; duplicate them to customise.
- Custom exercises and custom additions to seeded skill-variation lists.
- Exercises with history are archived, never destructively removed.

## Programmes and workouts

- Programmes are not scheduled; the user starts any day whenever they choose.
- Create, edit, duplicate, delete and reorder programmes, days and exercises.
- Targets for sets, reps, holds, rest and optional RPE.
- Immediate set autosave and recovery of one active draft after process death.
- Drafts affect no history-derived view until **Finish workout**.
- Finish requires at least one completed non-zero working set. A rejected finish preserves the active workout and pending input; once that minimum is met, finish immediately without warnings about other incomplete rows.
- Notes at workout and exercise level. Set logging stays deliberately compact; individual sets do not have notes.
- Warm-up/working labels; failed/partial attempts are stored as zero and excluded from calculations.
- Previous performance, substitutions, supersets and circuits.

A substitution changes only the exercise performed in that workout slot. The substitute retains its own graph, history and PRs; unlike movements are never merged.

## Set data and bands

Support weights, reps, holds, left/right values, bodyweight, added weight, assistance, RPE, ROM measurements and notes. The nearest bodyweight measurement is copied into a workout and remains editable.

Bands share material, thickness and length, so width is the relative assistance proxy:

| Band | Width |
|---|---:|
| Yellow | 0.6 cm |
| Red | 1.2 cm |
| Black | 2.2 cm |
| Purple | 3.1 cm |

Stacks add their widths. Band-assisted performance is compared only within the same exercise variation.

Each set is logged in one compact row: one performance field, an optional RPE column and the relevant timer/action controls. Hold exercises provide a count-up timer that fills the editable performance field. Rest timers can start automatically or manually, use group-level rules for supersets/circuits, and notify/vibrate in the Android background.

## Muscle maps

Male/female front/back anatomy is a changeable profile setting. Left/right set results remain separate, but muscle freshness is bilateral. Every relevant path is tappable.

Strength and stretching maintain separate histories. A completed non-zero working set updates every mapped primary and secondary muscle. Warm-ups, zero attempts and drafts do not.

- Red: under 24 hours.
- Orange: 24–48 hours.
- Yellow: 48–72 hours.
- Green: 3–7 days.
- Blue: over 7 days.
- Neutral: never recorded.

Primary sets contribute 1 set-equivalent and secondary sets 0.5. Set-equivalents describe dose only; they do not gate freshness.

The daily historical scrubber reconstructs maps from records at the end of the selected day and never stores screenshots.

Finishing a valid strength or stretching session persists it before returning Home. Home then announces completion once, highlights only the mapped muscles affected by that session in deterministic order and shows a brief palette-themed celebration. Sessions without mapped muscles receive a generic acknowledgement; reduced motion removes confetti and staggered movement.

## Progress

- The strongest valid working set supplies the exercise session score.
- Weighted reps: `weight × (1 + reps / 30)`.
- Assisted reps use total band width and compare only the same variation.
- Skills account for structured progression level, hold/reps and assistance.
- Raw performance only before three sessions; first three valid sessions establish index 100.
- Rolling three-session line plus Rising/Flat/Falling label.
- Graph points expose raw performance, RPE and the exercise note recorded for that session.
- PRs for weight, reps, estimated 1RM, holds and calculated performance.
- ROM measurements live inside relevant stretches and remain separate from stretch freshness.

## Homepage and history

The homepage contains the freshness SVG and a calendar-month activity heatmap. The selected freshness date is authoritative: the slider exposes one step per selectable day in that displayed month, past months include every day, and the current month stops at today. The front/back figures use the same anatomy coordinates and hit regions with a 10% vertical presentation stretch; width is preserved and the full head, hands and feet remain visible.

A compact vertical key beside the maps uses the exact active preset's continuous Freshness scale from most recent at the top to least recent at the bottom. Its five ordered bands are under 24 hours, 24–48 hours, 48–72 hours, 3–7 days and over 7 days. Adjacent dated states blend in OKLab while scrubbing; no-data is a separate neutral and is never interpolated as an age stop.

- 0 activities: neutral.
- 1: light green.
- 2: medium green.
- 3 or more: dark green.

Every completed workout or stretching session counts separately. A measured tracker with a target counts when met; a targetless tracker counts when any entry exists. Tapping a day opens its history and reconstructed map.

## Open-ended trackers

Trackers contain any combination of Boolean, number, duration, count, rating, text, choice and date/time fields. Numerical fields use custom units and optional at-least/at-most/exact/range targets. Each field has one editable daily value. Trackers have no schedules or missed-day penalties. Metric charting is deferred, but the schema remains extensible.

## Data and platform

- One person per database.
- Local-first with sync-ready architecture; no provider selected initially.
- Validated structured JSON import, JSON backup/restore and CSV export.
- Historical notes are converted externally before import.
- Structured backup excludes photographs; photographs export separately.
- Searchable/editable history, bodyweight, measurements and progress photos.
- kg/lb, plate calculator, haptics, reduced motion and accessibility.
- Brief interaction haptics respect the haptics preference; body-map history colours interpolate while scrubbing unless reduced motion is enabled.
- No dead placeholder controls in test builds.

## Engineering sequence

1. Database/domain architecture and seed validation.
2. Complete programme → recovered draft → finished history → derived map/progress workflow.
3. Verify in the Android emulator.
4. Expand the remaining UI and integrations.

## Visual system

Every screen consumes semantic tokens for colour, typography, spacing, shape and motion. The four curated Ocean, Sunset, Forest and Mono presets provide complete light/dark Material roles and matching Freshness colours without screen-specific overrides. Appearance defaults to **Follow system**, while explicit **Dark** and **Light** choices persist across launches and backups. Dark variants use restrained layered neutral surfaces; Ocean dark maps its core roles to the official [VS Code Dark Modern theme](https://github.com/microsoft/vscode/blob/main/extensions/theme-defaults/themes/dark_modern.json) while retaining Vibe Check's own semantic Freshness colours and mobile state treatments.

Habit heat maps use a separate four-state scale: neutral/no activity, low, medium and strong. Every preset preserves that perceptual order; dark themes increase visual intensity rather than making low values disappear into the background. Habit choice previews, calendars and legends use the same centralized scale.

The app scaffold owns one context-sensitive extended Add FAB at logical bottom-start. It appears only for an available page-primary creation action and adds bottom scroll clearance without consuming page layout:

| Context | Shared action |
|---|---|
| Programme list | New programme |
| Programme editor | Add exercise or stretch, with day/type choice when needed |
| Habits | New habit |
| Exercise catalogue | New exercise |
| Active workout | Add exercise |
| Bodyweight and photos | Add photo for the selected day |

Home, Progress, More, History, Settings, Style and Archive deliberately have no shared Add action. Inline controls remain where they confirm a form or add within a nested structure: saving bodyweight, adding a set inside an exercise, adding a habit measurement or choice, adding a variation, attaching media, and export/restore actions. The shared FAB hides while the keyboard, a modal flow or a reorder drag owns interaction, and its visibility change is immediate under reduced motion.

The Freshness panel uses one clipped cached technical texture across its complete surface, including title, maps and legend. With no active workout at default type on a reference phone, the Work tracker consumes the measured remaining height without changing Freshness geometry or creating a scroll range, and its calendar divides the available grid height evenly across the displayed four, five or six week rows. Active workouts, constrained widths and larger type may scroll.
