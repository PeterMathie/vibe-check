package com.petermathie.vibecheck.domain.workout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

const val MAX_TUT_ENTRIES = 8

fun encodeTimingInputs(values: List<String>): String =
    JsonArray(values.map(::JsonPrimitive)).toString()

fun timingInputs(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val array = Json.parseToJsonElement(value) as? JsonArray
        ?: throw IllegalArgumentException("Timing inputs must be an array")
    require(array.size <= MAX_TUT_ENTRIES) { "At most eight timing entries are supported" }
    return array.map {
        require(it is JsonPrimitive && it.isString) { "Timing inputs must be text" }
        requireNotNull(it.contentOrNull)
    }
}

fun encodeTimingMillis(values: List<Long>): String {
    require(values.size <= MAX_TUT_ENTRIES && values.all { it in 0..1_000_000_000_000L }) {
        "Timing entries must contain at most eight non-negative durations"
    }
    return JsonArray(values.map(::JsonPrimitive)).toString()
}

fun timingMillis(value: String): List<Long> {
    val array = Json.parseToJsonElement(value) as? JsonArray
        ?: throw IllegalArgumentException("Timing entries must be an array")
    require(array.size <= MAX_TUT_ENTRIES) { "At most eight timing entries are supported" }
    return array.map {
        require(it is JsonPrimitive && !it.isString) { "Timing entries must be integer milliseconds" }
        requireNotNull(it.jsonPrimitive.longOrNull).also { millis ->
            require(millis in 0..1_000_000_000_000L) { "Timing duration is out of range" }
        }
    }
}
