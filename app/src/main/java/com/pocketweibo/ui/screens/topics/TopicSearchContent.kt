package com.pocketweibo.ui.screens.topics

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.data.local.dao.CommentSearchRow
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.ListPostImageIndicator
import com.pocketweibo.ui.components.SelectableCopyDialog
import com.pocketweibo.ui.components.TopicText
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay

@Composable
internal fun SearchTipsContent() {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.discover_search_tips),
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        item {
            SearchHintItem(
                title = stringResource(R.string.discover_search_identity_title),
                description = stringResource(R.string.discover_search_identity_desc),
                icon = Icons.Default.Person
            )
        }
        item {
            SearchHintItem(
                title = stringResource(R.string.discover_search_content_title),
                description = stringResource(R.string.discover_search_content_desc),
                icon = Icons.Default.Search
            )
        }
        item {
            SearchHintItem(
                title = stringResource(R.string.discover_search_multi_title),
                description = stringResource(R.string.discover_search_multi_desc),
                icon = Icons.Default.Search
            )
        }
        item {
            SearchHintItem(
                title = stringResource(R.string.discover_search_comment_title),
                description = stringResource(R.string.discover_search_comment_desc),
                icon = Icons.Default.ChatBubbleOutline
            )
        }
    }
}

@Composable
internal fun SearchHintItem(title: String, description: String, icon: ImageVector) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = WeiboOrange, modifier = Modifier.size(24.dp))
            Column {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GrayDark)
                Text(text = description, fontSize = 12.sp, color = GrayMiddle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun SearchResultsContent(
    results: List<SearchResult>,
    listState: LazyListState,
    onIdentityClick: (IdentityEntity) -> Unit,
    onOpenTopic: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onPostClick: (Long) -> Unit
) {
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GrayLight,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(R.string.discover_empty_results),
                    fontSize = 16.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        val identityResults = results.filterIsInstance<SearchResult.IdentityResult>()
        val topicResults = results.filterIsInstance<SearchResult.TopicResult>()
        val postResults = results.filterIsInstance<SearchResult.PostResult>()
        val commentResults = results.filterIsInstance<SearchResult.CommentResult>()

        if (identityResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_identity))
            }
            items(identityResults) { result ->
                IdentitySearchItem(identity = result.identity, onClick = { onIdentityClick(result.identity) })
            }
        }
        if (topicResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_topics))
            }
            items(topicResults) { result ->
                TopicCard(topic = result.topic, onClick = { onOpenTopic(result.topic.key) })
            }
        }
        if (postResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_posts))
            }
            items(postResults) { result ->
                PostSearchItem(
                    post = result.post,
                    onTopicClick = onTopicClick,
                    onClick = { onPostClick(result.post.id) }
                )
            }
        }
        if (commentResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_comments))
            }
            items(commentResults) { result ->
                CommentSearchItem(
                    row = result.row,
                    onTopicClick = onTopicClick,
                    onOpenPost = { onPostClick(result.row.postId) }
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = GrayDark,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
internal fun IdentitySearchItem(identity: IdentityEntity, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(
                name = identity.name,
                color = Color(0xFF4A90D9),
                size = 44.dp,
                avatarResName = identity.avatarResName,
                customAvatarUri = identity.customAvatarUri
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = identity.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GrayDark)
                Text(
                    text = stringResource(R.string.identity_search_subtitle),
                    fontSize = 12.sp,
                    color = GrayMiddle
                )
            }
            Text(
                text = stringResource(R.string.discover_identity_view_posts),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeiboOrange
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PostSearchItem(
    post: PostWithIdentity,
    onTopicClick: (String) -> Unit,
    onClick: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val displayName = identityDisplayName(post.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(post.content, post.audioPath, voiceOnlyLabel)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showSelectableCopy = true }),
            color = Color.White
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(
                    name = displayName,
                    color = Color(0xFF4A90D9),
                    size = 36.dp,
                    avatarResName = post.identityAvatarResName,
                    customAvatarUri = post.identityCustomAvatarUri
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        ListPostImageIndicator(imageUris = post.imageUris)
                    }
                    TopicText(
                        text = body,
                        onTopicClick = onTopicClick,
                        style = TextStyle(fontSize = 13.sp, color = GrayDark),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        if (showSelectableCopy) {
            SelectableCopyDialog(body = body, onDismiss = { showSelectableCopy = false }, title = null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CommentSearchItem(
    row: CommentSearchRow,
    onTopicClick: (String) -> Unit,
    onOpenPost: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val openPostCd = stringResource(R.string.discover_comment_open_post_cd)
    val commentAuthor = identityDisplayName(row.commentAuthorName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(row.content, row.audioPath, voiceOnlyLabel)
    val postLine = stringResource(
        R.string.discover_comment_parent_post_line,
        row.postContent.trim().let { if (it.length <= 72) it else it.take(72) + "…" }
    )
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpenPost, onLongClick = { showSelectableCopy = true }),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.padding(16.dp).semantics { contentDescription = openPostCd },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = WeiboOrange,
                    modifier = Modifier.padding(top = 2.dp).size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = commentAuthor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.comment_search_result_subtitle),
                        fontSize = 11.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    TopicText(
                        text = body,
                        onTopicClick = onTopicClick,
                        style = TextStyle(fontSize = 13.sp, color = GrayDark),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = postLine,
                        fontSize = 11.sp,
                        color = GrayMiddle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        if (showSelectableCopy) {
            SelectableCopyDialog(body = body, onDismiss = { showSelectableCopy = false }, title = null)
        }
    }
}
