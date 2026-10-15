package com.petermathie.vibecheck.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.petermathie.vibecheck.domain.model.*
import com.petermathie.vibecheck.domain.tracker.HabitChoiceIntensity
import com.petermathie.vibecheck.domain.tracker.decodeHabitChoices
import org.json.JSONObject
import org.json.JSONArray
import java.math.BigDecimal

/** Import is an untrusted boundary: SQLite coercion is not domain validation. */
internal object ImportValidation {
    data class Column(val name: String, val type: String, val required: Boolean, val key: Boolean)
    private val booleanFields = setOf("isCustom", "isArchived", "isSeeded", "isDemo", "booleanValue", "targetMet")
    private val nonNegative = setOf("position", "ordinal", "progressionRank", "variationRankSnapshot", "widthCentimetresSnapshot", "targetSets", "targetRepsMin", "targetRepsMax", "targetHoldSeconds", "restSeconds", "reps", "leftReps", "rightReps", "legacyReps", "legacyLeftReps", "legacyRightReps", "holdMillis", "leftHoldMillis", "rightHoldMillis", "timeUnderTensionMillis", "weightKg", "addedWeightKg", "assistanceKg", "startedAt", "finishedAt", "loggedAt", "updatedAt", "recordedAt")
    private val intFields = setOf("position", "ordinal", "progressionRank", "variationRankSnapshot", "targetSets", "targetRepsMin", "targetRepsMax", "targetHoldSeconds", "restSeconds", "legacyReps", "legacyLeftReps", "legacyRightReps", "version")
    private val boundedQuantitative = setOf("reps", "leftReps", "rightReps", "weightKg", "addedWeightKg", "assistanceKg", "romValue")
    private val numericTrackers = setOf("NUMBER", "COUNT", "DURATION", "RATING")

    fun columns(sql: SupportSQLiteDatabase, table: String): List<Column> = buildList {
        sql.query("PRAGMA table_info(`$table`)").use { c ->
            while (c.moveToNext()) add(Column(c.getString(c.getColumnIndexOrThrow("name")), c.getString(c.getColumnIndexOrThrow("type")), c.getInt(c.getColumnIndexOrThrow("notnull")) != 0, c.getInt(c.getColumnIndexOrThrow("pk")) != 0))
        }
    }

