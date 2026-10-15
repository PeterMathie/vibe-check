package com.petermathie.vibecheck.data

import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.domain.tracker.decodeHabitChoices
import com.petermathie.vibecheck.domain.tracker.encodeHabitChoices
import com.petermathie.vibecheck.domain.tracker.legacyHabitChoices
import com.petermathie.vibecheck.domain.tracker.legacyHabitChoiceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, structured, portable records. Photos are deliberately separate. */
object DataTransfer {
    val tables=listOf("muscles","exercises","exercise_aliases","exercise_muscles","exercise_variations","bands","programmes","programme_days","programme_exercises","workouts","workout_exercises","workout_muscles","workout_entry_drafts","workout_sets","workout_set_bands","trackers","tracker_fields","tracker_daily_values","tracker_day_outcomes","body_measurements","seed_metadata")
    suspend fun export(db:VibeDatabase):String=withContext(Dispatchers.IO) {
        val root=JSONObject().put("format","vibe-trainer").put("version",1)
        val data=JSONObject()
        val sql=db.openHelper.readableDatabase
        sql.beginTransaction()
        try {
            tables.forEach { table ->
                val rows=JSONArray()
                sql.query("SELECT * FROM `$table`").use { cursor ->
                    while(cursor.moveToNext()) {
                        val row=JSONObject()
                        cursor.columnNames.forEachIndexed { i,name -> row.put(name,when(cursor.getType(i)) { 0->JSONObject.NULL;1->cursor.getLong(i);2->cursor.getDouble(i);else->cursor.getString(i) }) }
                        rows.put(row)
                    }
                };data.put(table,rows)
            }
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        root.put("tables",data).toString(2)
    }
    /** Merge by stable ID; validate everything and roll back on any invalid record. */
    suspend fun import(db:VibeDatabase,text:String)=withContext(Dispatchers.IO) {
        require(text.length<50_000_000){"Import exceeds 50 MB"}
        val root=JSONObject(text)
        require(root.optString("format")=="vibe-trainer" && (root.opt("version") as? Number)?.toDouble()==1.0){"Unsupported backup format"}
        require(root.keys().asSequence().all { it in setOf("format","version","tables","preferences") }) { "Unknown top-level import field" }
        if(root.has("preferences")) {
            require(root.get("preferences") is JSONObject) { "preferences must be an object" }
            BackupPreferences.validate(text)
        }
        val data=root.getJSONObject("tables")
        require(data.keys().asSequence().all { it in tables }){"Unknown table in import"}
        val sql=db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            tables.forEach { table ->
                if(!data.has(table)) return@forEach
                require(data.get(table) is JSONArray) { "$table must be an array" }
                val rows=data.getJSONArray(table)
                val schema=ImportValidation.columns(sql,table)
                val columns=schema.map { it.name };val keys=schema.filter { it.key }.map { it.name }
                val seen=mutableSetOf<List<Any>>()
                repeat(rows.length()) { i ->
                    val row=rows.getJSONObject(i)
                    if (
                        table == "exercises" &&
                        row.optString("id") == "core:front-split" &&
                        row.optString("source") == "vibe-trainer" &&
                        row.optInt("isCustom") == 0 &&
                        row.optString("trackingType") == "ROM_MEASUREMENT" &&
                        row.optString("inputConfig").isBlank()
                    ) {
                        row.put("trackingType", "HOLD")
                        row.put("inputConfig", FRONT_SPLIT_HOLD_INPUT_CONFIG)
                    }
                    if (
                        table == "workout_exercises" &&
                        row.optString("actualExerciseId") == "core:front-split" &&
                        row.optString("trackingType") == "ROM_MEASUREMENT" &&
                        row.optString("inputConfig").isBlank()
                    ) {
                        val draft = sql.query(
                            "SELECT status FROM workouts WHERE id=?",
                            arrayOf(row.getString("workoutId")),
                        ).use { it.moveToFirst() && it.getString(0) == "DRAFT" }
                        if (draft) {
                            row.put("trackingType", "HOLD")
                            row.put("inputConfig", FRONT_SPLIT_HOLD_INPUT_CONFIG)
                        }
                    }
                    if (table in setOf("programme_exercises", "workout_exercises") && row.has("notes")) {
                        row.put("notes", sanitizeExerciseNotes(row.optString("notes")))
                    }
                    if (table == "tracker_fields" && !row.has("choiceOptionsJson")) {
                        val options = if (row.optString("valueType") == "CHOICE") {
                            legacyHabitChoices(
                                row.getString("id"),
                                row.optString("choiceOptions"),
                                row.optInt("choiceLightThrough", -1),
                                row.optInt("choiceDarkFrom", -1),
                            )
                        } else {
                            emptyList()
                        }
                        row.put("choiceOptionsJson", encodeHabitChoices(options))
                    }
                    if (
                        table == "tracker_daily_values" &&
                        (!row.has("choiceOptionId") || row.isNull("choiceOptionId") ||
                            !row.has("choiceIntensity") || row.isNull("choiceIntensity"))
                    ) {
                        if (!row.has("choiceOptionId")) row.put("choiceOptionId", JSONObject.NULL)
                        if (!row.has("choiceIntensity")) row.put("choiceIntensity", JSONObject.NULL)
                        val text = row.optString("textValue").takeIf(String::isNotBlank)
                        if (text != null) {
                            sql.query(
                                "SELECT choiceOptionsJson FROM tracker_fields WHERE id=? AND UPPER(valueType)='CHOICE'",
                                arrayOf(row.getString("fieldId")),
                            ).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val option = legacyHabitChoiceSnapshot(
                                        row.getString("fieldId"),
                                        text,
                                        decodeHabitChoices(cursor.getString(0)),
                                    )
                                    row.put("choiceOptionId", option.id)
                                    row.put("choiceIntensity", option.intensity.name)
                                }
                            }
                        }
                    }
                    if (table == "workout_exercises" && !row.has("targetSets")) {
                        val assignment = sql.query(
                            """
                            SELECT pe.targetSets,pe.targetRepsMin,pe.targetRepsMax,pe.targetHoldSeconds,pe.targetRpe
                            FROM workouts w
                            JOIN programme_exercises pe ON pe.programmeDayId=w.programmeDayId
                            WHERE w.id=? AND pe.exerciseId=? AND pe.position=?
                            LIMIT 1
                            """.trimIndent(),
                            arrayOf(
                                row.getString("workoutId"),
                                row.getString("plannedExerciseId"),
                                row.getInt("position"),
                            ),
                        )
                        assignment.use {
                            val found = it.moveToFirst()
                            row.put("targetSets", if (found && !it.isNull(0)) it.getInt(0) else JSONObject.NULL)
                            row.put("targetRepsMin", if (found && !it.isNull(1)) it.getInt(1) else JSONObject.NULL)
                            row.put("targetRepsMax", if (found && !it.isNull(2)) it.getInt(2) else JSONObject.NULL)
                            row.put("targetHoldSeconds", if (found && !it.isNull(3)) it.getInt(3) else JSONObject.NULL)
                            row.put("targetRpe", if (found && !it.isNull(4)) it.getDouble(4) else JSONObject.NULL)
                        }
                    }
                    if (table == "workout_sets" && !row.has("variationRankSnapshot")) {
                        val rank = row.optString("variationId").takeIf(String::isNotBlank)?.let { variationId ->
                            sql.query("SELECT progressionRank FROM exercise_variations WHERE id=?", arrayOf(variationId)).use { if (it.moveToFirst()) it.getInt(0) else null }
                        }
                        row.put("variationRankSnapshot", rank ?: JSONObject.NULL)
                    }
                    if (table == "workout_sets" && !row.has("timeMeasurementsMillis")) {
                        row.put("timeMeasurementsMillis", "[]")
                    }
                    if (table == "workout_entry_drafts" && !row.has("timeMeasurements")) {
                        row.put("timeMeasurements", "[]")
                    }
                    if (
                        table == "exercises" &&
                        row.optString("id") == "core:dip" &&
                        row.optInt("isCustom") == 0 &&
                        row.optString("inputConfig") in setOf("", "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false")
                    ) {
                        sql.query("SELECT inputConfig FROM exercises WHERE id='core:dip'").use {
                            if (it.moveToFirst()) row.put("inputConfig", it.getString(0))
                        }
                    }
                    if (
                        table == "exercise_variations" &&
                        row.optInt("isSeeded") == 1 &&
                        row.optString("id") in setOf("handstand-free", "pull-up-bodyweight", "pull-up-weighted")
                    ) {
                        val legacyConfig = row.optString("inputConfig")
                        val recognisedLegacy = when (row.getString("id")) {
                            "handstand-free" -> legacyConfig ==
                                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=true;reps=false;bodyweight=false;addedWeight=false"
                            "pull-up-bodyweight" -> legacyConfig ==
                                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false"
                            else -> legacyConfig ==
                                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=true"
                        }
                        if (recognisedLegacy) {
                            sql.query(
                                "SELECT inputConfig FROM exercise_variations WHERE id=?",
                                arrayOf(row.getString("id")),
                            ).use {
                                if (it.moveToFirst()) row.put("inputConfig", it.getString(0))
                            }
                        }
                    }
                    if (table == "workout_sets" && !row.has("legacyReps")) {
                        fun legacyCount(name: String): Any {
                            if (row.isNull(name)) return JSONObject.NULL
                            val value = row.getDouble(name)
                            return if (value % 1.0 == 0.0 && value <= Int.MAX_VALUE) value.toInt() else JSONObject.NULL
                        }
                        row.put("legacyReps", legacyCount("reps"))
                        row.put("legacyLeftReps", legacyCount("leftReps"))
                        row.put("legacyRightReps", legacyCount("rightReps"))
                    }
                    if (table == "workout_sets" && !row.has("variationNameSnapshot")) {
                        val variationId = row.optString("variationId").takeIf(String::isNotBlank)
                        val variation = variationId?.let {
                            sql.query("SELECT name,trackingType,inputConfig FROM exercise_variations WHERE id=?", arrayOf(it))
                        }
                        variation?.use {
                            if (it.moveToFirst()) {
                                row.put("variationNameSnapshot", it.getString(0))
                                row.put("variationTrackingTypeSnapshot", it.getString(1))
                                row.put("variationInputConfigSnapshot", it.getString(2))
                            }
                        }
                        if (!row.has("variationNameSnapshot")) {
                            row.put("variationNameSnapshot", "")
                            row.put("variationTrackingTypeSnapshot", "")
                            row.put("variationInputConfigSnapshot", "")
                        }
                    }
                    if (table == "exercise_variations" && !row.has("trackingType")) {
                        sql.query(
                            """
                            SELECT trackingType,inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds
                            FROM exercises WHERE id=?
                            """.trimIndent(),
                            arrayOf(row.getString("exerciseId")),
                        ).use {
                            require(it.moveToFirst()) { "Unknown exercise for variation" }
                            row.put("trackingType", it.getString(0))
                            row.put("inputConfig", it.getString(1))
                            row.put("targetSets", if (it.isNull(2)) JSONObject.NULL else it.getInt(2))
                            row.put("targetRepsMin", if (it.isNull(3)) JSONObject.NULL else it.getInt(3))
                            row.put("targetRepsMax", if (it.isNull(4)) JSONObject.NULL else it.getInt(4))
                            row.put("targetRpe", if (it.isNull(5)) JSONObject.NULL else it.getDouble(5))
                            row.put("restSeconds", it.getInt(6))
                        }
                    }
                    if (table == "workout_set_bands" && !row.has("nameSnapshot")) {
                        sql.query("SELECT name,widthCentimetres FROM bands WHERE id=?", arrayOf(row.getString("bandId"))).use {
                            if (it.moveToFirst()) {
                                row.put("nameSnapshot", it.getString(0))
                                row.put("widthCentimetresSnapshot", it.getDouble(1))
                            } else {
                                row.put("nameSnapshot", "")
                                row.put("widthCentimetresSnapshot", 0.0)
                            }
                        }
                    }
                    ImportValidation.row(table,row,schema)
                    require(seen.add(keys.map { row.get(it) })) { "Duplicate key in $table record $i" }
                    // Application seeding state is not portable personal data.
                    if(table=="seed_metadata") return@repeat
                    val existing=ImportValidation.existing(sql,table,row,schema)
                    ImportValidation.protectCatalogue(sql,table,row,existing)
                    val args=columns.map { if(row.isNull(it))null else row.get(it) }.toTypedArray()
                    val updateColumns=columns.filter { it !in keys }
                    if(existing==null) sql.execSQL("INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})",args)
                    else if(updateColumns.isNotEmpty()) sql.execSQL("UPDATE `$table` SET ${updateColumns.joinToString { "`$it`=?" }} WHERE ${keys.joinToString(" AND ") { "`$it`=?" }}",(updateColumns+keys).map { if(row.isNull(it))null else row.get(it) }.toTypedArray())
                }
            }
            sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()){ "Import contains broken references" } }
            ImportValidation.relationships(sql)
            sql.execSQL(
                """
                INSERT OR IGNORE INTO tracker_day_outcomes
                SELECT days.trackerId, days.epochDay,
                  CASE WHEN EXISTS (SELECT 1 FROM tracker_fields f WHERE f.trackerId=days.trackerId AND f.isArchived=0 AND f.targetComparison IS NOT NULL)
                  THEN NOT EXISTS (
                    SELECT 1 FROM tracker_fields f LEFT JOIN tracker_daily_values v ON v.fieldId=f.id AND v.epochDay=days.epochDay
                    WHERE f.trackerId=days.trackerId AND f.isArchived=0 AND f.targetComparison IS NOT NULL AND (
                      v.numericValue IS NULL OR
                      CASE f.targetComparison WHEN 'AT_LEAST' THEN v.numericValue < f.targetValue
                        WHEN 'AT_MOST' THEN v.numericValue > f.targetValue
                        WHEN 'EXACTLY' THEN v.numericValue != f.targetValue
                        WHEN 'RANGE' THEN v.numericValue < f.targetValue OR v.numericValue > f.targetMaxValue
                        ELSE 1 END
                    )
                  ) ELSE 1 END
                FROM (SELECT DISTINCT f.trackerId,v.epochDay FROM tracker_daily_values v JOIN tracker_fields f ON f.id=v.fieldId) days
                """.trimIndent(),
            )
            sql.setTransactionSuccessful()
        } finally { sql.endTransaction() }
        db.invalidationTracker.refreshVersionsAsync()
    }
    suspend fun csv(db:VibeDatabase):String=withContext(Dispatchers.IO) {
        fun quote(s:String)="\""+s.replace("\"","\"\"")+"\""
        val out=StringBuilder()
        db.openHelper.readableDatabase.query("SELECT w.name AS workout,w.finishedAt,w.notes AS workoutNotes,COALESCE(NULLIF(we.exerciseName,''),e.canonicalName) AS exercise,we.notes AS exerciseNotes,v.name AS variation,(SELECT GROUP_CONCAT(COALESCE(NULLIF(sb.nameSnapshot,''),b.name), ' + ') FROM workout_set_bands sb JOIN bands b ON b.id=sb.bandId WHERE sb.setId=s.id) AS bands,(SELECT SUM(CASE WHEN sb.widthCentimetresSnapshot>0 THEN sb.widthCentimetresSnapshot ELSE b.widthCentimetres END) FROM workout_set_bands sb JOIN bands b ON b.id=sb.bandId WHERE sb.setId=s.id) AS bandWidthCm,s.* FROM workout_sets s JOIN workout_exercises we ON we.id=s.workoutExerciseId JOIN exercises e ON e.id=we.actualExerciseId JOIN workouts w ON w.id=we.workoutId LEFT JOIN exercise_variations v ON v.id=s.variationId WHERE w.status='FINISHED' ORDER BY w.finishedAt,s.ordinal").use { c ->
            out.appendLine(c.columnNames.joinToString(",",transform=::quote))
            while(c.moveToNext())out.appendLine(c.columnNames.indices.joinToString(","){quote(if(c.isNull(it))"" else c.getString(it))})
        };out.toString()
    }
}
