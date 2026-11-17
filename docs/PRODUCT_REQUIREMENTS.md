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
- One Programmes list shows all active programmes without Strength/Stretch browsing tabs. Each card expands independently; opening or closing one does not close another. Existing programme activity classifications, days, assignments, IDs and saved ordering remain unchanged.
- **New programme** is the final scrolling list item, including when the list is empty, not a floating action. Naming a new programme opens its editor with an empty day; the inline **Add exercise** action must work immediately without leaving and reopening. Existing day selection and exercise/stretch choice remain available. New programmes retain the current training context as their default stored classification; browsing is no longer filtered by it.
- Targets for sets, reps, holds, rest and optional RPE.
- Immediate set autosave and recovery of one active draft after process death.
- Drafts affect no history-derived view until **Finish workout**.
- Finish requires at least one completed non-zero working set. A rejected finish preserves the active workout and pending input; once that minimum is met, finish immediately without warnings about other incomplete rows.
- Notes at workout and exercise level. Set logging stays deliberately compact; individual sets do not have notes.
- Warm-up/working labels; failed/partial attempts are stored as zero and excluded from calculations.
- Previous performance, supersets and circuits. Historical substitutions remain attached to the performed exercise; the logger no longer offers a substitution action or a **More** menu.

A substitution changes only the exercise performed in that workout slot. The substitute retains its own graph, history and PRs; unlike movements are never merged.

## Set data and bands

Support weights, reps, holds, left/right values, bodyweight, added weight, assistance, RPE, ROM measurements and notes. The nearest bodyweight measurement is copied into a workout when its bodyweight is missing. The logger has no workout-level Bodyweight button, replacement label or edit dialog; automatic measurement copying, stored workout bodyweight, historical snapshots and existing units remain unchanged.

Bands share material, thickness and length, so width is the relative assistance proxy:

| Band | Width |
|---|---:|
| Yellow | 0.6 cm |
| Red | 1.2 cm |
| Black | 2.2 cm |
| Purple | 3.1 cm |

Stacks add their widths. Band-assisted performance is compared only within the same exercise variation.

Logging follows the page/exercise/set/measurement hierarchy. Each exercise surface contains its set tiles and footer; its heading/background still collapses the section, with a pinned heading during editing. Set tiles have no header row. A small variant name overlaps the top-left border on the first set of each consecutive run; repeats use a tiny selector chevron. A, A, B, B, A therefore shows names on sets 1, 3 and 5. Runs preserve distinct variant identities and meaningful recorded-name changes, and recompute after edits/deletion. Selection remains available on completed sets. Truly variant-less sets omit redundant exercise-name titles and dead selectors; exercise headings and meaningful recorded/current names remain. Selectable headers have a wider horizontal hit area without increasing measured header/set heights or covering neighbouring fields.

One shared composable tile supports all 12 combinations of three resistance types (bodyweight, kilograms, bands) and four measurement types (repetitions, hold duration, skill hold, ROM). Renderer groups have presentation-only preferred sizing metadata named `large: Boolean`; only bands and skill hold prefer large. The shared tile resolves two or more large-preferring groups to compact, otherwise retaining each preference. It never mutates the preferred flags or counts selected bands/populated inputs. Completion/unlocking therefore cannot change profiles. This is not persistent exercise configuration or a transfer/schema field.

Controls have fixed dimensions within a profile at a given accessibility setting, consistent across exercises, variants and attempt counts. All numeric boxes use the same 24dp ordinary height; font scaling increases their height together. Single-duration fields use the same 64dp ordinary width as Reps/RPE. The independent Total time input has exactly the same width as each N-time/accumulated timing input in its effective profile: 28dp ordinarily when compact. Large skill hold uses spare measurement-group width for wider cells, capped at 48dp ordinarily and budgeted for four timing columns, the inline accumulated sum and the equally wide Total input, independently of whether one, four or eight attempts are present. Large cells shrink enough to keep the sum to Total's left rather than above it. Weight, RPE, completion and inter-group gaps retain their reserved widths. Ordinary numbers remain 15sp with 12sp units, timing numbers 12sp with 9sp units, and the Total heading/readout 10sp. Existing line-height budgets, limits and horizontal insets of 4dp/2dp remain.

