package com.myweibo.ui.screens.compose

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myweibo.MyWeiboApp
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.data.local.entity.PostEntity
import com.myweibo.ui.components.Avatar
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
fun ComposeScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)

    var selectedIdentity by remember { mutableStateOf<IdentityEntity?>(null) }
    var content by remember { mutableStateOf("") }
    var showIdentityPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = GrayDark
                    )
                }

                Text(
                    text = "发微博",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )

                Button(
                    onClick = {
                        if (content.isNotBlank() && selectedIdentity != null) {
                            CoroutineScope(Dispatchers.IO).launch {
                                app.repository.insertPost(
                                    PostEntity(
                                        identityId = selectedIdentity!!.id,
                                        content = content
                                    )
                                )
                            }
                            onDismiss()
                        }
                    },
                    enabled = content.isNotBlank() && selectedIdentity != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeiboOrange,
                        disabledContainerColor = GrayLight
                    )
                ) {
                    Text("发送")
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showIdentityPicker = !showIdentityPicker }
                    ) {
                        if (selectedIdentity != null) {
                            Avatar(
                                name = selectedIdentity!!.name,
                                color = Color(selectedIdentity!!.avatarColor),
                                size = 32.dp
                            )
                            Text(
                                text = selectedIdentity!!.name,
                                fontSize = 14.sp,
                                color = GrayDark,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(GrayLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "?", fontSize = 14.sp, color = GrayMiddle)
                            }
                            Text(
                                text = "选择身份",
                                fontSize = 14.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    if (showIdentityPicker && identities.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(identities) { identity ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (selectedIdentity?.id == identity.id)
                                                Color(identity.avatarColor)
                                            else
                                                GrayLight,
                                            CircleShape
                                        )
                                        .border(
                                            width = if (selectedIdentity?.id == identity.id) 2.dp else 0.dp,
                                            color = if (selectedIdentity?.id == identity.id) GrayDark else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedIdentity = identity
                                            showIdentityPicker = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = identity.name.first().toString().uppercase(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("分享新鲜事...", color = GrayMiddle) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        minLines = 5
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Image,
                    label = "图片",
                    onClick = { }
                )
                ActionButton(
                    icon = Icons.Default.LocationOn,
                    label = "位置",
                    onClick = { }
                )
                ActionButton(
                    icon = Icons.Default.AlternateEmail,
                    label = "艾特",
                    onClick = { }
                )
            }

            if (identities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请先在\"我\"页面添加身份",
                        fontSize = 14.sp,
                        color = GrayMiddle
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = WeiboOrange,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
