package com.pocketweibo.ui.screens.me

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.data.local.dao.IdentityScheduleWithIdentityName
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.local.entity.IdentityScheduleEntity
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.launch

@Composable
fun IdentityScheduleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    val schedules by app.repository.observeIdentitySchedules().collectAsState(initial = emptyList())
    val allIdentities by app.repository.allIdentities.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<IdentityScheduleWithIdentityName?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = stringResource(R.string.schedule_title),
            leftIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.schedule_empty),
                    fontSize = 15.sp,
                    color = GrayMiddle
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.schedule_empty_hint),
                    fontSize = 13.sp,
                    color = GrayLight
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        onToggle = { enabled ->
                            scope.launch {
                                app.repository.setIdentityScheduleEnabled(schedule.id, enabled)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                app.repository.deleteIdentitySchedule(schedule.id)
                            }
                        },
                        onEdit = {
                            editingSchedule = schedule
                        }
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp),
            containerColor = WeiboOrange
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.schedule_add_cd),
                tint = Color.White
            )
        }
    }

    if (showAddDialog) {
        EditScheduleDialog(
            identities = allIdentities,
            schedule = null,
            onDismiss = { showAddDialog = false },
            onSave = { identityId, hour, minute, daysOfWeek ->
                scope.launch {
                    app.repository.insertIdentitySchedule(
                        IdentityScheduleEntity(
                            identityId = identityId,
                            hour = hour,
                            minute = minute,
                            daysOfWeek = daysOfWeek
                        )
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.schedule_added_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showAddDialog = false
            }
        )
    }

    editingSchedule?.let { schedule ->
        EditScheduleDialog(
            identities = allIdentities,
            schedule = schedule,
            onDismiss = { editingSchedule = null },
            onSave = { identityId, hour, minute, daysOfWeek ->
                scope.launch {
                    app.repository.updateIdentitySchedule(
                        IdentityScheduleEntity(
                            id = schedule.id,
                            identityId = identityId,
                            hour = hour,
                            minute = minute,
                            daysOfWeek = daysOfWeek,
                            enabled = schedule.enabled
                        )
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.schedule_updated_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                editingSchedule = null
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: IdentityScheduleWithIdentityName,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.identityName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatScheduleTime(context, schedule.hour, schedule.minute, schedule.daysOfWeek),
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.delete),
                    color = Color.Red.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatScheduleTime(context: android.content.Context, hour: Int, minute: Int, daysOfWeek: String): String {
    val timeStr = String.format("%02d:%02d", hour, minute)
    val dayStr = when {
        daysOfWeek.isBlank() -> context.getString(R.string.schedule_label_every_day)
        daysOfWeek == "1,2,3,4,5" -> context.getString(R.string.schedule_label_weekdays)
        daysOfWeek == "6,7" -> context.getString(R.string.schedule_label_weekends)
        else -> {
            val names = daysOfWeek.split(",").mapNotNull { d ->
                when (d.trim().toIntOrNull()) {
                    1 -> context.getString(R.string.schedule_day_mon)
                    2 -> context.getString(R.string.schedule_day_tue)
                    3 -> context.getString(R.string.schedule_day_wed)
                    4 -> context.getString(R.string.schedule_day_thu)
                    5 -> context.getString(R.string.schedule_day_fri)
                    6 -> context.getString(R.string.schedule_day_sat)
                    7 -> context.getString(R.string.schedule_day_sun)
                    else -> null
                }
            }
            names.joinToString(", ")
        }
    }
    return "$timeStr · $dayStr"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScheduleDialog(
    identities: List<IdentityEntity>,
    schedule: IdentityScheduleWithIdentityName?,
    onDismiss: () -> Unit,
    onSave: (identityId: Long, hour: Int, minute: Int, daysOfWeek: String) -> Unit
) {
    val isEdit = schedule != null
    var selectedIdentityId by remember { mutableStateOf(schedule?.identityId ?: identities.firstOrNull()?.id ?: 0L) }
    var selectedHour by remember { mutableStateOf(schedule?.hour ?: 9) }
    var selectedMinute by remember { mutableStateOf(schedule?.minute ?: 0) }
    var selectedDays by remember { mutableStateOf(schedule?.daysOfWeek ?: "") }

    val dayLabels = listOf(
        1 to stringResource(R.string.schedule_day_mon),
        2 to stringResource(R.string.schedule_day_tue),
        3 to stringResource(R.string.schedule_day_wed),
        4 to stringResource(R.string.schedule_day_thu),
        5 to stringResource(R.string.schedule_day_fri),
        6 to stringResource(R.string.schedule_day_sat),
        7 to stringResource(R.string.schedule_day_sun)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) stringResource(R.string.schedule_edit_title)
                       else stringResource(R.string.schedule_add_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.schedule_pick_identity),
                    fontSize = 15.sp,
                    color = GrayDark,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp)
                        .heightIn(max = 200.dp)
                ) {
                    identities.forEach { identity ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIdentityId == identity.id,
                                onClick = { selectedIdentityId = identity.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = identity.name,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(thickness = 0.5.dp, color = GrayLight)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.schedule_time_header),
                    fontSize = 15.sp,
                    color = GrayDark,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                        Text(
                            text = String.format("%02d", selectedHour),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark
                        )
                        IconButton(onClick = { selectedHour = (selectedHour + 23) % 24 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    Text(
                        text = ":",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayMiddle,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                        Text(
                            text = String.format("%02d", selectedMinute),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark
                        )
                        IconButton(onClick = { selectedMinute = (selectedMinute + 55) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(thickness = 0.5.dp, color = GrayLight)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.schedule_days_label),
                    fontSize = 15.sp,
                    color = GrayDark,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedDays == "",
                        onClick = { selectedDays = "" },
                        label = {
                            Text(
                                stringResource(R.string.schedule_every_day),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayLabels.forEach { (num, label) ->
                        val chipSelected = selectedDays.split(",").contains(num.toString())
                        FilterChip(
                            selected = chipSelected,
                            onClick = {
                                val current = selectedDays.split(",")
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .toMutableSet()
                                if (chipSelected) current.remove(num) else current.add(num)
                                selectedDays = current.sorted().joinToString(",")
                            },
                            label = { Text(label, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedIdentityId != 0L) {
                        onSave(selectedIdentityId, selectedHour, selectedMinute, selectedDays)
                    }
                },
                enabled = selectedIdentityId != 0L
            ) {
                Text(if (isEdit) stringResource(R.string.save) else stringResource(R.string.schedule_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
