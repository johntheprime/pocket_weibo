package com.pocketweibo.ui.screens.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.PostImageGallery
import com.pocketweibo.ui.components.SelectablePostBody
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.copyPlainToClipboard
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ... (Keep your ViewModel and State logic the same)
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModel.Factory(app.repository))

    LaunchedEffect(postId) { viewModel.loadPost(postId) }

    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }

    // Use Scaffold: It is specifically designed to handle top bars and bottom bars
    // while managing inner content padding correctly.
    androidx.compose.material3.Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                WeiboTitleBar(
                    title = stringResource(R.string.title_post_detail),
                    leftIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                stringResource(R.string.back_cd),
                                tint = WeiboOrange
                            )
                        }
                    }
                )
                Divider(thickness = 0.5.dp)
            }
        },
        bottomBar = {
            // Pinning this to the bottomBar slot of the Scaffold handles the keyboard transition best
            Surface(
                color = Color.White,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding() // This pushes the input bar up
                    .navigationBarsPadding() // This respects the system nav bar
            ) {
                Column {
                    Divider(thickness = 0.5.dp)
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.post_detail_comment_hint),
                                    fontSize = 14.sp,
                                    color = GrayMiddle
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WeiboOrange,
                                unfocusedBorderColor = GrayLight,
                                focusedContainerColor = Color(0xFFF8F8F8),
                                unfocusedContainerColor = Color(0xFFF8F8F8)
                            ),
                            maxLines = 4
                        )
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
                                Icons.Default.Send,
                                stringResource(R.string.post_detail_send_cd),
                                tint = if (commentText.isNotBlank()) WeiboOrange else GrayMiddle
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // The innerPadding automatically accounts for the topBar and the bottomBar (including keyboard)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(innerPadding)
        ) {
            post?.let { currentPost ->
                item {
                    PostDetailCard(
                        post = currentPost,
                        onLikeClick = { viewModel.toggleLike() },
                        onShareClick = { sharePost(context, currentPost.identityName, currentPost.content) }
                    )
                }

                item { CommentsHeader(commentCount = comments.size) }

                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.comments_empty),
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
            }
        }
    }
}

@Composable
private fun PostDetailCard(
    post: com.pocketweibo.data.local.dao.PostWithIdentity,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val resources = LocalContext.current.resources
    val shareLabel = stringResource(R.string.post_detail_action_share)
    val likeCd = stringResource(R.string.post_detail_like_cd)
    val commentCd = stringResource(R.string.post_detail_comment_cd)
    val moreCd = stringResource(R.string.post_detail_more_cd)
    val shareCd = stringResource(R.string.post_detail_share_cd)
    val likeLabelZero = stringResource(R.string.action_like)
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
                        text = resources.formatRelativeTime(post.createdAt, RelativeTimePreset.PostDetail),
                        fontSize = 13.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = moreCd,
                        tint = GrayMiddle
                    )
                }
            }
            
            SelectablePostBody(
                text = post.content,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = GrayDark,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.padding(top = 12.dp)
            )

            if (PostAttachmentStorage.parseStoredPaths(post.imageUris).isNotEmpty()) {
                PostImageGallery(
                    imageUris = post.imageUris,
                    maxHeight = 160.dp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

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
                            contentDescription = shareCd,
                            tint = GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = shareLabel,
                    onClick = onShareClick
                )
                Spacer(modifier = Modifier.width(24.dp))
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = likeCd,
                            tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = if (post.likeCount > 0) post.likeCount.toString() else likeLabelZero,
                    onClick = onLikeClick,
                    tint = if (post.isLiked) Color(0xFFFF5136) else GrayMiddle
                )
                Spacer(modifier = Modifier.width(24.dp))
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = commentCd,
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
    val header = stringResource(R.string.comments_header)
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
                text = header,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentCard(comment: CommentWithIdentity) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.clipboard_label_comment)
    val copiedToast = stringResource(R.string.toast_comment_copied)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    context.copyPlainToClipboard(clipLabel, comment.content, toast = copiedToast)
                }
            ),
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
                            text = " · ${context.resources.formatRelativeTime(comment.createdAt, RelativeTimePreset.PostDetail)}",
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

private fun sharePost(context: Context, authorName: String, content: String) {
    val shareText = "$authorName: $content"
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, shareText)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_chooser_title))
    try {
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.share_clip_label), shareText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.toast_clipboard_copied), Toast.LENGTH_SHORT).show()
    }
}
