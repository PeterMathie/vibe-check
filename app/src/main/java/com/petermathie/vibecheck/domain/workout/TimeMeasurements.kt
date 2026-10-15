package com.petermathie.vibecheck.domain.workout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

fun decodeTimeMeasurementInputs(value: String): List<String> = runCatching {
    val array = Json.parseToJsonElement(value.ifBlank { "[]" }) as JsonArray
    array.map { (it as JsonPrimitive).content }
}.getOrDefault(emptyList())

fun encodeTimeMeasurementInputs(values: List<String>): String =
    JsonArray(values.map(::JsonPrimitive)).toString()

fun decodeTimeMeasurementsMillis(value: String): List<Long> = runCatching {
    val array = Json.parseToJsonElement(value.ifBlank { "[]" }) as JsonArray
    array.map { (it as JsonPrimitive).longOrNull ?: error("Time measurement must be an integer") }
}.getOrDefault(emptyList())

fun encodeTimeMeasurementsMillis(values: List<Long>): String =
    JsonArray(values.map(::JsonPrimitive)).toString()

fun parseTimeMeasurementsMillis(value: String): List<Long>? =
    decodeTimeMeasurementInputs(value).map { input ->
        val seconds = quantitativeInput(input).value ?: return null
        (seconds * 1000).toLong()
    }