    fun row(table: String, row: JSONObject, columns: List<Column>) {
        require(row.keys().asSequence().toSet() == columns.map { it.name }.toSet()) { "Invalid columns in $table" }
        columns.forEach { column ->
            val key = column.name
            val value = row.get(key)
            if (value == JSONObject.NULL) {
                require(!column.required && !column.key) { "$table.$key cannot be null" }
            } else when (column.type) {
                "TEXT" -> require(value is String && (!column.key || value.isNotBlank())) { "$table.$key must be text${if (column.key) " with a non-empty key" else ""}" }
                "INTEGER" -> {
                    val integer = if (key in booleanFields && value is Boolean) { if (value) 1L else 0L } else {
                        require(value is Number) { "$table.$key must be an integer" }
                        try { BigDecimal(value.toString()).longValueExact() } catch (_: ArithmeticException) { throw IllegalArgumentException("$table.$key must be a 64-bit integer") }
                    }
                    require(key !in booleanFields || integer in 0L..1L) { "$table.$key must be true/false or 0/1" }
                    require(key !in intFields || integer in 0L..Int.MAX_VALUE.toLong()) { "$table.$key is out of range" }
                    row.put(key, integer)
                }
                "REAL" -> require(value is Number && value.toDouble().isFinite()) { "$table.$key must be a finite number" }
            }
            if (!row.isNull(key) && key in nonNegative) require(row.getDouble(key) >= 0) { "$table.$key cannot be negative" }
            if (!row.isNull(key) && column.type == "REAL" && key in boundedQuantitative) {
                require(row.getDouble(key) <= 1_000_000_000.0) { "$table.$key is out of range" }
            }
        }
        fun oneOf(field: String, choices: Set<String>) {
            if (row.has(field) && !row.isNull(field)) require(row.getString(field) in choices) { "Invalid $table.$field: ${row.get(field)}" }
        }
        fun range(field: String, min: Double, max: Double) {
            if (row.has(field) && !row.isNull(field)) require(row.getDouble(field) in min..max) { "$table.$field must be $min–$max" }
        }
        oneOf("mode", TrainingMode.entries.map { it.name }.toSet())
        oneOf("tag", ExerciseTag.entries.map { it.name }.toSet())
        oneOf("trackingType", TrackingType.entries.map { it.name }.toSet() + if (table == "workout_exercises") setOf("") else emptySet())
        oneOf("role", MuscleRole.entries.map { it.name }.toSet())
        oneOf("status", WorkoutStatus.entries.map { it.name }.toSet())
        oneOf("setType", SetType.entries.map { it.name }.toSet())
        oneOf("result", SetResult.entries.map { it.name }.toSet())
        oneOf("valueType", numericTrackers + setOf("BOOLEAN", "TEXT", "CHOICE", "DATETIME"))
        oneOf("targetComparison", setOf("AT_LEAST", "AT_MOST", "EXACTLY", "RANGE"))
        oneOf("choiceIntensity", HabitChoiceIntensity.entries.map { it.name }.toSet())
        range("rpe", 0.0, 10.0)
        range("targetRpe", 0.0, 10.0)
        if (table == "workout_sets") {
            val values = runCatching { JSONArray(row.getString("timeMeasurementsMillis")) }
                .getOrElse { throw IllegalArgumentException("workout_sets.timeMeasurementsMillis must be an array") }
            var total = 0L
            repeat(values.length()) { index ->
                val value = values.opt(index)
                require(value is Number) { "workout_sets.timeMeasurementsMillis values must be integers" }
                val millis = try {
                    BigDecimal(value.toString()).longValueExact()
                } catch (_: ArithmeticException) {
                    throw IllegalArgumentException("workout_sets.timeMeasurementsMillis values must be integers")
                }
                require(millis >= 0) { "workout_sets.timeMeasurementsMillis values cannot be negative" }
                total = Math.addExact(total, millis)
            }
            if (values.length() > 0) {
                require(!row.isNull("timeUnderTensionMillis") && row.getLong("timeUnderTensionMillis") == total) {
                    "Repeated time measurements must equal total time"
                }
            }
        }
        if (table == "workouts") {
            require(row.getString("status") != "FINISHED" || !row.isNull("finishedAt")) { "Finished workout requires finishedAt" }
            require(row.isNull("finishedAt") || row.getLong("finishedAt") >= row.getLong("startedAt")) { "Workout finish precedes start" }
            require(row.isNull("bodyweightKg") || row.getDouble("bodyweightKg") > 0) { "Bodyweight must be positive" }
        }
        if (table == "programme_exercises" && !row.isNull("targetRepsMin") && !row.isNull("targetRepsMax")) require(row.getInt("targetRepsMax") >= row.getInt("targetRepsMin")) { "Rep target maximum is below minimum" }
        if (table == "tracker_fields") {
            require(row.isNull("targetComparison") == row.isNull("targetValue")) { "Target value and comparison must be supplied together" }
            require(row.isNull("targetValue") || row.getString("valueType") in numericTrackers) { "Targets require a numerical field" }
            require(row.optString("targetComparison") == "RANGE" || row.isNull("targetMaxValue")) { "Target maximum requires a range target" }
            require(row.optString("targetComparison") != "RANGE" || (!row.isNull("targetMaxValue") && row.getDouble("targetMaxValue") >= row.getDouble("targetValue"))) { "Range target maximum must be at least its minimum" }
            val options = row.optString("choiceOptions").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            require(row.getString("valueType") == "CHOICE" || options.isEmpty()) { "Choice options require a choice field" }
            require(row.getString("valueType") != "CHOICE" || options.size >= 2) { "Choice fields require at least two options" }
            require(options.distinct().size == options.size) { "Choice options must be unique" }
            val explicitOptions = decodeHabitChoices(row.optString("choiceOptionsJson"))
            require(row.getString("valueType") != "CHOICE" || explicitOptions.size == options.size) { "Choice option metadata is incomplete" }
            require(explicitOptions.map { it.id }.distinct().size == explicitOptions.size) { "Choice option IDs must be unique" }
            require(explicitOptions.map { it.label }.distinct().size == explicitOptions.size) { "Choice option labels must be unique" }
        }
        if (table == "tracker_daily_values") require(row.getLong("epochDay") in java.time.LocalDate.MIN.toEpochDay()..java.time.LocalDate.MAX.toEpochDay()) { "Habit date is outside the supported calendar range" }
        if (table == "workout_sets" && row.getString("result") == "FAILED") {
            val outcomes = listOf("reps", "leftReps", "rightReps", "holdMillis", "leftHoldMillis", "rightHoldMillis", "timeUnderTensionMillis", "romValue")
            require(outcomes.all { row.isNull(it) || row.getDouble(it) == 0.0 }) { "Failed/partial results must be zero" }
            require(row.getString("timeMeasurementsMillis") == "[]") { "Failed/partial repeated times must be empty" }
        }
    }

