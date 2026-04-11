package com.myweibo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myweibo.data.local.dao.PostWithIdentity
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.GrayDark
import com.myweibo.ui.theme.WeiboOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostCard(
    post: PostWithIdentity,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                name = post.identityName,
                color = Color(post.identityAvatarColor),
                size = 50.dp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = post.identityName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    Text(
                        text = " · ${formatTime(post.createdAt)}",
                        fontSize = 12.sp,
                        color = GrayMiddle
                    )
                }

                Text(
                    text = post.content,
                    fontSize = 14.sp,
                    color = GrayDark,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "转发",
                        tint = GrayMiddle,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = "转发",
                onClick = onShareClick
            )
            ActionItem(
                icon = {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = GrayMiddle,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = post.commentCount.toString(),
                onClick = onCommentClick
            )
            ActionItem(
                icon = {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = if (post.likeCount > 0) post.likeCount.toString() else "赞",
                onClick = onLikeClick,
                tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = text,
            fontSize = 12.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun formatTime(timestamp: Long): String {
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
