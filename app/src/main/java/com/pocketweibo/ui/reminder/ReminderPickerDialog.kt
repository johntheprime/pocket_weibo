package com.pocketweibo.ui.reminder

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.text.format.DateFormat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.data.prefs.ReminderQuickPresetId
import com.pocketweibo.data.prefs.ReminderQuickPresetStats
import com.pocketweibo.reminder.ReminderRepeatRule
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

internal fun millisTomorrowAt(hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Next local 20:00: today if still in the future (with a short buffer), otherwise tomorrow 20:00. */
internal fun millisNextTodayOrTomorrowAt20(): Long {
    val minFuture = System.currentTimeMillis() + 5_000L
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 20)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    if (cal.timeInMillis <= minFuture) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}

internal fun showReminderDateTimePicker(context: Context, onChosen: (Long) -> Unit) {
    val activity = context.findActivity() ?: return
    val now = Calendar.getInstance()
    DatePickerDialog(
        activity,
        { _, year, month, dayOfMonth ->
            TimePickerDialog(
                activity,
                { _, hourOfDay, minute ->
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        set(Calendar.HOUR_OF_DAY, hourOfDay)
                        set(Calendar.MINUTE, minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onChosen(cal.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(activity)
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/**
 * Shared reminder schedule UI (quick presets, repeat, custom date/time) used from post detail and compose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerDialog(
    onDismissRequest: () -> Unit,
    onScheduleAt: (fireAtMillis: Long, repeatRule: String) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    titleText: String,
    additionalHint: String? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    val quickBar by ReminderQuickPresetStats.barStateFlow(appContext).collectAsState(
        initial = ReminderQuickPresetStats.defaultBarState()
    )
    var repeatRule by remember { mutableStateOf(ReminderRepeatRule.NONE) }
    var showRepeatOptions by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeatRule = ReminderRepeatRule.NONE
        showRepeatOptions = false
    }
    val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.canScheduleExactAlarms() == true
    val ignoringBattery = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    fun dismissThenSchedule(fireAt: Long, rule: String) {
        onDismissRequest()
        onScheduleAt(fireAt, rule)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(titleText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                val effectiveRepeat =
                    if (showRepeatOptions) repeatRule else ReminderRepeatRule.NONE
                if (!additionalHint.isNullOrBlank()) {
                    Text(
                        text = additionalHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = WeiboOrange,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.post_detail_remind_hint_system),
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayMiddle
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canExact) {
                    TextButton(
                        onClick = onOpenExactAlarmSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.post_detail_remind_open_exact_alarm),
                            color = WeiboOrange
                        )
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBattery) {
                    Text(
                        text = stringResource(R.string.post_detail_remind_battery_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    TextButton(
                        onClick = onOpenAppDetailsSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.post_detail_remind_open_app_settings),
                            color = WeiboOrange
                        )
                    }
                }
                TextButton(
                    onClick = { showRepeatOptions = !showRepeatOptions },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (showRepeatOptions) {
                                R.string.post_detail_remind_repeat_hide
                            } else {
                                R.string.post_detail_remind_repeat_show
                            }
                        ),
                        color = WeiboOrange
                    )
                }
                if (showRepeatOptions) {
                    Text(
                        text = stringResource(R.string.post_detail_remind_repeat_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = GrayDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = repeatRule == ReminderRepeatRule.NONE,
                            onClick = { repeatRule = ReminderRepeatRule.NONE },
                            label = { Text(stringResource(R.string.reminder_repeat_once)) }
                        )
                        FilterChip(
                            selected = repeatRule == ReminderRepeatRule.DAILY,
                            onClick = { repeatRule = ReminderRepeatRule.DAILY },
                            label = { Text(stringResource(R.string.reminder_repeat_daily)) }
                        )
                        FilterChip(
                            selected = repeatRule == ReminderRepeatRule.WORKDAYS,
                            onClick = { repeatRule = ReminderRepeatRule.WORKDAYS },
                            label = { Text(stringResource(R.string.reminder_repeat_workdays)) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = repeatRule == ReminderRepeatRule.WEEKLY,
                            onClick = { repeatRule = ReminderRepeatRule.WEEKLY },
                            label = { Text(stringResource(R.string.reminder_repeat_weekly)) }
                        )
                        FilterChip(
                            selected = repeatRule == ReminderRepeatRule.MONTHLY,
                            onClick = { repeatRule = ReminderRepeatRule.MONTHLY },
                            label = { Text(stringResource(R.string.reminder_repeat_monthly)) }
                        )
                    }
                }
                val presetScroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .horizontalScroll(presetScroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fun labelForPreset(id: ReminderQuickPresetId): Int = when (id) {
                        ReminderQuickPresetId.M15 -> R.string.post_detail_remind_chip_15m
                        ReminderQuickPresetId.M30 -> R.string.post_detail_remind_chip_30m
                        ReminderQuickPresetId.H1 -> R.string.post_detail_remind_chip_1h
                        ReminderQuickPresetId.H3 -> R.string.post_detail_remind_chip_3h
                        ReminderQuickPresetId.H6 -> R.string.post_detail_remind_chip_6h
                        ReminderQuickPresetId.H10 -> R.string.post_detail_remind_chip_10h
                    }
                    fun schedulePreset(id: ReminderQuickPresetId) {
                        scope.launch(Dispatchers.IO) {
                            ReminderQuickPresetStats.recordUse(appContext, id)
                        }
                        dismissThenSchedule(
                            ReminderQuickPresetStats.millisOffsetMillis(id),
                            effectiveRepeat
                        )
                    }
                    quickBar.topThree.forEach { id ->
                        OutlinedButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = { schedulePreset(id) }
                        ) {
                            Text(
                                stringResource(labelForPreset(id)),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                    OutlinedButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            dismissThenSchedule(
                                millisTomorrowAt(9, 0),
                                effectiveRepeat
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.post_detail_remind_chip_tomorrow_9),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    OutlinedButton(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        onClick = {
                            dismissThenSchedule(
                                millisNextTodayOrTomorrowAt20(),
                                effectiveRepeat
                            )
                        }
                    ) {
                        Text(
                            stringResource(R.string.post_detail_remind_chip_today_20),
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    quickBar.remainder.forEach { id ->
                        OutlinedButton(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            onClick = { schedulePreset(id) }
                        ) {
                            Text(
                                stringResource(labelForPreset(id)),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        onDismissRequest()
                        showReminderDateTimePicker(context) { ms ->
                            onScheduleAt(ms, effectiveRepeat)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(stringResource(R.string.post_detail_remind_pick_datetime))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.close_cd))
            }
        }
    )
}
