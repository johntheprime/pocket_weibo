package com.pocketweibo.ui.screens.me

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.backup.AutoBackupCrypto
import com.pocketweibo.data.backup.AutoDailyBackup
import com.pocketweibo.data.backup.DayBackupSlot
import com.pocketweibo.data.prefs.BackupPreferences
import com.pocketweibo.data.prefs.ShakeReminderSettings
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.diagnostic.DiagnosticLog
import com.pocketweibo.diagnostic.DiagnosticLogBuffer
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import java.util.Locale

private const val RepoUrl = "https://github.com/johntheprime/pocket_weibo"

@Composable
private fun ShakeReminderSettingsSection() {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    val settings by UiPreferences.shakeReminderSettingsFlow(app).collectAsState(initial = ShakeReminderSettings.Default)

    fun formatLocalTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return DateFormat.getTimeFormat(context).format(cal.time)
    }

    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_shake_reminder_section_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = GrayDark,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_shake_reminder_section_desc),
            fontSize = 12.sp,
            color = GrayMiddle,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 4.dp)
        )
        MenuItem(
            title = stringResource(R.string.settings_shake_reminder_evening_title),
            subtitle = stringResource(
                R.string.settings_shake_reminder_evening_subtitle,
                formatLocalTime(settings.eveningHour, settings.eveningMinute)
            ),
            onClick = {
                val act = context.findActivity() ?: return@MenuItem
                TimePickerDialog(
                    act,
                    { _, h, m ->
                        scope.launch { UiPreferences.setShakeReminderEvening(app, h, m) }
                    },
                    settings.eveningHour,
                    settings.eveningMinute,
                    DateFormat.is24HourFormat(act)
                ).show()
            }
        )
        Divider()
        MenuItem(
            title = stringResource(R.string.settings_shake_reminder_morning_title),
            subtitle = stringResource(
                R.string.settings_shake_reminder_morning_subtitle,
                formatLocalTime(settings.morningHour, settings.morningMinute)
            ),
            onClick = {
                val act = context.findActivity() ?: return@MenuItem
                TimePickerDialog(
                    act,
                    { _, h, m ->
                        scope.launch { UiPreferences.setShakeReminderMorning(app, h, m) }
                    },
                    settings.morningHour,
                    settings.morningMinute,
                    DateFormat.is24HourFormat(act)
                ).show()
            }
        )
    }
}

@Composable
fun MeSettingsScreen(
    onBack: () -> Unit,
    onOpenIdentities: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = stringResource(R.string.title_settings),
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

        Divider(thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                MenuItem(
                    title = stringResource(R.string.me_identity_title),
                    subtitle = stringResource(R.string.me_identity_subtitle),
                    onClick = onOpenIdentities
                )
            }
            item {
                Divider()
                MenuItem(
                    title = stringResource(R.string.schedule_title),
                    subtitle = stringResource(R.string.schedule_settings_subtitle),
                    onClick = onOpenSchedule
                )
            }
            item {
                Divider()
                MenuItem(
                    title = stringResource(R.string.settings_export_title),
                    subtitle = stringResource(R.string.settings_export_subtitle),
                    onClick = { showExportDialog = true }
                )
            }
            item {
                Divider()
                MenuItem(
                    title = stringResource(R.string.settings_import_title),
                    subtitle = stringResource(R.string.settings_import_subtitle),
                    onClick = { showImportDialog = true }
                )
            }
            item {
                Divider()
                LanguagePreferenceSection(
                    onApplied = { context.findActivity()?.recreate() }
                )
            }
            item {
                Divider()
                ShakeReminderSettingsSection()
            }
            item {
                Divider()
                AutoBackupSettingsSection()
            }
            item {
                Divider()
                DiagnosticLogSection()
            }
            item {
                Divider()
                AboutSection(
                    versionLabel = rememberAppVersionLabel(context),
                    onOpenRepo = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(RepoUrl))
                        )
                    }
                )
            }
        }
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImportFromText = { json, override ->
                scope.launch {
                    val success = app.repository.importData(json, override)
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.toast_import_ok)
                        else context.getString(R.string.toast_import_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onImportFromFile = { uri, override ->
                scope.launch {
                    val success = app.repository.importBackupFromUri(uri, override)
                    Toast.makeText(
                        context,
                        if (success) context.getString(R.string.toast_import_ok)
                        else context.getString(R.string.toast_import_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                scope.launch {
                    try {
                        when (format) {
                            "ZIP" -> {
                                val zipFile = withContext(Dispatchers.IO) { app.repository.exportAllDataZip() }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    zipFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                                )
                            }
                            "JSON" -> {
                                val content = app.repository.exportAllData()
                                val file = File(context.cacheDir, "pocket_weibo_backup.json")
                                file.writeText(content)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                                )
                            }
                            else -> {
                                val content = app.repository.exportAllDataToMarkdown()
                                val file = File(context.cacheDir, "pocket_weibo_backup.md")
                                file.writeText(content)
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/markdown"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                                )
                            }
                        }
                        Toast.makeText(context, context.getString(R.string.toast_export_done), Toast.LENGTH_SHORT)
                            .show()
                        showExportDialog = false
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.toast_export_fail), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        )
    }
}

private fun shareDecryptedJsonForBackup(context: Context, plain: ByteArray) {
    val out = File(context.cacheDir, "pw_decrypted_${System.currentTimeMillis()}.json")
    out.writeBytes(plain)
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        out
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.settings_auto_backup_share_decrypted_title))
    )
}

