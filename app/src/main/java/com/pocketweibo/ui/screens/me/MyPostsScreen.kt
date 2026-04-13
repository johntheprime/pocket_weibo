package com.pocketweibo.ui.screens.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    onBack: () -> Unit,
    onPostClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: MyPostsViewModel = viewModel(
        factory = MyPostsViewModel.Factory(app.repository)
    )
    
    val identities by viewModel.identities.collectAsState()
    val selectedIdentityId by viewModel.selectedIdentityId.collectAsState()
    val filteredPosts by viewModel.filteredPosts.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val showCommentSheet by viewModel.showCommentSheet.collectAsState()
    val selectedPostId by viewModel.selectedPostId.collectAsState()
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = "我的发布",
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
        
        IdentityFilterRow(
            identities = identities,
            selectedIdentityId = selectedIdentityId,
            onIdentitySelected = { viewModel.selectIdentity(it) }
        )
        
        Divider(thickness = 0.5.dp)

        if (filteredPosts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "该身份暂无发布内容",
                        fontSize = 16.sp,
                        color = GrayMiddle
                    )
                    Text(
                        text = "切换身份或发布新内容",
                        fontSize = 14.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onLikeClick = { viewModel.toggleLike(post.id) },
                        onCommentClick = { viewModel.openComments(post.id) },
                        onShareClick = { sharePost(context, post.identityName, post.content) },
                        onPostClick = { onPostClick(post.id) }
                    )
                    Divider(thickness = 6.dp, color = Background)
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
            onSendComment = { content ->
                viewModel.addComment(selectedPostId!!, content)
            },
            onDeleteComment = { commentId ->
                viewModel.deleteComment(commentId, selectedPostId!!)
            }
        )
    }
}

@Composable
private fun IdentityFilterRow(
    identities: List<IdentityEntity>,
    selectedIdentityId: Long?,
    onIdentitySelected: (Long?) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IdentityFilterChip(
                label = "全部",
                isSelected = selectedIdentityId == null,
                color = WeiboOrange,
                onClick = { onIdentitySelected(null) }
            )
            
            identities.forEach { identity ->
                IdentityFilterChip(
                    label = identity.name,
                    isSelected = selectedIdentityId == identity.id,
                    color = Color(0xFF4A90D9),
                    onClick = { onIdentitySelected(identity.id) }
                )
            }
        }
    }
}

@Composable
private fun IdentityFilterChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, color, CircleShape)
                } else {
                    Modifier
                }
            ),
        shape = CircleShape,
        color = if (isSelected) color else GrayLight
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else GrayDark,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

private fun sharePost(context: Context, authorName: String, content: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("微博内容", "$authorName: $content")
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
