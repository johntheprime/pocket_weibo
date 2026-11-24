package com.pocketweibo.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.reminder.ReminderRepeatRule
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.util.extractTopicTags
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.util.isReservedTopicKey
import com.pocketweibo.ui.util.resolveTopicKey
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface as SurfaceColor
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.formatReminderFireAt
import com.pocketweibo.ui.util.formatTimeUntilFire
import com.pocketweibo.ui.util.reminderRepeatShortLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
@Composable
fun MeScreen(
    onNavigateToSettings: () -> Unit = {},
    onEditActiveIdentity: (Long) -> Unit = {},
    onOpenPost: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val posts by app.repository.allPosts.collectAsState(initial = emptyList())
    val comments by app.repository.allCommentsForSearch.collectAsState(initial = emptyList())
    val topicAliasRows by app.repository.topicAliases.collectAsState(initial = emptyList())
    val pendingReminders by app.repository.observePendingRemindersWithPreview()
        .collectAsState(initial = emptyList())
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        nowMillis = System.currentTimeMillis()
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val reminderListMaxHeight = (configuration.screenHeightDp * 0.36f).dp
        .coerceAtMost(300.dp)
        .coerceAtLeast(168.dp)

    val topicAliases = remember(topicAliasRows) {
        topicAliasRows.associate { it.fromKey to it.toKey }
    }
    val topicCount = remember(posts, topicAliases) {
        posts.flatMap { post ->
            extractTopicTags(post.content)
                .map { resolveTopicKey(it.key, topicAliases) }
                .filterNot { isReservedTopicKey(it) }
        }.distinct().size
    }
    val weekAgo = remember(nowMillis) { nowMillis - 7L * 24 * 60 * 60 * 1000 }
    val postsLastWeek = remember(posts, weekAgo) { posts.count { it.createdAt >= weekAgo } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = stringResource(R.string.title_me),
            rightIcon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings_cd),
                    tint = WeiboOrange,
                    modifier = Modifier.size(24.dp)
                )
            },
            onRightIconClick = onNavigateToSettings
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = activeIdentity != null,
                            onClick = { activeIdentity?.let { onEditActiveIdentity(it.id) } }
                        ),
                    color = SurfaceColor
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activeIdentity != null) {
                                Avatar(
                                    name = activeIdentity!!.name,
                                    color = Color(0xFF4A90D9),
                                    size = 60.dp,
                                    avatarResName = activeIdentity!!.avatarResName,
                                    customAvatarUri = activeIdentity!!.customAvatarUri
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(GrayLight, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.me_avatar_placeholder),
                                        fontSize = 24.sp,
                                        color = GrayMiddle
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp)
                            ) {
                                Text(
                                    text = activeIdentity?.name
                                        ?: stringResource(R.string.me_no_identity),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GrayDark
                                )
                                if (activeIdentity?.motto?.isNotEmpty() == true) {
                                    Text(
                                        text = "\"${activeIdentity!!.motto}\"",
                                        fontSize = 12.sp,
                                        color = GrayMiddle,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.me_identity_count, identities.size),
                                    fontSize = 14.sp,
                                    color = GrayMiddle,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            activeIdentity?.let { identity ->
                if (identity.nationality.isNotEmpty() || identity.occupation.isNotEmpty() || identity.famousWork.isNotEmpty()) {
                    item {
                        Divider()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                if (identity.nationality.isNotEmpty()) {
                                    InfoRow(
                                        label = stringResource(R.string.label_nationality),
                                        value = identity.nationality
                                    )
                                }
                                if (identity.occupation.isNotEmpty()) {
                                    InfoRow(
                                        label = stringResource(R.string.label_occupation),
                                        value = identity.occupation
                                    )
                                }
                                if (identity.famousWork.isNotEmpty()) {
                                    InfoRow(
                                        label = stringResource(R.string.label_famous_work),
                                        value = identity.famousWork
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Divider(thickness = 0.5.dp)
                MeStatsSection(
                    posts = posts.size,
                    comments = comments.size,
                    topics = topicCount,
                    identities = identities.size,
                    postsLastWeek = postsLastWeek
                )
            }

            if (pendingReminders.isNotEmpty()) {
                item {
                    Divider(thickness = 0.5.dp)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceColor,
                        tonalElevation = 1.dp
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.me_reminders_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GrayDark,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "(${pendingReminders.size})",
                                    fontSize = 14.sp,
                                    color = GrayMiddle
                                )
                            }
                            Text(
                                text = stringResource(R.string.me_reminders_bottom_hint),
                                fontSize = 12.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                            )
                            val res = context.resources
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = reminderListMaxHeight)
                                    .verticalScroll(scrollState)
                            ) {
                                pendingReminders.forEach { row ->
                                    val fireStr = res.formatReminderFireAt(row.fireAtMillis)
                                    val untilStr = res.formatTimeUntilFire(row.fireAtMillis, nowMillis)
                                    val repeatShort = res.reminderRepeatShortLabel(row.repeatRule)
                                    val subtitleText = if (ReminderRepeatRule.isRepeating(row.repeatRule) && repeatShort != null) {
                                        stringResource(
                                            R.string.me_reminders_subtitle_next_repeat,
                                            untilStr,
                                            repeatShort
                                        )
                                    } else {
                                        stringResource(
                                            R.string.me_reminders_subtitle,
                                            fireStr,
                                            untilStr
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenPost(row.postId) }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = row.content.trim().ifBlank { "—" }.take(120),
                                                fontSize = 14.sp,
                                                color = GrayDark,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = subtitleText,
                                                fontSize = 12.sp,
                                                color = WeiboOrange,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            Text(
                                                text = identityDisplayName(row.identityName),
                                                fontSize = 12.sp,
                                                color = GrayMiddle,
                                                modifier = Modifier.padding(top = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    app.repository.cancelReminderById(row.reminderId)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = stringResource(R.string.me_reminders_cancel_cd),
                                                tint = GrayMiddle
                                            )
                                        }
                                    }
                                    Divider(thickness = 0.5.dp, color = GrayLight.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact, read-only totals. Kept on the Me page so feeds stay uncluttered. */
@Composable
private fun MeStatsSection(
    posts: Int,
    comments: Int,
    topics: Int,
    identities: Int,
    postsLastWeek: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.me_stats_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.me_stats_posts),
                    value = posts,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.me_stats_comments),
                    value = comments,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.me_stats_topics),
                    value = topics,
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.me_stats_identities),
                    value = identities,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(R.string.me_stats_last7days, postsLastWeek),
                fontSize = 11.sp,
                color = WeiboOrange,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = GrayDark
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 13.sp,
            color = GrayMiddle
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = GrayDark
        )
    }
}
