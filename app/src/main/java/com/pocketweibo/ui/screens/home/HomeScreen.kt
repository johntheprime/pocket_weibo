package com.pocketweibo.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.ui.components.CommentBottomSheet
import com.pocketweibo.ui.components.PostCard
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onPostClick: (Long) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onNavigateToDiscover: () -> Unit = {},
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
    val scope = rememberCoroutineScope()

    var showTitleMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchDraft by remember { mutableStateOf("") }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                delay(450L)
                isRefreshing = false
            }
        }
    )

    val filteredPosts = remember(posts, searchQuery) {
        if (searchQuery.isBlank()) posts
        else posts.filter { p ->
            p.content.contains(searchQuery, ignoreCase = true) ||
                p.identityName.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
        ) {
            WeiboTitleBar(
                title = stringResource(R.string.title_home),
                showDropdown = true,
                onTitleClick = { showTitleMenu = true },
                leftIcon = {
                    IconButton(onClick = {
                        searchDraft = searchQuery
                        showSearchDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = stringResource(R.string.home_search_cd),
                            tint = WeiboOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                rightIcon = {
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.home_more_cd),
                                tint = WeiboOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_more_settings)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_more_discover)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onNavigateToDiscover()
                                }
                            )
                        }
                    }
                }
            )

            Divider(thickness = 0.5.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                    posts.isEmpty() -> EmptyFeed()
                    filteredPosts.isEmpty() -> EmptySearch()
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 8.dp)
                        ) {
                            items(filteredPosts, key = { it.id }) { post ->
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
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = WeiboOrange
                )
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

    if (showTitleMenu) {
        AlertDialog(
            onDismissRequest = { showTitleMenu = false },
            title = { Text(stringResource(R.string.home_title_menu_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTitleMenu = false
                        isRefreshing = true
                        scope.launch {
                            delay(450L)
                            isRefreshing = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.home_title_menu_refresh))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleMenu = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text(stringResource(R.string.home_search_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = searchDraft,
                    onValueChange = { searchDraft = it },
                    placeholder = { Text(stringResource(R.string.home_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        searchQuery = searchDraft.trim()
                        showSearchDialog = false
                    }
                ) {
                    Text(stringResource(R.string.home_search_apply))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        searchQuery = ""
                        searchDraft = ""
                        showSearchDialog = false
                    }
                ) {
                    Text(stringResource(R.string.home_search_clear))
                }
            }
        )
    }
}

@Composable
private fun EmptySearch() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.home_empty_search),
            fontSize = 16.sp,
            color = GrayMiddle
        )
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
