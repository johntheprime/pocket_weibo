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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.ui.components.ListPostImageIndicator
import com.pocketweibo.ui.components.SelectableCopyDialog
import com.pocketweibo.ui.components.TopicText
import com.pocketweibo.ui.theme.TodoDone
import com.pocketweibo.ui.theme.TodoPending
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatExactTime
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodoListContent(
    unfinished: List<TodoItem>,
    completed: List<PostWithIdentity>,
    showCompleted: Boolean,
    onToggleCompleted: () -> Unit,
    listState: LazyListState,
    onTopicClick: (String) -> Unit,
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
                color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Text(
                        text = stringResource(R.string.todo_empty_subtitle),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        items(unfinished, key = { "todo-${it.post.id}" }) { item ->
            TodoRow(
                item = item,
                onTopicClick = onTopicClick,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (showCompleted) R.string.todo_done_collapse else R.string.todo_done_expand
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (showCompleted) {
            items(completed, key = { "done-${it.id}" }) { post ->
                CompletedTodoRow(
                    post = post,
                    onTopicClick = onTopicClick,
                    onPostClick = { onPostClick(post.id) },
                    onReopen = { onReopen(post.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodoRow(
    item: TodoItem,
    onTopicClick: (String) -> Unit,
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
            color = MaterialTheme.colorScheme.surface
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
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(text = time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = reasonLabel,
                        fontSize = 11.sp,
                        color = TodoPending,
                        modifier = Modifier.padding(top = 3.dp)
                    )
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
internal fun CompletedTodoRow(
    post: PostWithIdentity,
    onTopicClick: (String) -> Unit,
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
            color = MaterialTheme.colorScheme.surface
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.todo_done_at, doneAt),
                        fontSize = 11.sp,
                        color = TodoDone,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    TopicText(
                        text = body,
                        onTopicClick = onTopicClick,
                        style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

