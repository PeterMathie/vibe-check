package com.petermathie.vibecheck.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.petermathie.vibecheck.domain.model.HistoryDayDetail
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.domain.model.MuscleRole
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.freshnessColors
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun HomeDayActivityDialog(
    day: Long,
    detail: HistoryDayDetail?,
    bounds: Rect,
    presentationProgress: Float,
    onReady: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalVibePalette.current
    val date = formatDisplayDate(LocalDate.ofEpochDay(day))
    val matchingDetail = detail?.takeIf { it.epochDay == day }
    HomeGlassDialog(
        title = date,
        paneLabel = "Activity on $date",
        testTag = "Home day activity",
        closeLabel = "Close day activity",
        bounds = bounds,
        presentationProgress = presentationProgress,
        onReady = onReady,
        onDismiss = onDismiss,
    ) {
        when {
            matchingDetail == null -> Text(
                "Loading activity...",
                Modifier.padding(16.dp),
                color = palette.textSecondary,
            )
            matchingDetail.activities.isEmpty() -> Text(
                "No logged activity",
                Modifier.padding(16.dp),
                color = palette.textSecondary,
            )
            else -> LazyColumn(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                    .testTag("Home day activity list"),
            ) {
                itemsIndexed(matchingDetail.activities) { index, activity ->
                    if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                    HomeActivityRow(
                        title = activity.title,
                        detail = activity.detail,
                        description = listOf(activity.title, activity.detail, activity.notes)
                            .filter { it.isNotBlank() }.joinToString(". "),
                        testTag = "Home day activity row",
                    )
                }
            }
        }
    }
}

internal fun homeMuscleElapsedLabel(lastTrainedAt: Long?, asOfMillis: Long): String {
    if (lastTrainedAt == null) return "No recorded training"
    require(asOfMillis >= lastTrainedAt) { "Muscle activity cannot be later than its viewing time" }
    val elapsed = asOfMillis - lastTrainedAt
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    if (elapsed < minute) return "just now"
    fun rounded(unitMillis: Long) = (elapsed.toDouble() / unitMillis).roundToLong()
    val minutes = rounded(minute)
    val hours = rounded(hour)
    val days = rounded(day)
    val months = rounded(30 * day)
    val (count, unit) = when {
        minutes < 60 -> minutes to "minute"
        hours < 24 -> hours to "hour"
        days < 7 -> days to "day"
        days < 30 -> rounded(7 * day) to "week"
        months < 12 -> months to "month"
        else -> rounded(365 * day) to "year"
    }
    val quantity = if (count == 1L) {
        if (unit == "hour") "an hour" else "a $unit"
    } else "$count ${unit}s"
    return "about $quantity ago"
}

@Composable
internal fun HomeMuscleActivityDialog(
    muscleId: String,
    recency: MuscleRecency?,
    mode: TrainingMode,
    date: LocalDate,
    asOfMillis: Long?,
    bounds: Rect,
    presentationProgress: Float,
    onReady: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalVibePalette.current
    val name = muscleId.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
    val event = if (mode == TrainingMode.STRENGTH) "trained" else "stretched"
    val average = recency?.weeklyAverageRpe?.let { "%.1f".format(it) } ?: "Not recorded"
    val count = recency?.weeklyWorkingSets ?: 0
    val contributions = recency?.weeklyContributions.orEmpty()
    val viewingDate = formatDisplayDate(date)
    HomeGlassDialog(
        title = name,
        paneLabel = "$name activity on ${formatDisplayDate(date)}",
        testTag = "Home muscle activity",
        closeLabel = "Close muscle activity",
        bounds = bounds,
        presentationProgress = presentationProgress,
        onReady = onReady,
        onDismiss = onDismiss,
    ) {
        if (asOfMillis == null) {
            Text("Loading activity...", Modifier.padding(16.dp), color = palette.textSecondary)
        } else LazyColumn(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)
                .testTag("Home muscle activity list"),
        ) {
            item {
                val elapsed = homeMuscleElapsedLabel(recency?.lastTrainedAt, asOfMillis)
                val lastActivity = if (recency?.lastTrainedAt == null) {
                    elapsed.replaceFirstChar(Char::lowercase)
                } else "last $event $elapsed"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(
                        Modifier.size(12.dp)
                            .background(
                                palette.freshnessColors().forBand(recency?.band ?: MuscleRecencyBand.NEVER),
                                CircleShape,
                            )
                            .testTag("Home muscle freshness colour"),
                    )
                    Text(
                        "$viewingDate, $lastActivity",
                        color = palette.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HomeActivityRow("Past 7 days", "$count ${if (count == 1) "set" else "sets"}")
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                HomeActivityRow("Average RPE", average, description = "Average recorded RPE. $average")
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                if (contributions.isEmpty()) Text(
                    "No contributing exercises in the past 7 days",
                    Modifier.padding(vertical = 16.dp),
                    color = palette.textSecondary,
                )
            }
            items(contributions) { contribution ->
                val role = if (contribution.role == MuscleRole.PRIMARY) "Primary" else "Secondary"
                HomeActivityRow(
                    contribution.exerciseName,
                    "$role \u00b7 ${contribution.setCount} ${if (contribution.setCount == 1) "set" else "sets"}",
                    testTag = "Home muscle contribution",
                )
                HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun HomeActivityRow(
    title: String,
    detail: String,
    description: String = "$title. $detail",
    testTag: String = "Home activity row",
) {
    val palette = LocalVibePalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 8.dp)
            .testTag(testTag)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            detail,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeGlassDialog(
    title: String,
    paneLabel: String,
    testTag: String,
    closeLabel: String,
    bounds: Rect,
    presentationProgress: Float,
    onReady: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalVibePalette.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(24.dp)
    val opacity = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.97f else if (palette.isDark) 0.78f else 0.85f
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val parent = LocalView.current.parent
        DisposableEffect(parent) {
            check(parent is DialogWindowProvider) { "Home activity requires its dialog window" }
            parent.window.setWindowAnimations(0)
            parent.window.setDimAmount(0f)
            onDispose {}
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = presentationProgress }
                .onGloballyPositioned { onReady() }
                .testTag("$testTag overlay"),
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            Spacer(
                Modifier.fillMaxSize()
                    .background(palette.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .clearAndSetSemantics {},
            )
            Column(
                Modifier
                    .absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .clip(shape)
                    .background(palette.surfaceFloating.copy(alpha = opacity))
                    .border(1.dp, palette.border.copy(alpha = 0.5f), shape)
                    .pointerInput(Unit) { detectTapGestures {} }
                    .semantics { paneTitle = paneLabel }
                    .testTag(testTag),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, closeLabel, tint = palette.textPrimary)
                    }
                }
                content()
            }
        }
    }
}