@Composable
private fun AutoBackupSettingsSection() {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    var lastDay by remember { mutableStateOf<String?>(null) }
    var daySlots by remember { mutableStateOf<List<DayBackupSlot>>(emptyList()) }
    val dateFormatter = remember {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }

    fun load() {
        scope.launch(Dispatchers.IO) {
            val d = BackupPreferences.getLastAutoTextBackupDay(app)
            val slots = AutoDailyBackup.listLastThreeCalendarDaySlots(app)
            withContext(Dispatchers.Main) {
                lastDay = d
                daySlots = slots
            }
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("empty")
                    AutoBackupCrypto.decrypt(bytes, app)
                }
            }
            result.fold(
                onSuccess = { bytes ->
                    shareDecryptedJsonForBackup(context, bytes)
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_auto_backup_toast_decrypt_ok),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_auto_backup_toast_decrypt_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_auto_backup_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            Text(
                text = stringResource(R.string.settings_auto_backup_subtitle),
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = stringResource(R.string.settings_auto_backup_last_prefix) +
                    (lastDay ?: stringResource(R.string.settings_auto_backup_never)),
                fontSize = 13.sp,
                color = GrayDark,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = stringResource(R.string.settings_auto_backup_three_slots_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GrayDark,
                modifier = Modifier.padding(top = 14.dp)
            )
            daySlots.forEach { slot ->
                val dateLabel = slot.date.format(dateFormatter)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        text = dateLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GrayDark
                    )
                    val file = slot.file
                    if (file != null) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            AutoBackupCrypto.decrypt(file.readBytes(), app)
                                        }
                                    }
                                    result.fold(
                                        onSuccess = { bytes ->
                                            shareDecryptedJsonForBackup(context, bytes)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_auto_backup_toast_decrypt_ok),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onFailure = {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.settings_auto_backup_toast_decrypt_fail),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_auto_backup_decrypt_share),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.settings_auto_backup_slot_missing),
                            fontSize = 13.sp,
                            color = GrayMiddle,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { pickLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_auto_backup_pick_file))
                    Text(
                        text = stringResource(R.string.settings_auto_backup_pick_file_sub),
                        fontSize = 12.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogSection() {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    var captureOn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        captureOn = UiPreferences.isDiagnosticLogCaptureEnabled(appCtx)
        DiagnosticLogBuffer.captureEnabled = captureOn
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_diagnostic_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            Text(
                text = stringResource(R.string.settings_diagnostic_subtitle),
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_diagnostic_capture_label),
                    fontSize = 15.sp,
                    color = GrayDark,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = captureOn,
                    onCheckedChange = { v ->
                        scope.launch {
                            UiPreferences.setDiagnosticLogCaptureEnabled(appCtx, v)
                            DiagnosticLogBuffer.captureEnabled = v
                            captureOn = v
                            if (v) {
                                DiagnosticLog.i("PW_Reminder", "Diagnostic in-memory capture enabled (settings)")
                            }
                        }
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                val header = buildDiagnosticLogHeader(context)
                                val lines = DiagnosticLogBuffer.dumpLines()
                                val body = if (lines.isEmpty()) {
                                    context.getString(R.string.settings_diagnostic_buffer_empty)
                                } else {
                                    lines.joinToString("\n")
                                }
                                val f = File(
                                    context.cacheDir,
                                    "pocket_weibo_diag_${System.currentTimeMillis()}.txt"
                                )
                                f.writeText(header + body + "\n")
                                f
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    shareIntent,
                                    context.getString(R.string.settings_diagnostic_share_title)
                                )
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_diagnostic_toast_exported),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.settings_diagnostic_export),
                        color = WeiboOrange
                    )
                }
                TextButton(
                    onClick = {
                        DiagnosticLogBuffer.clear()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_diagnostic_toast_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        stringResource(R.string.settings_diagnostic_clear),
                        color = GrayMiddle
                    )
                }
            }
        }
    }
}

