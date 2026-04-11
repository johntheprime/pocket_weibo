package com.myweibo.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.myweibo.MyWeiboApp
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.ui.components.Avatar
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.AvatarColors
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayDark
import com.myweibo.ui.theme.GrayLight
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.Surface
import com.myweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    onNavigateToIdentities: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    var showAddDialog by remember { mutableStateOf(false) }

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
                                    color = Color(activeIdentity!!.avatarColor),
                                    size = 60.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(GrayLight, CircleShape),
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
                Text(
                    text = "身份管理",
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Surface
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(identities) { identity ->
                            IdentityChip(
                                identity = identity,
                                isActive = identity.id == activeIdentity?.id,
                                onClick = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        app.repository.setActiveIdentity(identity.id)
                                    }
                                }
                            )
                        }
                        item {
                            AddIdentityChip(onClick = { showAddDialog = true })
                        }
                    }
                }
            }

            item {
                Divider()
                Text(
                    text = "我的发布",
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Surface
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "查看所有发布的内容",
                            fontSize = 15.sp,
                            color = GrayDark,
                            modifier = Modifier.clickable { }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddIdentityDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, color ->
                CoroutineScope(Dispatchers.IO).launch {
                    val newIdentity = IdentityEntity(
                        name = name,
                        avatarColor = color,
                        isActive = identities.isEmpty()
                    )
                    app.repository.insertIdentity(newIdentity)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun IdentityChip(
    identity: IdentityEntity,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Avatar(
            name = identity.name,
            color = Color(identity.avatarColor),
            size = 50.dp
        )
        Text(
            text = identity.name,
            fontSize = 12.sp,
            color = if (isActive) WeiboOrange else GrayMiddle,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AddIdentityChip(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(GrayLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加身份",
                tint = GrayMiddle,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = "添加",
            fontSize = 12.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AddIdentityDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(AvatarColors[0]) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Surface
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "添加身份",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )

                Text(
                    text = "给自己起个新名字，开启一段新对话",
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 8.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("身份名称") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                )

                Text(
                    text = "选择代表色",
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvatarColors) { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color, CircleShape)
                                .clickable { selectedColor = color }
                                .then(
                                    if (color == selectedColor) {
                                        Modifier.border(2.dp, GrayDark, CircleShape)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (color == selectedColor) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消", color = GrayMiddle)
                    }
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(name, selectedColor.toArgb())
                            }
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WeiboOrange
                        )
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
