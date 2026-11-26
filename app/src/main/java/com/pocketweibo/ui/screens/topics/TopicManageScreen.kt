package com.pocketweibo.ui.screens.topics

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.resolveTopicKey

/**
 * One place for all topic housekeeping. Several topics can be selected at once;
 * merge, split and restore actions then apply to the whole selection.
 */
@Composable
fun TopicManageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: TopicsViewModel = viewModel(
        factory = TopicsViewModel.Factory(app.repository)
    )

    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val aliases by viewModel.topicAliases.collectAsStateWithLifecycle()
    val exclusions by viewModel.topicExclusions.collectAsStateWithLifecycle()
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    var mergeSources by remember { mutableStateOf<List<TopicItem>?>(null) }

    val allSelected = topics.isNotEmpty() && selectedKeys.containsAll(topics.map { it.key })
    val mergedSourceKeys = aliases.keys
        .filter { resolveTopicKey(it, aliases) in selectedKeys }
        .sorted()
    val excludedPostCount = exclusions.count { it.first in selectedKeys }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        WeiboTitleBar(
            title = stringResource(R.string.topics_manage_title),
            leftIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_cd),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        Text(
            text = stringResource(R.string.topics_manage_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    selectedKeys = if (allSelected) emptySet() else topics.map { it.key }.toSet()
                }
            ) {
                Text(
                    text = stringResource(
                        if (allSelected) R.string.topics_manage_clear_selection
                        else R.string.topics_manage_select_all
                    ),
                    fontSize = 12.sp
                )
            }
        }
        if (topics.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.topics_empty_title),
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(topics, key = { it.key }) { topic ->
                    val mergedKeys = aliases
                        .filterValues { resolveTopicKey(it, aliases) == topic.key }
                        .keys
                        .sorted()
                    TopicManageRow(
                        topic = topic,
                        mergedKeys = mergedKeys,
                        selected = topic.key in selectedKeys,
                        onClick = {
                            selectedKeys = if (topic.key in selectedKeys) {
                                selectedKeys - topic.key
                            } else {
                                selectedKeys + topic.key
                            }
                        }
                    )
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.topics_manage_selected, selectedKeys.size),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            mergeSources = topics.filter { it.key in selectedKeys }
                        },
                        enabled = selectedKeys.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.topics_manage_merge), fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            viewModel.unmergeTopicAliases(selectedKeys.toList())
                            selectedKeys = emptySet()
                        },
                        enabled = mergedSourceKeys.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.topics_manage_unmerge), fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            viewModel.restoreExcludedPosts(selectedKeys.toList())
                            selectedKeys = emptySet()
                        },
                        enabled = excludedPostCount > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            stringResource(
                                R.string.topics_restore_excluded_action,
                                excludedPostCount
                            ),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    mergeSources?.let { sources ->
        MergeTopicDialog(
            sources = sources,
            topics = topics,
            onDismiss = { mergeSources = null },
            onMerge = { target ->
                viewModel.mergeTopics(sources.map { it.key }, target.key)
                mergeSources = null
                selectedKeys = emptySet()
            }
        )
    }
}

@Composable
private fun TopicManageRow(
    topic: TopicItem,
    mergedKeys: List<String>,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val lastActivity = context.resources.formatRelativeTime(
        topic.lastAt,
        RelativeTimePreset.FeedCard
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(if (selected) 1.dp else 0.dp, WeiboOrange)
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
                Text(
                    text = "#",
                    color = WeiboOrange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.displayName,
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
                if (mergedKeys.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.topics_detail_merged,
                            mergedKeys.joinToString("、") { "#$it" }
                        ),
                        fontSize = 11.sp,
                        color = WeiboOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = WeiboOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
