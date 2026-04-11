package com.myweibo.ui.screens.message

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myweibo.MyWeiboApp
import com.myweibo.ui.components.Avatar
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayDark
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.WeiboOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val viewModel: MessageViewModel = viewModel(
        factory = MessageViewModel.Factory(app.repository)
    )
    
    val messages by viewModel.messages.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = "消息",
            rightIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "发起聊天",
                    tint = WeiboOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
        )

        if (messages.isEmpty()) {
            EmptyMessages()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages, key = { it.commentId }) { message ->
                    MessageItem(message = message)
                    Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: MessageItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(
                name = message.commentIdentityName,
                color = Color(message.commentIdentityColor),
                size = 48.dp
            )
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.commentIdentityName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    Text(
                        text = formatMessageTime(message.createdAt),
                        fontSize = 12.sp,
                        color = GrayMiddle
                    )
                }
                
                Text(
                    text = "评论了你的微博: ${message.postContent}",
                    fontSize = 13.sp,
                    color = GrayMiddle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Text(
                        text = message.commentContent,
                        fontSize = 14.sp,
                        color = GrayDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessages() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = GrayMiddle,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(48.dp)
            )
            Text(
                text = "暂无消息",
                fontSize = 16.sp,
                color = GrayMiddle
            )
            Text(
                text = "来自各个身份的互动会在这里显示",
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