Resistance, measurements and RPE pack from the left with compact outer gaps; only completion stays at the right edge. Other spare width remains after the groups. The ordinary eight-band/eight-attempt combination resolves both groups compact, with two internal four-column grids. Each timing row packs left with at most four columns. Equal thirds/halves were an implementation simplification, not a requirement. Bodyweight omits resistance entirely. Units, Reps and RPE sit beside their numbers on the same baseline in editable and completed fields, with a comfortable inset. Visible content is vertically centred; unlabelled fields reserve no blank label row. Added-weight and legacy Total labels sit above their fixed-height boxes. Numeric and completion targets remain smaller than the usual 48dp touch recommendation; this owner-directed compactness is not general accessibility acceptance. Longer values remain accessible within their fields. Errors appear below rather than resizing the box; compact fields retain their Error/Finish cue and full accessibility/set-level validation messages.

Skill-hold containers reserve at least two timing rows, including empty, single-row and locked sets: 50dp ordinarily, two 24dp frames plus their 2dp gap. One actual row stays vertically centred; further wrapped rows can grow the container. This minimum does not enlarge individual boxes or unrelated single-duration/Reps/kg tiles. Band-dot spacing remains unchanged, including the ordinary 72dp compact two-row band group. Numeric controls and taller boxed groups remain centred against each other, excluding trailing error height; the right-side tick centres within wrapped content. Narrow/large-font layouts wrap logical groups in resistance/measurement/RPE order, restarting at the left edge without silently changing a sole-large group's profile. Existing weight/added-weight backing fields and units remain; recorded ROM in centimetres is never relabelled as degrees. New ROM entries default to degrees.

Within each numeric field, the value and unit are centred together as one group. Empty unit/measurement cues are centred too. As digits are entered, the group expands outwards from the middle rather than remaining pinned to the right. Editable and completed values share baselines and retain the same box widths and stored units. RPE matches the neighbouring primary measurement's numeric and unit text sizes, including compact timing text, without changing its box dimensions or padding.

Logger typography uses medium-weight tabular numerals and quieter, regular-weight units on the same baseline. Existing numeric/unit styles and compact packing remain; RPE selects the neighbouring primary measurement's text style. Variant and previous-performance text retain their current sizes with stronger text contrast. Logger-local exercise/set surfaces use flat fills and softer inactive decorative outlines, retaining continuous set boundaries; shared app-wide surface styling is unchanged. Empty inputs have an inset fill and subtle outline; populated inputs have a neutral fill; focus adds a theme-coloured inset ring and selected fill without moving content. Completed values remain read-only without input chrome, with the existing highlighted tick and matching set outline. Selected band colours/outlines remain. These visual states do not alter entry, validation, completion, locking or rest behaviour.

The active-set background is owned by the workout page, so at most one set is highlighted across all exercises. Tapping a set, focusing one of its inputs or changing its values moves that highlight rather than leaving another exercise's set active. Completed ticks/borders and input focus states remain distinct from this page-wide selection; no stored measurement or completion state changes merely because a different set is selected.

On entry, selection waits for the logger's saved sets, drafts and exercise configuration to load, then defaults to the first unticked set in workout/exercise order, including partial and autosaved entries. Explicit tick locks are skipped; recorded/imported results without a tick remain eligible. If every existing set is ticked, none is selected and no set is created. Initial selection never opens the keyboard or forces scrolling, and later draft restoration cannot discard the selected logical set or override an explicit activation. Fields first appear with their restored values/configuration rather than animating from temporary empty controls. The optional completion-driven first-empty progression remains separate.

Restoring an exercise after collapse or scrolling is not a measurement edit. Ticking an untouched saved set must retain its complete recorded entity and snapshots; actual pending edits still save before locking. Edit tracking belongs to the workout page so it survives disposal of an individual exercise card.

Ordinary scrolling and initial content sizing do not animate exercise-card placement. Placement feedback applies only to actual order/index changes; the six-dot handle retains deliberate drag reordering, edge scrolling and its drag overlay.

