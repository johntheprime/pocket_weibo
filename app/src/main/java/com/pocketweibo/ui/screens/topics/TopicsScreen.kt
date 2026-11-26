package com.pocketweibo.ui.screens.topics

import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import com.pocketweibo.ui.components.HighlightText
import com.pocketweibo.ui.components.ListPostImageIndicator
import com.pocketweibo.ui.components.SelectableCopyDialog
import com.pocketweibo.ui.components.TopicText
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay
import com.pocketweibo.ui.util.resolveTopicKey
import com.pocketweibo.ui.util.stripTopicPrefix
import com.pocketweibo.ui.util.topicKeysForPost
import java.util.Calendar

enum class TopicsSection { TOPICS, TODO }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TopicsScreen(
    selectedTopicKey: String?,
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
    onOpenManage: () -> Unit,
    topicListState: LazyListState,
    topicTimelineListState: LazyListState,
    todoListState: LazyListState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: TopicsViewModel = viewModel(
        factory = TopicsViewModel.Factory(app.repository)
    )

    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val unfinishedTodos by viewModel.unfinishedTodos.collectAsStateWithLifecycle()
    val completedTodos by viewModel.completedTodos.collectAsStateWithLifecycle()
    val topicAliases by viewModel.topicAliases.collectAsStateWithLifecycle()
    val topicExclusions by viewModel.topicExclusions.collectAsStateWithLifecycle()
    // Only the topic-detail menu opens a merge dialog; list cards use the central Manage screen.
    var mergeSource by remember { mutableStateOf<TopicItem?>(null) }
    var removalMode by remember { mutableStateOf(false) }
    var postsToRemove by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(selectedTopicKey) {
        removalMode = false
        postsToRemove = emptySet()
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val selectedTopic = selectedTopicKey?.let { key ->
        val resolved = resolveTopicKey(key, topicAliases)
        topics.find { it.key == resolved }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            selectedTopic != null -> {
                val mergedSourceKeys = topicAliases
                    .filterValues { resolveTopicKey(it, topicAliases) == selectedTopic.key }
                    .keys
                    .sorted()
                val excludedPostIds = topicExclusions
                    .filter { it.first == selectedTopic.key }
                    .map { it.second }
                    .toSet()
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
                    },
                    rightIcon = {
                        TopicDetailMenu(
                            mergedCount = mergedSourceKeys.size,
                            excludedCount = excludedPostIds.size,
                            onMerge = { mergeSource = selectedTopic },
                            onUnmerge = { viewModel.unmergeTopicAliases(listOf(selectedTopic.key)) },
                            onRestoreExcluded = {
                                viewModel.restoreExcludedPosts(listOf(selectedTopic.key))
                            },
                            onEnterRemoveMode = {
                                postsToRemove = emptySet()
                                removalMode = true
                            }
                        )
                    }
                )
                if (removalMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.topics_remove_mode_hint),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    R.string.topics_remove_mode_selected,
                                    postsToRemove.size
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = {
                                removalMode = false
                                postsToRemove = emptySet()
                            }) {
                                Text(stringResource(R.string.cancel), fontSize = 12.sp)
                            }
                            TextButton(
                                enabled = postsToRemove.isNotEmpty(),
                                onClick = {
                                    viewModel.excludePostsFromTopic(
                                        selectedTopic.key,
                                        postsToRemove.toList()
                                    )
                                    removalMode = false
                                    postsToRemove = emptySet()
                                }
                            ) {
                                Text(
                                    stringResource(R.string.topics_remove_mode_confirm),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                TopicDetailContent(
                    topic = selectedTopic,
                    posts = allPosts,
                    aliases = topicAliases,
                    excludedPostIds = excludedPostIds,
                    mergedSourceKeys = mergedSourceKeys,
                    listState = topicTimelineListState,
                    removalMode = removalMode,
                    selectedPostIds = postsToRemove,
                    onTopicClick = onTopicClick,
                    onPostClick = onPostClick,
                    onTogglePostSelection = { postId ->
                        postsToRemove = if (postId in postsToRemove) {
                            postsToRemove - postId
                        } else {
                            postsToRemove + postId
                        }
                    },
                    onDiscussAgain = { onDiscussAgain(selectedTopic.key, selectedTopic.displayName) }
                )
            }

            else -> {
                TopicsHeader(
                    section = section,
                    topicCount = topics.size,
                    todoCount = unfinishedTodos.size,
                    onSectionChange = onSectionChange
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onOpenManage,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = stringResource(R.string.topics_manage),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (topicQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onTopicQueryChange("") },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.discover_clear_search_cd),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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

    mergeSource?.let { source ->
        MergeTopicDialog(
            sources = listOf(source),
            topics = topics,
            onDismiss = { mergeSource = null },
            onMerge = { target ->
                viewModel.mergeTopics(listOf(source.key), target.key)
                mergeSource = null
                onOpenTopic(target.key)
            }
        )
    }
}

@Composable
private fun TopicsHeader(
    section: TopicsSection,
    topicCount: Int,
    todoCount: Int,
    onSectionChange: (TopicsSection) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopicSectionTab(
                label = stringResource(R.string.topics_section_topics, topicCount),
                selected = section == TopicsSection.TOPICS,
                onClick = { onSectionChange(TopicsSection.TOPICS) },
                modifier = Modifier.weight(1f)
            )
            TopicSectionTab(
                label = stringResource(R.string.topics_section_todo, todoCount),
                selected = section == TopicsSection.TODO,
                onClick = { onSectionChange(TopicsSection.TODO) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TopicSectionTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) WeiboOrange else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) WeiboOrange else Color.Transparent,
                    RoundedCornerShape(1.dp)
                )
        )
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
            TopicCard(
                topic = topic,
                onClick = { onTopicClick(topic) }
            )
        }
    }
}

