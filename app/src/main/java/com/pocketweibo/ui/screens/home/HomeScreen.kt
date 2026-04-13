package com.pocketweibo.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.CommentBottomSheet
import com.pocketweibo.ui.components.PostCard
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPostClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.repository)
    )
    
    val posts by app.repository.allPosts.collectAsState(initial = emptyList())
    val comments by viewModel.comments.collectAsState()
    val showCommentSheet by viewModel.showCommentSheet.collectAsState()
    val selectedPostId by viewModel.selectedPostId.collectAsState()
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showIdentitySwitcher by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            WeiboTitleBar(
                title = activeIdentity?.name ?: "首页",
                showDropdown = true,
                onTitleClick = { showIdentitySwitcher = !showIdentitySwitcher },
                leftIcon = {
                    if (activeIdentity != null) {
                        Avatar(
                            name = activeIdentity!!.name,
                            color = Color(0xFF4A90D9),
                            size = 28.dp,
                            avatarResName = activeIdentity!!.avatarResName,
                            modifier = Modifier.clickable { showIdentitySwitcher = !showIdentitySwitcher }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = "搜索",
                            tint = WeiboOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                rightIcon = {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            Divider(thickness = 0.5.dp)

            if (posts.isEmpty()) {
                EmptyFeed()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onLikeClick = { viewModel.toggleLike(post.id) },
                            onCommentClick = { viewModel.openComments(post.id) },
                            onShareClick = { sharePost(context, post.identityName, post.content) },
                            onPostClick = { onPostClick(post.id) }
                        )
                    }
                }
            }
        }
        
        if (showCommentSheet && selectedPostId != null) {
            CommentBottomSheet(
                sheetState = sheetState,
                comments = comments,
                activeIdentityId = activeIdentity?.id,
                onDismiss = { viewModel.closeComments() },
                onSendComment = { content, parentId ->
                    viewModel.addComment(selectedPostId!!, content, parentId)
                },
                onDeleteComment = { commentId ->
                    viewModel.deleteComment(commentId, selectedPostId!!)
                }
            )
        }

        if (showIdentitySwitcher) {
            IdentitySwitcherDialog(
                identities = identities,
                activeIdentityId = activeIdentity?.id,
                onDismiss = { showIdentitySwitcher = false },
                onSelectIdentity = { identityId ->
                    scope.launch {
                        app.repository.setActiveIdentity(identityId)
                        showIdentitySwitcher = false
                    }
                }
            )
        }
    }
}

@Composable
private fun IdentitySwitcherDialog(
    identities: List<IdentityEntity>,
    activeIdentityId: Long?,
    onDismiss: () -> Unit,
    onSelectIdentity: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.3f),
        onClick = onDismiss
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp
                ),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "切换身份",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(identities) { identity ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectIdentity(identity.id) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(
                                    name = identity.name,
                                    color = Color(0xFF4A90D9),
                                    size = 44.dp,
                                    avatarResName = identity.avatarResName
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = identity.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GrayDark
                                    )
                                    if (identity.motto.isNotEmpty()) {
                                        Text(
                                            text = identity.motto,
                                            fontSize = 12.sp,
                                            color = GrayMiddle,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                if (identity.id == activeIdentityId) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "当前",
                                        tint = WeiboOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Divider()
                        }
                    }
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
    val shareIntent = Intent.createChooser(sendIntent, "分享微博")
    try {
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("微博内容", shareText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun EmptyFeed() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "还没有微博",
                fontSize = 16.sp,
                color = GrayMiddle
            )
            Text(
                text = "点击中间+号发布第一条",
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}