    fun existing(sql: SupportSQLiteDatabase, table: String, row: JSONObject, columns: List<Column>): JSONObject? {
        val keys = columns.filter { it.key }.map { it.name }
        return sql.query("SELECT * FROM `$table` WHERE ${keys.joinToString(" AND ") { "`$it`=?" }}", keys.map { row.get(it) }.toTypedArray()).use { c ->
            if (!c.moveToFirst()) null else JSONObject().also { result ->
                c.columnNames.forEachIndexed { i, name ->
                    result.put(name, when (c.getType(i)) { 0 -> JSONObject.NULL; 1 -> c.getLong(i); 2 -> c.getDouble(i); else -> c.getString(i) })
                }
            }
        }
    }

    fun protectCatalogue(sql: SupportSQLiteDatabase, table: String, row: JSONObject, existing: JSONObject?) {
        fun seededExercise(id: String) = sql.query("SELECT isCustom FROM exercises WHERE id=?", arrayOf(id)).use { it.moveToFirst() && it.getInt(0) == 0 }
        val protected = when (table) {
            "muscles", "bands" -> true
            "exercises" -> existing?.optInt("isCustom") == 0 || row.getInt("isCustom") == 0
            "exercise_variations" -> existing?.optInt("isSeeded") == 1 || row.getInt("isSeeded") == 1
            "exercise_aliases", "exercise_muscles" -> seededExercise(row.getString("exerciseId"))
            else -> false
        }
        if (protected) require(existing != null && row.keys().asSequence().all { key ->
            val a = row.get(key); val b = existing.get(key)
            if (a is Number && b is Number) BigDecimal(a.toString()).compareTo(BigDecimal(b.toString())) == 0 else a == b
        }) { "Cannot change seeded $table definitions; duplicate an exercise to customise it" }
        if (table == "exercises" && existing == null) require(!row.getString("id").startsWith("core:") && !row.getString("id").startsWith("free:")) { "Custom exercise cannot use a reserved catalogue ID" }
    }

    fun relationships(sql: SupportSQLiteDatabase) {
        fun reject(query: String, message: String) = sql.query(query).use { require(!it.moveToFirst()) { message } }
        reject("SELECT s.id FROM workout_sets s JOIN workout_exercises we ON we.id=s.workoutExerciseId LEFT JOIN exercise_variations v ON v.id=s.variationId WHERE s.variationId IS NOT NULL AND (v.id IS NULL OR v.exerciseId != we.actualExerciseId) LIMIT 1", "A set variation does not belong to the performed exercise")
        reject("SELECT wm.muscleId FROM workout_muscles wm LEFT JOIN muscles m ON m.id=wm.muscleId WHERE m.id IS NULL LIMIT 1", "Unknown muscle in workout snapshot")
        reject("SELECT id FROM workouts WHERE status='DRAFT' LIMIT 1 OFFSET 1", "Only one active draft is supported")
        reject("SELECT v.fieldId FROM tracker_daily_values v JOIN tracker_fields f ON f.id=v.fieldId WHERE (f.valueType IN ('NUMBER','COUNT','DURATION','RATING') AND (v.numericValue IS NULL OR v.booleanValue IS NOT NULL OR v.textValue IS NOT NULL)) OR (f.valueType='BOOLEAN' AND (v.booleanValue IS NULL OR v.numericValue IS NOT NULL OR v.textValue IS NOT NULL)) OR (f.valueType IN ('TEXT','CHOICE','DATETIME') AND (v.textValue IS NULL OR v.numericValue IS NOT NULL OR v.booleanValue IS NOT NULL)) OR (f.valueType IN ('COUNT','DURATION') AND v.numericValue<0) OR (f.valueType='COUNT' AND v.numericValue != CAST(v.numericValue AS INTEGER)) LIMIT 1", "Habit value does not match its field type")
        sql.query(
            """
            SELECT f.valueType, f.choiceOptions, v.textValue,v.choiceOptionId,v.choiceIntensity
            FROM tracker_daily_values v
            JOIN tracker_fields f ON f.id=v.fieldId
            WHERE f.valueType IN ('CHOICE','DATETIME')
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(0)
                val value = cursor.getString(2)
                if (type == "CHOICE") {
                    require(!cursor.isNull(3) && !cursor.isNull(4)) { "Habit choice value requires a stable option snapshot" }
                    require(cursor.getString(4) in HabitChoiceIntensity.entries.map { it.name }) { "Habit choice intensity is invalid" }
                } else {
                    require(runCatching { java.time.LocalDateTime.parse(value) }.isSuccess) { "Habit date/time value is invalid" }
                }
            }
        }
    }
}
