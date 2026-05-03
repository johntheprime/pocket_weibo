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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
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
            title = "设置",
            leftIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
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
                    title = "导出数据",
                    subtitle = "备份所有数据到 JSON / Markdown 文件",
                    onClick = { showExportDialog = true }
                )
            }
            item {
                Divider()
                MenuItem(
                    title = "导入数据",
                    subtitle = "从 JSON 文件或粘贴内容恢复",
                    onClick = { showImportDialog = true }
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
                        if (success) "数据导入成功" else "数据导入失败",
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
                    context.startActivity(Intent.createChooser(shareIntent, "分享备份"))
                    Toast.makeText(context, "数据已导出", Toast.LENGTH_SHORT).show()
                }
            }
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
                pinfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pinfo.versionCode.toLong()
            }
            "版本 $name（$code）"
        }.getOrElse { "版本未知" }
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
                text = "关于本软件",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            Text(
                text = "PocketWeibo（口袋微博）",
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
                text = "本地微博风格笔记与多身份内容管理；数据保存在本机。",
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                text = "作者与贡献者信息见 GitHub 仓库说明与提交历史；欢迎提交 Issue 与 PR。",
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
                        text = "相关链接",
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
                Toast.makeText(context, "无法读取该文件", Toast.LENGTH_SHORT).show()
                pickedFileName = null
            } else {
                jsonInput = text
                pickedFileName = displayName
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入数据") },
        text = {
            Column {
                Text("选择 JSON 文件或粘贴备份内容:", fontSize = 14.sp, color = GrayMiddle)
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
                    Text("从文件选择…", color = WeiboOrange)
                }
                pickedFileName?.let { name ->
                    Text(
                        text = "已载入: $name",
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
                    placeholder = { Text("或在此粘贴 JSON", fontSize = 13.sp) },
                    maxLines = 10,
                    singleLine = false
                )
                Text(
                    if (override) "警告: 此操作会清空现有数据并导入"
                    else "此操作会追加数据，不会覆盖现有数据",
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
                    Text("覆盖现有数据", fontSize = 14.sp)
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
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
        title = { Text("导出数据") },
        text = {
            Column {
                Text("选择导出格式:", fontSize = 14.sp, color = GrayMiddle)
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "JSON",
                        onClick = { selectedFormat = "JSON" }
                    )
                    Text("JSON (可导入)", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "Markdown",
                        onClick = { selectedFormat = "Markdown" }
                    )
                    Text("Markdown (仅备份)", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onExport(selectedFormat) }) {
                Text("导出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