private fun buildDiagnosticLogHeader(context: android.content.Context): String {
    val versionLine = runCatching {
        @Suppress("DEPRECATION")
        val pinfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val name = pinfo.versionName ?: ""
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pinfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            pinfo.versionCode.toString()
        }
        context.getString(R.string.about_version_format, name, code)
    }.getOrElse { context.getString(R.string.about_version_unknown) }
    val device =
        "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})".trim()
    return buildString {
        appendLine("PocketWeibo diagnostic log")
        appendLine(versionLine)
        appendLine(device)
        appendLine("bufferLines=${DiagnosticLogBuffer.lineCount()}")
        appendLine("---")
        appendLine()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePreferenceSection(onApplied: () -> Unit) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf("system") }

    LaunchedEffect(Unit) {
        selected = UiPreferences.getLanguageCode(appCtx)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_section),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GrayMiddle
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val chipModifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                FilterChip(
                    selected = selected == "system",
                    onClick = {
                        scope.launch {
                            UiPreferences.setLanguageCode(appCtx, "system")
                            UiPreferences.applyLanguageCode("system")
                            selected = "system"
                            onApplied()
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_language_system),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    },
                    modifier = chipModifier
                )
                FilterChip(
                    selected = selected == "zh",
                    onClick = {
                        scope.launch {
                            UiPreferences.setLanguageCode(appCtx, "zh")
                            UiPreferences.applyLanguageCode("zh")
                            selected = "zh"
                            onApplied()
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_language_zh),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    },
                    modifier = chipModifier
                )
                FilterChip(
                    selected = selected == "en",
                    onClick = {
                        scope.launch {
                            UiPreferences.setLanguageCode(appCtx, "en")
                            UiPreferences.applyLanguageCode("en")
                            selected = "en"
                            onApplied()
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_language_en),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    },
                    modifier = chipModifier
                )
            }
        }
    }
}

@Composable
private fun rememberAppVersionLabel(context: android.content.Context): String {
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            val pinfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val name = pinfo.versionName ?: ""
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pinfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pinfo.versionCode.toString()
            }
            context.getString(R.string.about_version_format, name, code)
        }.getOrElse { context.getString(R.string.about_version_unknown) }
    }
}

@Composable
private fun AboutSection(
    versionLabel: String,
    onOpenRepo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.about_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            Text(
                text = stringResource(R.string.about_app_line),
                fontSize = 15.sp,
                color = GrayDark,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = versionLabel,
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = stringResource(R.string.about_desc),
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = stringResource(R.string.about_authors),
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable(onClick = onOpenRepo),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_links_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    Text(
                        text = RepoUrl,
                        fontSize = 12.sp,
                        color = WeiboOrange,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = GrayLight
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GrayLight
            )
        }
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImportFromText: (String, Boolean) -> Unit,
    onImportFromFile: (Uri, Boolean) -> Unit
) {
    val context = LocalContext.current
    var jsonInput by remember { mutableStateOf("") }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var override by remember { mutableStateOf(false) }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = queryDisplayName(context, uri)
        pickedUri = uri
        pickedFileName = displayName
        jsonInput = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_intro), fontSize = 14.sp, color = GrayMiddle)
                TextButton(
                    onClick = {
                        openJsonLauncher.launch(
                            arrayOf(
                                "application/json",
                                "application/zip",
                                "application/x-zip-compressed",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.import_pick_file), color = WeiboOrange)
                }
                pickedFileName?.let { name ->
                    Text(
                        text = stringResource(R.string.import_loaded, name),
                        fontSize = 12.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = {
                        jsonInput = it
                        pickedFileName = null
                        pickedUri = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                    placeholder = { Text(stringResource(R.string.import_placeholder), fontSize = 13.sp) },
                    maxLines = 10,
                    singleLine = false
                )
                Text(
                    if (override) stringResource(R.string.import_warn_override)
                    else stringResource(R.string.import_warn_merge),
                    fontSize = 12.sp,
                    color = WeiboOrange,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = override,
                        onCheckedChange = { override = it }
                    )
                    Text(stringResource(R.string.import_override_label), fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = pickedUri
                    if (uri != null) {
                        onImportFromFile(uri, override)
                        onDismiss()
                    } else {
                        val trimmed = jsonInput.trim().trimStart('\uFEFF')
                        if (trimmed.isNotBlank()) {
                            onImportFromText(trimmed, override)
                            onDismiss()
                        }
                    }
                },
                enabled = pickedUri != null || jsonInput.isNotBlank()
            ) {
                Text(stringResource(R.string.import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.import_cancel))
            }
        }
    )
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("ZIP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                Text(stringResource(R.string.export_intro), fontSize = 14.sp, color = GrayMiddle)
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "ZIP",
                        onClick = { selectedFormat = "ZIP" }
                    )
                    Text(stringResource(R.string.export_zip), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "JSON",
                        onClick = { selectedFormat = "JSON" }
                    )
                    Text(stringResource(R.string.export_json), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "Markdown",
                        onClick = { selectedFormat = "Markdown" }
                    )
                    Text(stringResource(R.string.export_markdown), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedFormat) }) {
                Text(stringResource(R.string.export_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.export_cancel))
            }
        }
    )
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    if (uri.scheme != android.content.ContentResolver.SCHEME_CONTENT) {
        return uri.lastPathSegment
    }
    val nameColumn = OpenableColumns.DISPLAY_NAME
    return context.contentResolver.query(
        uri,
        arrayOf(nameColumn),
        null,
        null,
        null
    )?.use { cursor ->
        val idx = cursor.getColumnIndex(nameColumn)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    } ?: uri.lastPathSegment
}
