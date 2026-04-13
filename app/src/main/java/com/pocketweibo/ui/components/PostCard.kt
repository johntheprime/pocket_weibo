package com.pocketweibo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.GrayDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostCard(
    post: PostWithIdentity,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onPostClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
            .clickable(onClick = onPostClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = post.identityName,
                    color = Color(0xFF4A90D9),
                    size = 40.dp,
                    avatarResName = post.identityAvatarResName
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = post.identityName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formatTime(post.createdAt),
                        fontSize = 11.sp,
                        color = GrayMiddle,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Text(
                text = post.content,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = Icons.Default.Share,
                    text = "转发",
                    count = null,
                    onClick = onShareClick
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                ActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    text = "评论",
                    count = post.commentCount,
                    onClick = onCommentClick
                )
                
                Spacer(modifier = Modifier.width(10.dp))
                
                ActionButton(
                    icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    text = "点赞",
                    count = post.likeCount,
                    isLiked = post.isLiked,
                    onClick = onLikeClick
                )
            }
        }
    }
}

/** Keeps action icons on one vertical line across cards; counts sit in this slot right-aligned. */
private val ActionButtonCountSlotWidth = 20.dp

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    count: Int? = null,
    isLiked: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value
    
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                if (isPressed) Color(0xFFE0E0E0) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = when {
                isLiked -> Color(0xFFFF5136)
                else -> GrayMiddle
            },
            modifier = Modifier.size(20.dp)
        )
        Box(
            modifier = Modifier
                .width(ActionButtonCountSlotWidth)
                .padding(start = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (count != null && count > 0) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    color = when {
                        isLiked -> Color(0xFFFF5136)
                        else -> GrayMiddle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = seconds / 3600
    val days = seconds / 86400

    return when {
        seconds < 60 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 12 -> "${hours}小时前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }
}