RPE entry accepts optional decimals from 0 to 10 and rejects new out-of-range input instead of inserting it and displaying a verbose error. Existing invalid raw inputs are not clamped or rewritten; shortening or replacing them remains possible, and set-level validation retains a compact range cue until corrected. A small information icon centred on the top-right corner of each visible RPE field opens only a compact 240dp-wide, vertically scrollable two-column table headed **RPE** and **RIR**. The centre of the icon sits at the intersection of the top and right borders without changing field dimensions or centring. It replaces the exercise-footer RPE action. Populated locked RPE fields retain help; blank locked fields remain hidden. Approximate counts use `~`, and RPE 0-4 share one row with a non-numeric **Many** RIR estimate rather than implying precise counts.

The RPE help badge uses the same visible background as its field in empty, populated and focused states, and the set background when completed, with a muted information glyph rather than a bright surface disc.

Opening the on-screen keyboard keeps the focused field accessible, with the scrollable workout content reaching the keyboard rather than leaving an extra bottom-navigation-height gap above it.

Accumulated boxes and their Total group use matching 2dp horizontal and vertical spacing. Outer logical groups use an 8dp baseline gap. On nearly full single rows, a small optical adjustment balances Total-to-RPE, RPE-to-painted-check and painted-check-to-right-border gaps, accounting for the check icon's transparent inset rather than treating its whole touch frame as painted content. It does not redistribute large spare space or change input widths. Narrow layouts retain non-overlapping completion targets and wrap logical groups rather than shrink fixed controls.

The tick remains visible on every tile, disabled until the entry is valid. Ticking saves the current entry, locks its controls, shows only populated measurements/selected bands, and highlights the tick in the theme accent colour. Tapping the highlighted tick restores the original editable fields without starting rest. This explicit done/lock state survives app restart as local UI preferences keyed by stable set ID; it does not change autosave, recorded completion/result, History, schema or JSON backup. Existing/imported sets are not automatically marked done. Deleting a set, exercise or workout through the logger clears its associated locks. The keyboard's explicitly requested Next action retains its existing navigation. Completion starts configured positive rest in an active workout; keyboard progression, unlocking and editing finished workouts do not start rest. Redundant visible set-number headings and stand-alone set-count labels such as **3 sets** are omitted; accessible controls still identify the exercise and set. Stored counts and detailed rep/timing prescriptions are unchanged.

Settings offers **Scroll to next empty set**, a persistent device-local toggle defaulting OFF and excluded from backup/import preference whitelists. OFF means completion forces neither scrolling nor focus. ON reveals the next entirely empty existing set forward in workout/exercise order after successful explicit completion in an active DRAFT. It skips completed/partially entered sets, expands a collapsed target exercise if necessary, never wraps or creates a set, and does not move when no target exists or the target is already fully visible. It never focuses an input or opens the keyboard. Typing, autosave, unlock, recreation and finished-history editing do not progress. Manual scrolling cancels pending movement; stale targets are rejected, pinned headings remain clear and reduced motion uses immediate scrolling. Save/lock/rest ordering remains unchanged.

Completing or unlocking a set preserves its occupied height and internal layout footprint. Locked sets retain the selected bands' actual colours and positions, without making them interactive or showing unselected bands. Hidden empty measurements, unselected band slots and the offered next-attempt slot keep their layout space rather than collapsing the grids. The Total group retains its editing arrangement when locked, including compact mode's stacked arrangement for three populated attempts plus the hidden fourth slot; large mode retains its inline sum/Total arrangement. Hidden slots are not displayed controls or recorded values.

