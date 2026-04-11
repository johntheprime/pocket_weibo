package com.myweibo.ui.screens.message

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.WeiboOrange

@Composable
fun MessageScreen(
    modifier: Modifier = Modifier
) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
}
