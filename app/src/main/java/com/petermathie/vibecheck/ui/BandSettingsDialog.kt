package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.local.isSeededBandId
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.domain.workout.quantitativeInput
import com.petermathie.vibecheck.domain.programme.MAX_RESISTANCE_BANDS

@Composable
internal fun BandSettingsDialog(
    bands: List<BandEntity>,
    onDismiss: () -> Unit,
    onSave: (BandEntity, () -> Unit, () -> Unit) -> Unit,
) {
    var editing by remember { mutableStateOf<BandEntity?>(null) }
    val selected = editing
    if (selected == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Resistance bands") },
            text = {
                LazyColumn {
                    items(bands, key = { it.id }) { band ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).background(Color(band.colourArgb), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(band.name, Modifier.weight(1f))
                            if (!isSeededBandId(band.id)) TextButton(onClick = { editing = band }) { Text("Edit") }
                        }
                    }
                    item { Text("${bands.size}/$MAX_RESISTANCE_BANDS bands configured", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = bands.size < MAX_RESISTANCE_BANDS,
                    onClick = { editing = BandEntity(newId(), "", 0.0, 0xFF5085D6L) },
                ) { Text("Add band") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        )
    } else {
        var name by rememberSaveable(selected.id) { mutableStateOf(selected.name) }
        var width by rememberSaveable(selected.id) {
            mutableStateOf(selected.widthCentimetres.takeIf { it > 0 }?.let(::formatQuantitative).orEmpty())
        }
        var colour by rememberSaveable(selected.id) { mutableStateOf(selected.colourArgb.toString(16).padStart(8, '0')) }
        var saving by remember { mutableStateOf(false) }
        var saveFailed by remember { mutableStateOf(false) }
        val parsedWidth = quantitativeInput(width)
        val hex = colour.removePrefix("#")
        val parsedColour = hex.takeIf { it.length == 6 || it.length == 8 }?.toLongOrNull(16)
            ?.let { if (hex.length == 6) it or 0xFF000000L else it }
        val valid = name.isNotBlank() && parsedWidth.isValid && (parsedWidth.value ?: 0.0) > 0 && parsedColour != null
        AlertDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = { Text(if (selected.name.isBlank()) "Add resistance band" else "Edit resistance band") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.semantics { contentDescription = "Band name" },
                    )
                    OutlinedTextField(
                        width, { width = it }, label = { Text("Width (cm)") }, singleLine = true,
                        isError = width.isNotBlank() && (!parsedWidth.isValid || (parsedWidth.value ?: 0.0) <= 0),
                        supportingText = { Text("Used for band strength and stable ordering") },
                        modifier = Modifier.semantics { contentDescription = "Band width in centimetres" },
                    )
                    OutlinedTextField(
                        colour, { colour = it }, label = { Text("Colour hex") }, singleLine = true,
                        isError = parsedColour == null,
                        supportingText = { Text("RGB or ARGB, for example FF5085D6") },
                        modifier = Modifier.semantics { contentDescription = "Band colour" },
                    )
                    if (parsedColour != null) Box(
                        Modifier.size(24.dp).background(Color(parsedColour), CircleShape)
                            .semantics { contentDescription = "Band colour preview" },
                    )
                    if (saveFailed) Text("Could not save the band. Please try again.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = valid && !saving, onClick = {
                    saving = true
                    saveFailed = false
                    onSave(selected.copy(
                        name = name.trim(),
                        widthCentimetres = requireNotNull(parsedWidth.value),
                        colourArgb = requireNotNull(parsedColour),
                    ), { editing = null }, { saving = false; saveFailed = true })
                }) { Text("Save band") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
