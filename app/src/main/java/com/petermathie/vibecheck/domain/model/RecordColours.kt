package com.petermathie.vibecheck.domain.model

import kotlin.random.Random

val recordColours = listOf(
    "Light pink" to 0xFFF8BBD0L, "Pale yellow" to 0xFFFFF59DL, "Light lime" to 0xFFDCE775L,
    "Light mint" to 0xFFB2DFDBL, "Light blue" to 0xFF90CAF9L, "Lavender" to 0xFFD1C4E9L,
    "Pink" to 0xFFEC407AL, "Yellow" to 0xFFFFEE58L, "Lime" to 0xFFCDDC39L,
    "Mint" to 0xFF80CBC4L, "Sky blue" to 0xFF81D4FAL, "Lilac" to 0xFFB39DDBL,
    "Red" to 0xFFEF5350L, "Orange" to 0xFFFFA726L, "Green" to 0xFF66BB6AL,
    "Teal" to 0xFF26A69AL, "Blue" to 0xFF42A5F5L, "Purple" to 0xFF7E57C2L,
    "Crimson" to 0xFFE53935L, "Amber" to 0xFFFFB300L, "Leaf green" to 0xFF43A047L,
    "Turquoise" to 0xFF00ACC1L, "Cobalt" to 0xFF1E88E5L, "Violet" to 0xFF8E24AAL,
    "Dark red" to 0xFFC62828L, "Burnt orange" to 0xFFEF6C00L, "Forest green" to 0xFF2E7D32L,
    "Dark teal" to 0xFF00897BL, "Deep blue" to 0xFF1565C0L, "Dark purple" to 0xFF6A1B9AL,
    "Burgundy" to 0xFF880E4FL, "Brown" to 0xFF6D4C41L, "Olive" to 0xFF827717L,
    "Pine" to 0xFF004D40L, "Navy" to 0xFF0D47A1L, "Plum" to 0xFF4A148CL,
)

// Stable legacy IDs get the same initial colour on upgrade and repeated old-format imports.
fun programmeColour(id: String): Long = recordColours.random(Random(id.hashCode())).second
