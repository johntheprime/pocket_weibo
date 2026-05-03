package com.pocketweibo.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pocketweibo.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.ui.components.CommentBottomSheet
import com.pocketweibo.ui.components.PostCard
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange

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
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCategoryDropdown by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            WeiboTitleBar(
                title = stringResource(R.string.title_home),
                showDropdown = true,
                onTitleClick = { showCategoryDropdown = !showCategoryDropdown },
                leftIcon = {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = stringResource(R.string.home_search_cd),
                        tint = WeiboOrange,
                        modifier = Modifier.size(24.dp)
                    )
                },
                rightIcon = {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.home_more_cd),
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
                onSendComment = { content ->
                    viewModel.addComment(selectedPostId!!, content)
                },
                onDeleteComment = { commentId ->
                    viewModel.deleteComment(commentId, selectedPostId!!)
                }
            )
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
                text = stringResource(R.string.empty_feed_title),
                fontSize = 16.sp,
                color = GrayMiddle
            )
            Text(
                text = stringResource(R.string.empty_feed_subtitle),
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
