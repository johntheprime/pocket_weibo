package com.pocketweibo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatExactTime
import com.pocketweibo.ui.util.formatRelativeTime

/**
 * Relative timestamp that reveals the exact date-time when tapped.
 * Tapping again restores the relative label. Already-absolute labels are not toggleable.
 */
@Composable
fun RelativeTimeText(
    timestamp: Long,
    preset: RelativeTimePreset,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val resources = LocalContext.current.resources
    var showExact by remember(timestamp) { mutableStateOf(false) }
    val relativeText = resources.formatRelativeTime(timestamp, preset)
    val exactText = resources.formatExactTime(timestamp)
    val canToggle = relativeText != exactText
    Text(
        text = if (showExact) exactText else relativeText,
        modifier = if (canToggle) modifier.clickable { showExact = !showExact } else modifier,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
    )
}
