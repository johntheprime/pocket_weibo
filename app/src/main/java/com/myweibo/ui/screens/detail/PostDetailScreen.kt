package com.myweibo.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myweibo.MyWeiboApp
import com.myweibo.data.local.dao.CommentWithIdentity
import com.myweibo.ui.components.Avatar
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayDark
import com.myweibo.ui.theme.GrayLight
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.WeiboOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val viewModel: PostDetailViewModel = viewModel(
        factory = PostDetailViewModel.Factory(app.repository)
    )
    
    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }
    
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .imePadding()
    ) {
        WeiboTitleBar(
            title = "微博详情",
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            post?.let { currentPost ->
                item {
                    PostDetailCard(
                        post = currentPost,
                        onLikeClick = { viewModel.toggleLike() },
                        onShareClick = { sharePost(context, currentPost.identityName, currentPost.content) }
                    )
                }
                
                item {
                    CommentsHeader(commentCount = comments.size)
                }
                
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无评论，快来抢沙发吧",
                                fontSize = 14.sp,
                                color = GrayMiddle
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentCard(comment = comment)
                        Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }

        Divider()
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                placeholder = { Text("写评论...", fontSize = 14.sp, color = GrayMiddle) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeiboOrange,
                    unfocusedBorderColor = GrayLight
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commentText.isNotBlank()) {
                        viewModel.addComment(commentText)
                        commentText = ""
                    }
                },
                enabled = commentText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (commentText.isNotBlank()) WeiboOrange else GrayMiddle
                )
            }
        }
    }
}

@Composable
private fun PostDetailCard(
    post: com.myweibo.data.local.dao.PostWithIdentity,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = post.identityName,
                    color = Color(0xFF4A90D9),
                    size = 56.dp,
                    avatarResName = post.identityAvatarResName
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = post.identityName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    Text(
                        text = formatTime(post.createdAt),
                        fontSize = 13.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = GrayMiddle
                    )
                }
            }
            
            Text(
                text = post.content,
                fontSize = 16.sp,
                color = GrayDark,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            tint = GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = "分享",
                    onClick = onShareClick
                )
                Spacer(modifier = Modifier.width(24.dp))
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = if (post.likeCount > 0) "${post.likeCount}" else "点赞",
                    onClick = onLikeClick,
                    tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle
                )
                Spacer(modifier = Modifier.width(24.dp))
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "评论",
                            tint = GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = "${post.commentCount}",
                    onClick = { }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    tint: Color = GrayMiddle
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = text,
            fontSize = 13.sp,
            color = tint,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CommentsHeader(commentCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "评论",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            if (commentCount > 0) {
                Text(
                    text = " ($commentCount)",
                    fontSize = 15.sp,
                    color = GrayMiddle
                )
            }
        }
    }
}

@Composable
private fun CommentCard(comment: CommentWithIdentity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = comment.identityName,
                    color = Color(0xFF4A90D9),
                    size = 40.dp,
                    avatarResName = comment.identityAvatarResName
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
                            text = comment.identityName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark
                        )
                        Text(
                            text = " · ${formatCommentTime(comment.createdAt)}",
                            fontSize = 12.sp,
                            color = GrayMiddle
                        )
                    }
                    Text(
                        text = comment.content,
                        fontSize = 14.sp,
                        color = GrayDark,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
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
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatCommentTime(timestamp: Long): String {
    return formatTime(timestamp)
}

private fun sharePost(context: Context, authorName: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("微博内容", "$authorName: $content")
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
