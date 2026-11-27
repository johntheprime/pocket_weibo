package com.pocketweibo.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Inline home search: filters as you type (no dialog / no "apply"), date chips under the field,
 * and a live result count. The text keeps its cursor because it is a TextFieldValue held by
 * MainScreen, so returning from a post continues where the user left off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSearchBar(
    state: HomeSearchState,
    resultCount: Int,
    focusRequester: FocusRequester,
    onChange: (HomeSearchState) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRangePicker by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = state.text,
            onValueChange = { onChange(state.copy(text = it)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GrayMiddle) },
            trailingIcon = {
                IconButton(onClick = {
                    if (state.text.text.isNotEmpty()) onChange(state.copy(text = androidx.compose.ui.text.input.TextFieldValue("")))
                    else onClose()
                }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(
                            if (state.text.text.isNotEmpty()) R.string.home_search_clear_text_cd else R.string.home_search_close_cd
                        )
                    )
                }
            },
            placeholder = { Text(stringResource(R.string.home_search_hint), color = GrayMiddle, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val presets = listOf(
                SearchDateRange.All to R.string.home_search_range_all,
                SearchDateRange.Today to R.string.home_search_range_today,
                SearchDateRange.Last7Days to R.string.home_search_range_7d,
                SearchDateRange.Last30Days to R.string.home_search_range_30d,
            )
            presets.forEach { (range, label) ->
                FilterChip(
                    selected = state.range == range,
                    onClick = { onChange(state.copy(range = range)) },
                    label = { Text(stringResource(label), fontSize = 13.sp) }
                )
            }
            val custom = state.range as? SearchDateRange.Custom
            FilterChip(
                selected = custom != null,
                onClick = { showRangePicker = true },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = {
                    Text(
                        if (custom == null) stringResource(R.string.home_search_range_custom)
                        else formatRange(custom.startDay, custom.endDay),
                        fontSize = 13.sp
                    )
                }
            )
        }
        if (state.isActive) {
            Text(
                text = stringResource(R.string.home_search_result_count, resultCount),
                fontSize = 12.sp,
                color = if (resultCount == 0) WeiboOrange else GrayMiddle,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }

    if (showRangePicker) {
        val current = state.range as? SearchDateRange.Custom
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = current?.startDay?.toUtcMillis(),
            initialSelectedEndDateMillis = current?.endDay?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val s = pickerState.selectedStartDateMillis?.toUtcDay()
                        val e = pickerState.selectedEndDateMillis?.toUtcDay() ?: s
                        if (s != null && e != null) onChange(state.copy(range = SearchDateRange.Custom(s, e)))
                        showRangePicker = false
                    }
                ) { Text(stringResource(R.string.home_search_range_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showRangePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.weight(1f),
                title = {
                    Text(
                        stringResource(R.string.home_search_range_custom),
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}

// DateRangePicker reports UTC-midnight millis for the selected calendar days.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toUtcDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun formatRange(a: LocalDate, b: LocalDate): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    return if (a == b) a.format(f) else "${a.format(f)} – ${b.format(f)}"
}
