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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.CommentBottomSheet
import com.pocketweibo.ui.components.PostCard
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
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
    onOpenMyPosts: () -> Unit = {},
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.repository)
    )

    val posts by app.repository.allPosts.collectAsState(initial = emptyList())
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val comments by viewModel.comments.collectAsState()
    val showCommentSheet by viewModel.showCommentSheet.collectAsState()
    val selectedPostId by viewModel.selectedPostId.collectAsState()
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)

    val commentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleQuickAccessSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    val postsByIdentity = remember(posts) {
        posts.groupBy { it.identityId }.mapValues { (_, list) ->
            list.sortedByDescending { it.createdAt }
        }
    }
    val sortedIdentitiesForSheet = remember(identities, activeIdentity?.id) {
        val aid = activeIdentity?.id
        identities.sortedWith(
            compareBy<IdentityEntity> { e ->
                if (aid != null && e.id == aid) 0 else 1
            }.thenBy { it.name }
        )
    }

    var showTitleQuickAccessSheet by remember { mutableStateOf(false) }
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
                onTitleClick = { showTitleQuickAccessSheet = true },
                titleDropdownContentDescription = stringResource(R.string.home_title_open_menu_cd),
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
                            state = listState,
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
                sheetState = commentSheetState,
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

    if (showTitleQuickAccessSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTitleQuickAccessSheet = false },
            sheetState = titleQuickAccessSheetState
        ) {
            HomeTitleQuickAccessSheet(
                sortedIdentities = sortedIdentitiesForSheet,
                postsByIdentity = postsByIdentity,
                activeIdentityId = activeIdentity?.id,
                onMyPosts = {
                    showTitleQuickAccessSheet = false
                    onOpenMyPosts()
                },
                onPickIdentity = { id ->
                    scope.launch {
                        app.repository.setActiveIdentity(id)
                        showTitleQuickAccessSheet = false
                    }
                },
                onOpenPost = { postId ->
                    showTitleQuickAccessSheet = false
                    onPostClick(postId)
                }
            )
        }
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
private fun HomeTitleQuickAccessSheet(
    sortedIdentities: List<IdentityEntity>,
    postsByIdentity: Map<Long, List<PostWithIdentity>>,
    activeIdentityId: Long?,
    onMyPosts: () -> Unit,
    onPickIdentity: (Long) -> Unit,
    onOpenPost: (Long) -> Unit
) {
    val myPostsRowCd = stringResource(R.string.home_title_sheet_my_posts_row_cd)
    val postOpenCd = stringResource(R.string.home_title_sheet_post_open_cd)
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        item {
            Text(
                text = stringResource(R.string.home_title_sheet_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WeiboOrange.copy(alpha = 0.09f),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .semantics { contentDescription = myPostsRowCd }
                    .clickable(onClick = onMyPosts)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.title_my_posts),
                            color = WeiboOrange,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.home_title_sheet_my_posts_subtitle),
                            color = GrayMiddle,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GrayMiddle
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_title_sheet_section_identities),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GrayDark,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(sortedIdentities, key = { it.id }) { identity ->
            val identityRowCd = stringResource(R.string.home_title_sheet_identity_row_cd, identity.name)
            val list = postsByIdentity[identity.id].orEmpty()
            val top = list.take(3)
            val more = (list.size - 3).coerceAtLeast(0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = identityRowCd }
                        .clickable { onPickIdentity(identity.id) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        name = identity.name,
                        color = Color(0xFF4A90D9),
                        size = 36.dp,
                        avatarResName = identity.avatarResName
                    )
                    Row(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = identity.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GrayDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeIdentityId != null && identity.id == activeIdentityId) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = WeiboOrange.copy(alpha = 0.15f),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.home_title_sheet_badge_active),
                                    fontSize = 10.sp,
                                    color = WeiboOrange,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                if (top.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_title_sheet_empty_identity_posts),
                        fontSize = 12.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(start = 46.dp, top = 8.dp)
                    )
                } else {
                    top.forEach { post ->
                        Text(
                            text = post.content,
                            fontSize = 13.sp,
                            color = GrayDark,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 46.dp, top = 8.dp)
                                .fillMaxWidth()
                                .semantics { contentDescription = postOpenCd }
                                .clickable { onOpenPost(post.id) }
                        )
                    }
                }
                if (more > 0) {
                    Text(
                        text = stringResource(R.string.home_title_sheet_more_posts, more),
                        fontSize = 12.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(start = 46.dp, top = 6.dp)
                    )
                }
                Divider(modifier = Modifier.padding(top = 12.dp))
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
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
