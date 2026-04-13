package com.pocketweibo.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.launch
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider

@Composable
fun MeScreen(
    onNavigateToMyPosts: () -> Unit = {},
    onNavigateToIdentities: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = "我",
            rightIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = WeiboOrange
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeIdentity != null) {
                                Avatar(
                                    name = activeIdentity!!.name,
                                    color = Color(0xFF4A90D9),
                                    size = 60.dp,
                                    avatarResName = activeIdentity!!.avatarResName
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(GrayLight, androidx.compose.foundation.shape.CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "?", fontSize = 24.sp, color = GrayMiddle)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                Text(
                                    text = activeIdentity?.name ?: "未选择身份",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GrayDark
                                )
                                if (activeIdentity?.motto?.isNotEmpty() == true) {
                                    Text(
                                        text = "\"${activeIdentity!!.motto}\"",
                                        fontSize = 12.sp,
                                        color = GrayMiddle,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    text = "${identities.size} 个身份",
                                    fontSize = 14.sp,
                                    color = GrayMiddle,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Divider()
                MenuItem(
                    title = "身份管理",
                    subtitle = "查看、编辑、添加身份",
                    onClick = onNavigateToIdentities
                )
            }

            item {
                Divider()
                MenuItem(
                    title = "我的发布",
                    subtitle = "查看所有发布的内容",
                    onClick = onNavigateToMyPosts
                )
            }

            item {
                Divider()
                MenuItem(
                    title = "导出数据",
                    subtitle = "备份所有数据到JSON/Markdown文件",
                    onClick = { showExportDialog = true }
                )
            }

            item {
                Divider()
                MenuItem(
                    title = "导入数据",
                    subtitle = "从JSON文件恢复数据",
                    onClick = { showImportDialog = true }
                )
            }

            item {
                Divider()
                MenuItem(
                    title = "清空所有数据",
                    subtitle = "删除所有身份、微博和评论",
                    onClick = { showClearDataDialog = true },
                    isDestructive = true
                )
            }
            
            activeIdentity?.let { identity ->
                if (identity.nationality.isNotEmpty() || identity.occupation.isNotEmpty()) {
                    item {
                        Divider()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (identity.nationality.isNotEmpty()) {
                                    InfoRow(label = "国籍", value = identity.nationality)
                                }
                                if (identity.occupation.isNotEmpty()) {
                                    InfoRow(label = "职业", value = identity.occupation)
                                }
                                if (identity.famousWork.isNotEmpty()) {
                                    InfoRow(label = "代表作", value = identity.famousWork)
                                }
                            }
                        }
                    }
                }
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
            },
            onExportSelective = { types ->
                scope.launch {
                    val fileName = "pocket_weibo_backup.json"
                    val content = app.repository.exportSelectiveData(types)
                    val file = java.io.File(context.cacheDir, fileName)
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
                    context.startActivity(Intent.createChooser(shareIntent, "分享备份"))
                    Toast.makeText(context, "数据已导出", Toast.LENGTH_SHORT).show()
                }
            },
            onExportCsv = {
                scope.launch {
                    val fileName = "pocket_weibo_backup.csv"
                    val content = app.repository.exportDataToCsv()
                    val file = java.io.File(context.cacheDir, fileName)
                    file.writeText(content)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享备份"))
                    Toast.makeText(context, "CSV数据已导出", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showClearDataDialog) {
        ClearDataDialog(
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                scope.launch {
                    app.repository.clearAllData()
                    Toast.makeText(context, "所有数据已清空", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
private fun MenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
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
                    color = if (isDestructive) Color(0xFFFF5136) else GrayDark
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = if (isDestructive) Color(0xFFFF5136) else GrayMiddle,
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
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = GrayMiddle
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = GrayDark
        )
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, Boolean) -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }
    var override by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入数据") },
        text = {
            Column {
                Text("请粘贴JSON备份数据:", fontSize = 14.sp, color = GrayMiddle)
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
                    if (jsonInput.isNotBlank()) {
                        onImport(jsonInput, override)
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
    onExport: (String) -> Unit,
    onExportSelective: (Set<String>) -> Unit,
    onExportCsv: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf("JSON") }
    var selectedTypes by remember { mutableStateOf(setOf("identities", "posts", "comments")) }
    
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
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedFormat == "CSV",
                        onClick = { selectedFormat = "CSV" }
                    )
                    Text("CSV (Excel)", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                }
                
                if (selectedFormat == "JSON") {
                    Text(
                        text = "选择数据类型:",
                        fontSize = 14.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            selected = "identities" in selectedTypes,
                            onCheckedChange = {
                                selectedTypes = if (it) selectedTypes + "identities" else selectedTypes - "identities"
                            }
                        )
                        Text("身份", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            selected = "posts" in selectedTypes,
                            onCheckedChange = {
                                selectedTypes = if (it) selectedTypes + "posts" else selectedTypes - "posts"
                            }
                        )
                        Text("微博", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            selected = "comments" in selectedTypes,
                            onCheckedChange = {
                                selectedTypes = if (it) selectedTypes + "comments" else selectedTypes - "comments"
                            }
                        )
                        Text("评论", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedFormat) {
                        "JSON" -> {
                            if (selectedTypes.isNotEmpty()) {
                                onExportSelective(selectedTypes)
                            }
                        }
                        "Markdown" -> onExport(selectedFormat)
                        "CSV" -> onExportCsv()
                    }
                    onDismiss()
                },
                enabled = (selectedFormat == "JSON" && selectedTypes.isNotEmpty()) || selectedFormat == "Markdown" || selectedFormat == "CSV"
            ) {
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

@Composable
private fun ClearDataDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清空所有数据") },
        text = {
            Column {
                Text(
                    text = "此操作将删除所有数据，包括:",
                    fontSize = 14.sp,
                    color = GrayDark
                )
                Text(
                    text = "- 所有身份\n- 所有微博\n- 所有评论\n\n此操作不可撤销，请先备份数据!",
                    fontSize = 13.sp,
                    color = Color(0xFFFF5136),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5136)
                )
            ) {
                Text("确认清空")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
