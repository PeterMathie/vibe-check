package com.petermathie.vibecheck.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun RpeInfoButton(contentDescription: String = "Explain the RPE scale") {
    var visible by remember { mutableStateOf(false) }
    IconButton(onClick = { visible = true }) {
        Icon(Icons.Outlined.Info, contentDescription = contentDescription)
    }
    if (visible) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text("Rate of perceived exertion") },
            text = {
                Text(
                    "RPE uses a 0–10 scale: 0 is rest, 1–3 is easy, 4–6 is moderate, " +
                        "7 is hard, 8 leaves about 2 reps in reserve, 9 leaves about 1, " +
                        "and 10 is maximal effort.",
                )
            },
            confirmButton = {
                TextButton(onClick = { visible = false }) { Text("Got it") }
            },
        )
    }
}