Timing inputs use **s** for seconds. The individual attempt inputs are the accumulated time boxes; their read-only sum is the accumulated value, while the independently editable overall duration is the total time box. Skill hold starts with one visible accumulated box. Completing a valid number (including zero) automatically reveals one empty next field, up to eight; empty, transient or invalid last inputs do not offer another field. There is no timing plus button. Existing internal gaps/raw editing buffers are retained, while surplus trailing empty fields remain hidden. Offered empty fields are not recorded zeroes or extra attempts. **Total** sits above the accumulated value. In large mode, the accumulated value and slash always sit to the left of the total time box, including at eight attempts. Compact mode keeps that inline arrangement below four visible boxes; at four or more, its order is Total label, accumulated value with slash, then the total time box. The automatically offered blank counts towards compact mode's layout threshold because it occupies a box, not towards the recorded measurements. Three 3-second attempts with 30 seconds upside down therefore show **9 s / 30 s**, retaining the three individual values. An empty set reserves neither a numerator row nor a left accumulated-value slot; Total labels its empty overall-duration field, and the two empty input boxes align with only their usual gap. Input sizes stay fixed across both arrangements. The Total group is 36dp ordinarily when inline/empty and 48dp when stacked, including its retained caption/readout above the 24dp input. It has the same lateral gap as between timing cells and no sum footer; narrow layouts may wrap the accumulated boxes or logical groups. This group height is distinct from numeric frame height and total occupied tile height. The denominator uses the existing total-time value; it is never derived from the attempts, and an empty denominator does not become zero. Recorded totals reopen with redundant decimal zeroes omitted without losing fractional precision or rewriting existing raw editing buffers. The count-up stopwatch stays removed. History/previous-result meanings and legacy total-only hold configurations are preserved. Invalid overall durations remain visibly editable with the existing numeric error handling. Locked tiles show populated attempts and recorded totals without offering another field; unlocking restores editing. **Add set** and **Notes** share a wrapping exercise footer.

Holding an individual populated N-time input reveals a small **x** control at that box's top-right corner, replacing the system text-selection toolbar for that input. Its visible circular badge is 16dp across, one-third smaller than the earlier 24dp badge, with a proportionally smaller glyph and the same 24dp hit area. Tapping the control removes just that attempt, shifts later inputs left in their existing order and recalculates the accumulated sum; independent Total, RPE, bands and other set values are unchanged. Tapping outside dismisses it without deleting. A **Show delete time** accessibility action reveals the same control. The last deletion leaves one empty timing input, not a recorded zero, and clears the previously saved timing values rather than leaving a hidden old attempt. Completed sets must be unlocked before deleting a time. No permanent controls or timing-cell dimensions are added.

The active rest countdown is one compact floating control fixed at the top-right of the workout page rather than scrolling with its content. It shows remaining minutes/seconds and retains tap-to-cancel, without a full-width backing strip or reserved bottom area. Starting rest does not also show a toast. The scheduled end alarm and completion notification, including their sound/vibration subject to Android permissions and channel settings, remain independent of this presentation. Persistence, expiration and cancellation keep their existing meanings; completion does not add a header row or shift the exercise list.

Workout exercise headings place the existing six-dot drag handle at the top-right when there is more than one exercise. Dragging it changes and saves only that workout's exercise order, with the shared drag feedback, edge scrolling and accessible earlier/later actions; it does not change programme order, exercise definitions, set ordinals or saved measurements. The handle is separate from header collapse and horizontal swipe-to-delete. In-progress draft values, notes and collapsed states follow their exercise IDs.

Variant names, repeat chevrons and previous-result border labels mask the outline with the parent surface above the border and the set surface below it. Their light-mode backgrounds blend into both surfaces rather than showing a child-coloured rectangular patch above the set. Text positions, header clearance and set heights are unchanged.

Previous performance sits on the top-right border, using the exact parent exercise, variant and ordinal in an earlier completed workout. Its compact summary uses `Prev: <resistance><unit>, <rep count>` without resistance/reps wording or previous RPE; bodyweight has no resistance value. Variant border titles use compact semibold text and selector chevrons use muted text colour; neither uses the selection accent. Skill hold shows `<attempt sum>s / <independent total>s`. Absent measurements are omitted, not filled with zero. Recorded ROM units are retained. Built-in bands use Yw/Rd/Bl/Pr; custom definitions use their current RGB hex code, since historical band snapshots do not contain colour. Deleted definitions fall back to their recorded names rather than inventing a historical colour. There is no other-position fallback or empty comparison placeholder.

Variant changes remain per-set, refresh renderers/comparisons immediately and do not start rest. Compatible values carry across; incompatible raw values, units and variant snapshots are buffered for switching back, including saved-instance restoration. Inactive-variant buffers are editing state, not a new database/backup archive. Persisted active measurements retain the existing save path. Metric settings offer exactly one resistance and one measurement choice; ambiguous legacy flag combinations require explicit review/selection rather than automatic conversion. Stored legacy flags/order and historical snapshots are not migrated or globally rewritten. Planche resolves to bands/hold; both Handstand variants inherit skill hold. Legacy total-only hold backing remains distinct when retaining that metric type. A hold-mode set retaining multiple recorded attempts shows their aggregate read-only and directs the user to Skill hold to edit them, rather than silently discarding attempts or mislabelling the first attempt as the aggregate.

