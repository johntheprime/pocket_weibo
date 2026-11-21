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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.dao.CommentSearchRow
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.ListPostImageIndicator
import com.pocketweibo.ui.components.SelectableCopyDialog
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.TodoDone
import com.pocketweibo.ui.theme.TodoPending
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.extractTopicTags
import com.pocketweibo.ui.util.formatExactTime
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay
import java.util.Calendar

private enum class TopicsSection { TOPICS, TODO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    selectedTopicKey: String?,
    onOpenTopic: (String) -> Unit,
    onCloseTopic: () -> Unit,
    onPostClick: (Long) -> Unit,
    onDiscussAgain: (String) -> Unit,
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

    var topicQuery by remember { mutableStateOf("") }
    var showGlobalSearch by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(TopicsSection.TOPICS) }
    var showCompleted by remember { mutableStateOf(false) }

    val selectedTopic = selectedTopicKey?.let { key -> topics.find { it.key == key } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
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
                                tint = GrayDark
                            )
                        }
                    }
                )
                TopicDetailContent(
                    topic = selectedTopic,
                    posts = allPosts,
                    listState = topicTimelineListState,
                    onPostClick = onPostClick,
                    onDiscussAgain = { onDiscussAgain(selectedTopic.displayName) }
                )
            }

            showGlobalSearch -> {
                WeiboTitleBar(
                    title = stringResource(R.string.title_search),
                    leftIcon = {
                        IconButton(onClick = {
                            viewModel.updateSearchQuery("")
                            focusManager.clearFocus()
                            showGlobalSearch = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd),
                                tint = GrayDark
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.discover_search_hint), color = GrayMiddle) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GrayMiddle
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.discover_clear_search_cd),
                                    tint = GrayMiddle
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
                if (searchQuery.isEmpty()) {
                    SearchTipsContent()
                } else {
                    SearchResultsContent(
                        results = searchResults,
                        listState = searchListState,
                        onIdentityClick = { identity -> viewModel.updateSearchQuery(identity.name) },
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
                                onClick = { section = TopicsSection.TOPICS },
                                label = { Text(stringResource(R.string.topics_section_topics)) }
                            )
                            FilterChip(
                                selected = section == TopicsSection.TODO,
                                onClick = { section = TopicsSection.TODO },
                                label = { Text(stringResource(R.string.topics_section_todo)) }
                            )
                        }
                    },
                    rightIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.home_search_cd),
                            tint = GrayDark
                        )
                    },
                    onRightIconClick = { showGlobalSearch = true }
                )
                if (section == TopicsSection.TOPICS) {
                    OutlinedTextField(
                        value = topicQuery,
                        onValueChange = { topicQuery = it },
                        placeholder = { Text(stringResource(R.string.topics_search_hint), color = GrayMiddle) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = GrayMiddle
                            )
                        },
                        trailingIcon = {
                            if (topicQuery.isNotEmpty()) {
                                IconButton(onClick = { topicQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.discover_clear_search_cd),
                                        tint = GrayMiddle
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
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
                        onToggleCompleted = { showCompleted = !showCompleted },
                        listState = todoListState,
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
        val q = query.trim().removePrefix("#")
        if (q.isEmpty()) topics else topics.filter { it.displayName.contains(q, ignoreCase = true) }
    }
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GrayLight,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = stringResource(
                        if (query.isBlank()) R.string.topics_empty_title else R.string.discover_empty_results
                    ),
                    fontSize = 16.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (query.isBlank()) {
                    Text(
                        text = stringResource(R.string.topics_empty_subtitle),
                        fontSize = 12.sp,
                        color = GrayMiddle,
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
private fun TopicCard(topic: TopicItem, onClick: () -> Unit) {
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
        color = Color.White
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
                    color = GrayDark,
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
                    color = GrayMiddle,
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
        Surface(modifier = Modifier.fillMaxWidth(), color = Color.White) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.topics_detail_summary, topic.postCount, topic.identityCount),
                    fontSize = 12.sp,
                    color = GrayMiddle
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
                    color = GrayMiddle
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
                                color = GrayDark,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TopicHeaderBackground)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        is TimelineRow.Post -> item(key = "post-${row.post.id}") {
                            TopicPostItem(post = row.post, onClick = { onPostClick(row.post.id) })
                        }
                    }
                }
            }
        }
    }
}

