package com.pocketweibo.ui.screens.topics

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.ListPostImageIndicator
import com.pocketweibo.ui.components.SelectableCopyDialog
import com.pocketweibo.ui.components.TopicText
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.extractTopicTags
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.stripTopicPrefix
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay
import java.util.Calendar

enum class TopicsSection { TOPICS, TODO }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TopicsScreen(
    selectedTopicKey: String?,
    showGlobalSearch: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    topicQuery: String,
    onTopicQueryChange: (String) -> Unit,
    section: TopicsSection,
    onSectionChange: (TopicsSection) -> Unit,
    showCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onCloseTopic: () -> Unit,
    onTopicClick: (String) -> Unit,
    onPostClick: (Long) -> Unit,
    onDiscussAgain: (key: String, name: String) -> Unit,
    topicListState: LazyListState,
    topicTimelineListState: LazyListState,
    todoListState: LazyListState,
    searchListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: TopicsViewModel = viewModel(
        factory = TopicsViewModel.Factory(app.repository)
    )

    val topics by viewModel.topics.collectAsState()
    val allPosts by viewModel.allPosts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val unfinishedTodos by viewModel.unfinishedTodos.collectAsState()
    val completedTodos by viewModel.completedTodos.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val globalSearchFocusRequester = remember { FocusRequester() }

    // Search text lives in the ViewModel (Activity-scoped) so it survives opening a post.
    // Clear it only when search is deliberately closed, then focus the field as soon as it opens.
    LaunchedEffect(showGlobalSearch) {
        if (showGlobalSearch) {
            withFrameNanos { }
            globalSearchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            viewModel.updateSearchQuery("")
        }
    }

    val selectedTopic = selectedTopicKey?.let { key -> topics.find { it.key == key } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            selectedTopic != null -> {
                WeiboTitleBar(
                    title = "#${selectedTopic.displayName}",
                    leftIcon = {
                        IconButton(onClick = onCloseTopic) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
                TopicDetailContent(
                    topic = selectedTopic,
                    posts = allPosts,
                    listState = topicTimelineListState,
                    onTopicClick = onTopicClick,
                    onPostClick = onPostClick,
                    onDiscussAgain = { onDiscussAgain(selectedTopic.key, selectedTopic.displayName) }
                )
            }

            showGlobalSearch -> {
                WeiboTitleBar(
                    title = stringResource(R.string.title_search),
                    leftIcon = {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            onCloseSearch()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.discover_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.discover_clear_search_cd),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(globalSearchFocusRequester)
                )
                if (searchQuery.isEmpty()) {
                    SearchTipsContent()
                } else {
                    SearchResultsContent(
                        results = searchResults,
                        listState = searchListState,
                        onIdentityClick = { identity ->
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.updateSearchQuery(identity.name)
                        },
                        onOpenTopic = onOpenTopic,
                        onTopicClick = onTopicClick,
                        onPostClick = onPostClick
                    )
                }
            }

            else -> {
                WeiboTitleBar(
                    title = stringResource(R.string.title_topics),
                    centerBelowTitle = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = section == TopicsSection.TOPICS,
                                onClick = { onSectionChange(TopicsSection.TOPICS) },
                                label = { Text(stringResource(R.string.topics_section_topics)) }
                            )
                            FilterChip(
                                selected = section == TopicsSection.TODO,
                                onClick = { onSectionChange(TopicsSection.TODO) },
                                label = { Text(stringResource(R.string.topics_section_todo)) }
                            )
                        }
                    },
                    rightIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search_cd),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onRightIconClick = onOpenSearch
                )
                if (section == TopicsSection.TOPICS) {
                    OutlinedTextField(
                        value = topicQuery,
                        onValueChange = onTopicQueryChange,
                        placeholder = { Text(stringResource(R.string.topics_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (topicQuery.isNotEmpty()) {
                                IconButton(onClick = { onTopicQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.discover_clear_search_cd),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    )
                    TopicListContent(
                        topics = topics,
                        query = topicQuery,
                        listState = topicListState,
                        onTopicClick = { topic -> onOpenTopic(topic.key) }
                    )
                } else {
                    TodoListContent(
                        unfinished = unfinishedTodos,
                        completed = completedTodos,
                        showCompleted = showCompleted,
                        onToggleCompleted = onToggleCompleted,
                        listState = todoListState,
                        onTopicClick = onTopicClick,
                        onPostClick = onPostClick,
                        onMarkDone = viewModel::markTodoDone,
                        onReopen = viewModel::reopenTodo
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicListContent(
    topics: List<TopicItem>,
    query: String,
    listState: LazyListState,
    onTopicClick: (TopicItem) -> Unit
) {
    val filtered = remember(topics, query) {
        val q = stripTopicPrefix(query)
        if (q.isEmpty()) topics else topics.filter { it.displayName.contains(q, ignoreCase = true) }
    }
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.topics_empty_title else R.string.discover_empty_results
                    ),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (query.isBlank()) {
                    Text(
                        text = stringResource(R.string.topics_empty_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
        return
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(filtered) { topic ->
            TopicCard(topic = topic, onClick = { onTopicClick(topic) })
        }
    }
}

@Composable
internal fun TopicCard(topic: TopicItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val lastActivity = context.resources.formatRelativeTime(
        topic.lastAt,
        RelativeTimePreset.FeedCard
    )
    val years = yearOf(topic.lastAt) - yearOf(topic.firstAt) + 1
    val cardCd = stringResource(
        R.string.topics_topic_cd,
        topic.displayName,
        topic.postCount,
        years
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = cardCd },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(WeiboOrange.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "#", color = WeiboOrange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${topic.displayName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.topics_topic_meta,
                        topic.postCount,
                        topic.identityCount,
                        lastActivity
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (years > 1) {
                    Text(
                        text = stringResource(R.string.topics_topic_span, years),
                        fontSize = 11.sp,
                        color = WeiboOrange,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun yearOf(timestamp: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

private sealed class TimelineRow {
    data class YearHeader(val year: Int, val count: Int) : TimelineRow()
    data class Post(val post: PostWithIdentity) : TimelineRow()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicDetailContent(
    topic: TopicItem,
    posts: List<PostWithIdentity>,
    listState: LazyListState,
    onTopicClick: (String) -> Unit,
    onPostClick: (Long) -> Unit,
    onDiscussAgain: () -> Unit
) {
    val entries = remember(topic.key, posts) {
        posts.filter { post ->
            extractTopicTags(post.content).any { it.key == topic.key }
        }.sortedBy { it.createdAt }
    }
    val rows = remember(entries) {
        val result = mutableListOf<TimelineRow>()
        var currentYear = -1
        var yearCount = 0
        for (post in entries) {
            val year = yearOf(post.createdAt)
            if (year != currentYear) {
                if (currentYear != -1) {
                    val index = result.indexOfLast { it is TimelineRow.YearHeader }
                    if (index >= 0) {
                        result[index] = TimelineRow.YearHeader(currentYear, yearCount)
                    }
                }
                currentYear = year
                yearCount = 0
                result.add(TimelineRow.YearHeader(year, 0))
            }
            yearCount += 1
            result.add(TimelineRow.Post(post))
        }
        if (currentYear != -1) {
            val index = result.indexOfLast { it is TimelineRow.YearHeader }
            if (index >= 0) {
                result[index] = TimelineRow.YearHeader(currentYear, yearCount)
            }
        }
        result
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.topics_detail_summary, topic.postCount, topic.identityCount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.clickable(onClick = onDiscussAgain),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = WeiboOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.topics_discuss_again),
                        fontSize = 13.sp,
                        color = WeiboOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Divider()
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.topics_detail_empty),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                rows.forEach { row ->
                    when (row) {
                        is TimelineRow.YearHeader -> stickyHeader(key = "year-${row.year}") {
                            Text(
                                text = stringResource(
                                    R.string.topics_year_header,
                                    row.year,
                                    row.count
                                ),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        is TimelineRow.Post -> item(key = "post-${row.post.id}") {
                            TopicPostItem(
                                post = row.post,
                                onTopicClick = onTopicClick,
                                onClick = { onPostClick(row.post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicPostItem(
    post: PostWithIdentity,
    onTopicClick: (String) -> Unit,
    onClick: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val displayName = identityDisplayName(post.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(post.content, post.audioPath, voiceOnlyLabel)
    val context = LocalContext.current
    val time = context.resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { showSelectableCopy = true }),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = displayName,
                    color = Color(0xFF4A90D9),
                    size = 36.dp,
                    avatarResName = post.identityAvatarResName,
                    customAvatarUri = post.identityCustomAvatarUri
                )
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TopicText(
                        text = body,
                        onTopicClick = onTopicClick,
                        style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.count_comments, post.commentCount),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ListPostImageIndicator(imageUris = post.imageUris)
                    }
                }
            }
        }
        if (showSelectableCopy) {
            SelectableCopyDialog(
                body = body,
                onDismiss = { showSelectableCopy = false },
                title = null
            )
        }
    }
}