Draft configuration resolution, numeric validation and set/variant conversion live in `WorkoutSetDraftLogic.kt`, independently of Compose rendering. The variant picker emits selection events; the workout screen retains editing state, save ordering and completion/rest effects. Saved-instance encoding and database/transfer formats are unchanged by this separation and the layout/access changes. Existing restored configuration buffers remain usable.

Bands use compact coloured dots, left-aligned within the resistance group, with at most eight configured bands. Large bands use one continuous row; compact bands use up to four per row, with two rows above four. Dot size, touch-box dimensions, spacing and order stay fixed in both profiles. Unselected colours have no white outline. Selected colours use only the accent outline, without a checkmark; accessible selected state and selection behaviour remain unchanged. Zero or multiple selections are allowed. Eight bands plus eight skill-hold attempts resolve both groups compact, retaining two four-column grids at the ordinary review width. Narrow/large-font layouts may use more timing rows or wrap logical groups without resizing cells; timing retains its four-column cap. Wall handstand remains bodyweight-only; generic worst-case screenshots use an explicitly custom test exercise, not a modified Wall handstand.

Set/metric configuration and band-catalogue editing are accessible only through the exercise DB, not a set's variant menu. Ambiguous legacy metrics explicitly direct the user there. The exercise DB's **Configure bands** action reuses the existing editor for stable ID, name, colour and positive physical width; the four built-in definitions remain read-only. Adding is disabled at eight; existing bands remain editable. Width remains the strength/order proxy. Moving configuration access does not introduce a new default-propagation policy or rewrite saved snapshots. Per-set variant selection, band selection and **Add set** remain in the logger. History uses explicitly recorded metric types without letting retained inactive fields override the active result; older history formatting remains unchanged.

Set and exercise deletion uses two separate left swipes: the first reveals a bin with the content resting one-third of its width to the left; a second left swipe deletes. A right swipe closes the reveal without deleting. A single long first swipe never deletes. The revealed bin and accessible reveal/delete/close actions provide non-gesture alternatives. Empty and recorded set slots can be deleted; remaining slots are renumbered and retained when reopened. Ordinary exercise saves, including notes entered from an older UI snapshot, preserve the current workout-local order and slot count, including zero; adding/deleting slots uses its dedicated mutations. Exercise deletion removes that workout entry and its children, not its catalogue definition or programme assignment. This does not add post-deletion undo. Individual N-time deletion uses the separate long-press action described above, not the set/exercise swipe.

All Planche variations offer band resistance with one TUT input by default. A one-time configuration correction enables bands in existing Planche defaults, assignments and active workouts without changing recorded measurements or finished-workout snapshots.

Rest timers use group-level rules for supersets/circuits and notify/vibrate in the Android background.

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
| Habits | New habit |
| Exercise catalogue | New exercise |
| Bodyweight and photos | Add photo for the selected day |

Home, Programmes (list and editor), Progress, More, History, Settings, Style, Archive and the workout logger deliberately have no shared Add action. Programmes uses a final inline **New programme** list item and an inline editor **Add exercise** action, retaining day/type choice when needed. The logger's existing **Add exercise** picker is an inline footer action immediately above **Finish workout**, or **Done editing** for a finished workout, including when no exercises exist. Other destinations' shared FABs remain unchanged. Inline controls also remain for saving bodyweight, adding a set inside an exercise, adding a habit measurement or choice, adding a variation, attaching media, and export/restore actions. The shared FAB hides while the keyboard, a modal flow or a reorder drag owns interaction, and its visibility change is immediate under reduced motion.

The Freshness panel uses one clipped cached technical texture across its complete surface, including title, maps and legend. With no active workout at default type on a reference phone, the Work tracker consumes the measured remaining height without changing Freshness geometry or creating a scroll range, and its calendar divides the available grid height evenly across the displayed four, five or six week rows. Active workouts, constrained widths and larger type may scroll.