private val TopicHeaderBackground = Color(0xFFEFEFEF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicPostItem(post: PostWithIdentity, onClick: () -> Unit) {
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
            color = Color.White
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
                            color = GrayDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = time, fontSize = 11.sp, color = GrayMiddle)
                    }
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = GrayDark,
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
                            color = GrayMiddle
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoListContent(
    unfinished: List<TodoItem>,
    completed: List<PostWithIdentity>,
    showCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    listState: LazyListState,
    onPostClick: (Long) -> Unit,
    onMarkDone: (Long) -> Unit,
    onReopen: (Long) -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "todo-header") {
            Text(
                text = stringResource(R.string.todo_unfinished_title, unfinished.size),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (unfinished.isEmpty()) {
            item(key = "todo-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TodoDone,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = stringResource(R.string.todo_empty_title),
                        fontSize = 15.sp,
                        color = GrayDark,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = stringResource(R.string.todo_empty_subtitle),
                        fontSize = 12.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        items(unfinished, key = { "todo-${it.post.id}" }) { item ->
            TodoRow(
                item = item,
                onPostClick = { onPostClick(item.post.id) },
                onMarkDone = { onMarkDone(item.post.id) }
            )
        }
        item(key = "todo-done-divider") {
            Divider(modifier = Modifier.padding(top = 12.dp))
        }
        item(key = "todo-done-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleCompleted)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.todo_done_title, completed.size),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (showCompleted) R.string.todo_done_collapse else R.string.todo_done_expand
                    ),
                    fontSize = 12.sp,
                    color = GrayMiddle
                )
            }
        }
        if (showCompleted) {
            items(completed, key = { "done-${it.id}" }) { post ->
                CompletedTodoRow(
                    post = post,
                    onPostClick = { onPostClick(post.id) },
                    onReopen = { onReopen(post.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoRow(
    item: TodoItem,
    onPostClick: () -> Unit,
    onMarkDone: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val post = item.post
    val context = LocalContext.current
    val displayName = identityDisplayName(post.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(post.content, post.audioPath, voiceOnlyLabel)
    val time = context.resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard)
    val reasonLabel = stringResource(
        when (item.reason) {
            TodoReason.MARKER -> R.string.todo_reason_marker
            TodoReason.REMINDER -> R.string.todo_reason_reminder
            TodoReason.BOTH -> R.string.todo_reason_both
        }
    )
    val doneCd = stringResource(R.string.todo_mark_done_cd)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .combinedClickable(onClick = onPostClick, onLongClick = { showSelectableCopy = true }),
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .background(TodoPending, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = time, fontSize = 11.sp, color = GrayMiddle)
                    }
                    Text(
                        text = reasonLabel,
                        fontSize = 11.sp,
                        color = TodoPending,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = GrayDark,
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
                            color = GrayMiddle
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        ListPostImageIndicator(imageUris = post.imageUris)
                    }
                }
                IconButton(
                    onClick = onMarkDone,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = doneCd }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TodoDone,
                        modifier = Modifier.size(22.dp)
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
private fun CompletedTodoRow(
    post: PostWithIdentity,
    onPostClick: () -> Unit,
    onReopen: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val displayName = identityDisplayName(post.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(post.content, post.audioPath, voiceOnlyLabel)
    val doneAt = post.todoDoneAt?.let { context.resources.formatExactTime(it) }.orEmpty()
    val reopenCd = stringResource(R.string.todo_reopen_cd)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .combinedClickable(onClick = onPostClick, onLongClick = { showSelectableCopy = true }),
            shape = RoundedCornerShape(10.dp),
            color = Color.White
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .background(TodoDone, CircleShape)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayMiddle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.todo_done_at, doneAt),
                        fontSize = 11.sp,
                        color = TodoDone,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = GrayMiddle,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                IconButton(
                    onClick = onReopen,
                    modifier = Modifier
                        .size(36.dp)
                        .semantics { contentDescription = reopenCd }
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = GrayMiddle,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        if (showSelectableCopy) {
            SelectableCopyDialog(body = body, onDismiss = { showSelectableCopy = false }, title = null)
        }
    }
}

@Composable
private fun SearchTipsContent() {
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
private fun SearchHintItem(title: String, description: String, icon: ImageVector) {
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
private fun SearchResultsContent(
    results: List<SearchResult>,
    listState: LazyListState,
    onIdentityClick: (IdentityEntity) -> Unit,
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
        if (postResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_posts))
            }
            items(postResults) { result ->
                PostSearchItem(post = result.post, onClick = { onPostClick(result.post.id) })
            }
        }
        if (commentResults.isNotEmpty()) {
            item {
                SectionTitle(text = stringResource(R.string.discover_section_comments))
            }
            items(commentResults) { result ->
                CommentSearchItem(row = result.row, onOpenPost = { onPostClick(result.row.postId) })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = GrayDark,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun IdentitySearchItem(identity: IdentityEntity, onClick: () -> Unit) {
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
            Column {
                Text(text = identity.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GrayDark)
                Text(
                    text = stringResource(R.string.identity_search_subtitle),
                    fontSize = 12.sp,
                    color = GrayMiddle
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostSearchItem(post: PostWithIdentity, onClick: () -> Unit) {
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
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = GrayDark,
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
private fun CommentSearchItem(row: CommentSearchRow, onOpenPost: () -> Unit) {
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
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = GrayDark,
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
