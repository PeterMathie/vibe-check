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
- Seeded catalogue identities are read-only; duplicate them to customise. Measurement settings, including one/many TUT entry, are editable and portable in backups.
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
- Previous performance, supersets and circuits. Historical substitutions remain attached to the performed exercise; the logger no longer offers a substitution action or a **More** menu.

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

Logging follows the page/exercise/set/measurement hierarchy. Each exercise surface contains its set tiles and footer; its heading/background still collapses the section, with a pinned heading during editing. Every tile has an understated chevron/name variant selector, including completed tiles and consecutive sets of the same variant. Exercises without explicit variants use their exercise name as the default. There is no separate swap button.

One shared composable tile supports all 12 combinations of three resistance types (bodyweight, kilograms, bands) and four measurement types (repetitions, hold duration, skill hold, ROM). Its logical columns are resistance, measurement, narrow optional RPE and completion. Numeric controls are content-sized with subtle filled backgrounds rather than nested outlines. RPE and completion stay at the trailing edge; resistance/measurement wrap below when necessary for narrow screens or larger fonts. Bodyweight renders no redundant resistance field or label. Existing weight/added-weight backing fields and units are retained; recorded ROM in centimetres is never relabelled as degrees. New ROM entries default to degrees.

The tick remains visible on every tile, disabled until the entry is valid. Ticking saves the current entry, locks its controls, shows only populated measurements/selected bands, and highlights the tick in the theme accent colour. Tapping the highlighted tick restores the original editable fields without starting rest. This explicit done/lock state survives app restart as local UI preferences keyed by stable set ID; it does not change autosave, recorded completion/result, History, schema or JSON backup. Existing/imported sets are not automatically marked done. Deleting a set, exercise or workout through the logger clears its associated locks. The keyboard's Next action or completing with the tick selects the next empty set. Completion also starts configured positive rest in an active workout; keyboard progression, unlocking and editing finished workouts do not start rest. Redundant visible set-number headings and stand-alone set-count labels such as **3 sets** are omitted; accessible controls still identify the exercise and set. Stored counts and detailed rep/timing prescriptions are unchanged.

Timing inputs use **s** for seconds. Skill hold starts with one attempt, adds up to eight, uses at most four columns (fewer at large font sizes), and places independent editable Total Time/stopwatch after the attempts. The **+** occupies the next attempt's grid cell and disappears at eight attempts. All inputs are accessible without expanding a tile. The attempt sum never overwrites Total Time. Locked tiles retain populated individual attempts and Total Time; unlocking restores editing. Empty inputs remain empty, not zero. Previous values appear subtly under their corresponding current fields, including individual attempts and total duration: exact parent exercise, exact variant and exact ordinal in an earlier completed workout. No other-position fallback or empty comparison placeholder is shown. **Add set** and **Notes** share a wrapping exercise footer.

Variant changes remain per-set, refresh renderers/comparisons immediately and do not start rest. Compatible values carry across; incompatible raw values, units and variant snapshots are buffered for switching back, including saved-instance restoration. Inactive-variant buffers are editing state, not a new database/backup archive. Persisted active measurements retain the existing save path. Metric settings offer exactly one resistance and one measurement choice; ambiguous legacy flag combinations require explicit review/selection rather than automatic conversion. Stored legacy flags/order and historical snapshots are not migrated or globally rewritten. Planche resolves to bands/hold; both Handstand variants inherit skill hold. Legacy total-only hold backing remains distinct when retaining that metric type. A hold-mode set retaining multiple recorded attempts shows their aggregate read-only and directs the user to Skill hold to edit them, rather than silently discarding attempts or mislabelling the first attempt as the aggregate.

Bands use fixed coloured dots within compact 24dp-wide, 48dp-high targets, always left-aligned, with at most eight configured bands and four per row (four: one row; eight: 2×4). Rings/checks and accessible selected states supplement colour. Zero or multiple selections are allowed. The worst-case eight-band/eight-attempt configuration keeps resistance, measurement, RPE and completion side by side at the ordinary 411dp review size; both internal grids remain 2×4. Narrow/large-font layouts retain a usable reflow rather than clipping. Wall handstand remains bodyweight-only; generic worst-case screenshots use an explicitly custom test exercise, not a modified Wall handstand. The selector's **Configure bands** action adds/edits custom bands with stable ID, name, colour and positive physical width; the four built-in definitions remain read-only. Adding is disabled at eight; existing bands remain editable. Width remains the strength/order proxy. **Configure metrics** provides the exclusive type selectors. History uses explicitly recorded metric types without letting retained inactive fields override the active result; older history formatting remains unchanged.

Set and exercise deletion uses two separate left swipes: the first reveals a bin with the content resting one-third of its width to the left; a second left swipe deletes. A right swipe closes the reveal without deleting. A single long first swipe never deletes. The revealed bin and accessible reveal/delete/close actions provide non-gesture alternatives. Empty and recorded set slots can be deleted; remaining slots are renumbered and retained when reopened. Exercise deletion removes that workout entry and its children, not its catalogue definition or programme assignment. This does not add post-deletion undo, exercise reordering or individual-TUT removal.

All Planche variations offer band resistance with one TUT input by default. A one-time configuration correction enables bands in existing Planche defaults, assignments and active workouts without changing recorded measurements or finished-workout snapshots.

The total-time field provides a count-up stopwatch. Rest timers use group-level rules for supersets/circuits and notify/vibrate in the Android background.

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
