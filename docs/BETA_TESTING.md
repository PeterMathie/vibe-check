# Beta testing

Use the debug APK from the branch or Android Actions run being reviewed, and record its source revision and build identity. Install `app-debug.apk` on Android. This is a debug beta, not a signed store release.

Release versioning, migration gates and unsigned build checks are documented in
[RELEASE.md](RELEASE.md).

Debug installs include sample programmes, stretches, habits and history. Production installs contain only the exercise catalogue and supporting definitions. Settings → Remove demo data removes sample personal records. JSON backup should be used before replacing existing records during an import.

## Main workflows to try

1. Programmes: confirm strength and stretching programmes share one list, and expanding two cards keeps both open. Scroll to the final **New programme** button, name a programme, then use its inline **Add exercise** immediately without reopening the editor; exercise and stretch choices must both save. Change targets/rest, reorder, duplicate and archive. Confirm **New programme** also appears in an empty list and scrolls with the content rather than floating. Existing multi-day programmes retain their day choice. Start a day whenever you choose; there is no schedule.
2. Workout: use the separate resistance and measurement fields, optional RPE and per-set variant selector. Select band dots directly; configure logging metrics and custom bands in the exercise DB. Skill hold offers another timing input after a valid number, up to eight, with an independent Total duration; hold a populated timing input to reveal its delete icon. Tick to lock a valid set and tap the highlighted tick to edit again. Collapse/reopen an exercise before ticking an untouched saved set: its original values and snapshots must not be rewritten. Exercise notes are under the labelled **Notes** footer action; editing notes must not restore deleted set slots or undo added slots.
3. Recovery: close and reopen the app after saving a set. The active workout should remain. Only Finish workout adds it to the maps, progress and activity calendar.
4. Workout order: use the six-dot exercise handles to reorder the active workout, then reopen it. Workout-local order and measurements must persist without changing the programme. Existing historical substitutions remain attached to their performed exercise; the logger has no substitution action.
5. Maps: tap a region, inspect its last session and set-equivalents, switch anatomy in Settings, and open an old day from the heatmap. Back returns to the app. Strength and stretching have separate histories.
6. Progress: choose an exercise and variation, tap a graph point for its performance/RPE/notes, and compare the RPE graph below. Missing RPE leaves a gap rather than shifting sessions. Three valid sessions establish the index baseline.
7. Habits: create a tracker, add fields and units, optionally set targets, and edit one daily total. The homepage counts each completed habit once and each finished training session separately.
8. History: search names or notes, correct sets and the workout date. Changes recompute the graphs and historical maps.
9. Body: add/edit measurements, import/remove photos and export photos separately.
10. Settings: try units, plate calculations, colours, haptics and reduced motion. Export JSON, change a record, and restore the backup. Export CSV to inspect raw sets, variations and bands.

## Timers and platform checks

Explicit completion starts positive configured rest only in an active draft; autosave, unlocking and finished-history edits do not restart it. One floating top-right countdown provides cancellation. The count-up stopwatch and manual start-rest action are no longer in the logger. Android notification and precise-alarm permissions control background alerts. Test an alert with the screen locked on your phone: emulator coverage does not establish behaviour under every manufacturer's battery restrictions.

## Interpretation

The body map shows **freshness derived from training recency**, not measured fatigue or recovery. Band widths are a relative assistance proxy within one exercise variation, not calibrated force. Overall skill indices include ordered variation levels and should be read alongside the actual performance.

Cloud sync and detailed habit metric charts remain intentionally deferred. Backups contain structured records and profile preferences; photographs export as a separate ZIP.

When reporting a bug, include the screen, steps, expected result, actual result, device/Android version and whether the records were demo or personal. Avoid posting personal backups or photos in public issues.