@Composable
internal fun TopicCard(
    topic: TopicItem,
    onClick: () -> Unit,
    highlightKeywords: List<String> = emptyList()
) {
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
                HighlightText(
                    text = topic.displayName,
                    keywords = highlightKeywords,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
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
    aliases: Map<String, String>,
    excludedPostIds: Set<Long>,
    mergedSourceKeys: List<String>,
    listState: LazyListState,
    removalMode: Boolean,
    selectedPostIds: Set<Long>,
    onTopicClick: (String) -> Unit,
    onPostClick: (Long) -> Unit,
    onTogglePostSelection: (Long) -> Unit,
    onDiscussAgain: () -> Unit
) {
    val entries = remember(topic.key, posts, aliases, excludedPostIds) {
        posts.filter { post ->
            post.id !in excludedPostIds && topic.key in topicKeysForPost(post.content, aliases)
        }.sortedByDescending { it.createdAt }
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
                if (mergedSourceKeys.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.topics_detail_merged,
                            mergedSourceKeys.joinToString("、") { "#$it" }
                        ),
                        fontSize = 11.sp,
                        color = WeiboOrange,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
                                removalMode = removalMode,
                                selected = row.post.id in selectedPostIds,
                                onTopicClick = onTopicClick,
                                onClick = { onPostClick(row.post.id) },
                                onToggleSelection = { onTogglePostSelection(row.post.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicDetailMenu(
    mergedCount: Int,
    excludedCount: Int,
    onMerge: () -> Unit,
    onUnmerge: () -> Unit,
    onRestoreExcluded: () -> Unit,
    onEnterRemoveMode: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.topics_detail_more_cd),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.topics_remove_posts_mode)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEnterRemoveMode()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.topics_merge_into)) },
                leadingIcon = { Icon(Icons.Default.CallMerge, contentDescription = null) },
                onClick = {
                    expanded = false
                    onMerge()
                }
            )
            if (mergedCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.topics_unmerge_action, mergedCount))
                    },
                    leadingIcon = { Icon(Icons.Default.CallSplit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onUnmerge()
                    }
                )
            }
            if (excludedCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.topics_restore_excluded_action, excludedCount))
                    },
                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onRestoreExcluded()
                    }
                )
            }
        }
    }
}

@Composable
internal fun MergeTopicDialog(
    sources: List<TopicItem>,
    topics: List<TopicItem>,
    onDismiss: () -> Unit,
    onMerge: (TopicItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val sourceKeys = remember(sources) { sources.map { it.key }.toSet() }
    val candidates = remember(topics, sourceKeys, query) {
        val q = stripTopicPrefix(query)
        topics.filter { topic ->
            topic.key !in sourceKeys &&
                (q.isEmpty() || topic.displayName.contains(q, ignoreCase = true))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (sources.size == 1) {
                    stringResource(R.string.topics_merge_dialog_title, sources.first().displayName)
                } else {
                    stringResource(R.string.topics_merge_dialog_title_multi, sources.size)
                }
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (sources.size == 1) R.string.topics_merge_dialog_hint
                        else R.string.topics_merge_dialog_hint_multi
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.topics_merge_dialog_search),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.topics_merge_no_targets),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.key }) { target ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMerge(target) },
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${target.displayName}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.topics_merge_target_count,
                                            target.postCount
                                        ),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicPostItem(
    post: PostWithIdentity,
    removalMode: Boolean,
    selected: Boolean,
    onTopicClick: (String) -> Unit,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val displayName = identityDisplayName(post.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val body = postOrCommentBodyForDisplay(post.content, post.audioPath, voiceOnlyLabel)
    val context = LocalContext.current
    val time = context.resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard)
    val rowClick = if (removalMode) onToggleSelection else onClick
    val rowLongClick: (() -> Unit)? = if (removalMode) null else ({ showSelectableCopy = true })
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = rowClick, onLongClick = rowLongClick),
            color = if (removalMode && selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
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
                        onTopicClick = if (removalMode) { { onToggleSelection() } } else onTopicClick,
                        onClick = rowClick,
                        onLongClick = rowLongClick,
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
                        if (removalMode && selected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = stringResource(R.string.topics_post_selected_cd),
                                tint = WeiboOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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

