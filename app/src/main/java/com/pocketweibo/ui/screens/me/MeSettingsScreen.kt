package com.pocketweibo.ui.screens.me

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.prefs.UiPreferences
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

private const val RepoUrl = "https://github.com/johntheprime/pocket_weibo"

@Composable
fun MeSettingsScreen(
    onBack: () -> Unit,
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
            onImport = { json, override ->
                scope.launch {
                    val success = app.repository.importData(json, override)
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
                    val fileName = if (format == "JSON") "pocket_weibo_backup.json" else "pocket_weibo_backup.md"
                    val content = if (format == "JSON") {
                        app.repository.exportAllData()
                    } else {
                        app.repository.exportAllDataToMarkdown()
                    }
                    val file = java.io.File(context.cacheDir, fileName)
                    file.writeText(content)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val mimeType = if (format == "JSON") "application/json" else "text/markdown"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, context.getString(R.string.export_share_title))
                    )
                    Toast.makeText(context, context.getString(R.string.toast_export_done), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

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
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_language_section),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            LanguageRow(
                label = stringResource(R.string.settings_language_system),
                selected = selected == "system",
                onClick = {
                    scope.launch {
                        UiPreferences.setLanguageCode(appCtx, "system")
                        UiPreferences.applyLanguageCode("system")
                        selected = "system"
                        onApplied()
                    }
                }
            )
            LanguageRow(
                label = stringResource(R.string.settings_language_zh),
                selected = selected == "zh",
                onClick = {
                    scope.launch {
                        UiPreferences.setLanguageCode(appCtx, "zh")
                        UiPreferences.applyLanguageCode("zh")
                        selected = "zh"
                        onApplied()
                    }
                }
            )
            LanguageRow(
                label = stringResource(R.string.settings_language_en),
                selected = selected == "en",
                onClick = {
                    scope.launch {
                        UiPreferences.setLanguageCode(appCtx, "en")
                        UiPreferences.applyLanguageCode("en")
                        selected = "en"
                        onApplied()
                    }
                }
            )
        }
    }
}

@Composable
private fun LanguageRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            fontSize = 15.sp,
            color = GrayDark,
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f)
                .clickable(onClick = onClick)
        )
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
    onImport: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var jsonInput by remember { mutableStateOf("") }
    var pickedFileName by remember { mutableStateOf<String?>(null) }
    var override by remember { mutableStateOf(false) }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val displayName = queryDisplayName(context, uri)
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }.orEmpty()
                }.getOrDefault("")
            }
            if (text.isBlank()) {
                Toast.makeText(context, context.getString(R.string.toast_file_read_fail), Toast.LENGTH_SHORT).show()
                pickedFileName = null
            } else {
                jsonInput = text
                pickedFileName = displayName
            }
        }
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
                            arrayOf("application/json", "text/plain", "application/*", "*/*")
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
                    val trimmed = jsonInput.trim().trimStart('\uFEFF')
                    if (trimmed.isNotBlank()) {
                        onImport(trimmed, override)
                        onDismiss()
                    }
                },
                enabled = jsonInput.isNotBlank()
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
    var selectedFormat by remember { mutableStateOf("JSON") }

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
