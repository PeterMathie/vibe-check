package com.petermathie.vibecheck.data

import android.content.SharedPreferences
import com.petermathie.vibecheck.ui.theme.VibePalettes
import org.json.JSONObject

/** Only durable profile and presentation choices belong in a backup, never running timers. */
object BackupPreferences {
    private val booleans = setOf(
        "lb",
        "female",
        "autoRest",
        "haptic",
        "reducedMotion",
        "showExerciseVariations",
    )
    private val colours = setOf("accent", "background", "surface")

    fun attach(json: String, preferences: SharedPreferences): String {
        val values = JSONObject()
        preferences.all.forEach { (key, value) ->
            if (key in booleans || key == "palette" || key == "themeMode") values.put(key, value)
        }
        return JSONObject(json).put("preferences", values).toString(2)
    }

    fun validate(json: String): JSONObject? {
        val values = JSONObject(json).optJSONObject("preferences") ?: return null
        values.keys().forEach { key ->
            val value = values.get(key)
            require(when (key) {
                in booleans -> value is Boolean
                in colours -> value is Number && value.toDouble() == value.toInt().toDouble()
                "palette" -> value is String
                "themeMode" -> value is String
                else -> false
            }) { "Invalid preference: $key" }
        }
        if (values.has("palette")) values.put("palette", VibePalettes.normalizeId(values.getString("palette")))
        if (values.has("themeMode")) {
            values.put("themeMode", com.petermathie.vibecheck.ui.theme.VibeThemeMode.fromPreference(values.getString("themeMode")).id)
        }
        colours.forEach(values::remove)
        return values
    }

    fun restore(values: JSONObject?, preferences: SharedPreferences) {
        if (values == null) return
        preferences.edit().remove("accent").remove("background").remove("surface").apply {
            values.keys().forEach { key ->
                when (key) {
                    in booleans -> putBoolean(key, values.getBoolean(key))
                    "palette" -> putString(key, VibePalettes.normalizeId(values.getString(key)))
                    "themeMode" -> putString(
                        key,
                        com.petermathie.vibecheck.ui.theme.VibeThemeMode.fromPreference(values.getString(key)).id,
                    )
                }
            }
        }.apply()
    }
}
