package com.pocketweibo.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pocketweibo.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.reminder.ReminderRepeatRule
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface as SurfaceColor
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.formatReminderFireAt
import com.pocketweibo.ui.util.formatTimeUntilFire
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MeScreen(
    onNavigateToMyPosts: () -> Unit = {},
    onNavigateToIdentities: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onEditActiveIdentity: (Long) -> Unit = {},
    onOpenPost: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val scope = rememberCoroutineScope()
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val pendingReminders by app.repository.observePendingRemindersWithPreview()
        .collectAsState(initial = emptyList())
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMillis = System.currentTimeMillis()
        }
    }

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
            modifier = Modifier.fillMaxSize()
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
                                    avatarResName = activeIdentity!!.avatarResName
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

            item {
                Divider()
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceColor
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
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
                            if (pendingReminders.isNotEmpty()) {
                                Text(
                                    text = "(${pendingReminders.size})",
                                    fontSize = 14.sp,
                                    color = GrayMiddle
                                )
                            }
                        }
                        if (pendingReminders.isEmpty()) {
                            Text(
                                text = stringResource(R.string.me_reminders_empty),
                                fontSize = 13.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            val res = context.resources
                            pendingReminders.forEach { row ->
                                val fireStr = res.formatReminderFireAt(row.fireAtMillis)
                                val untilStr = res.formatTimeUntilFire(row.fireAtMillis, nowMillis)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                        .clickable { onOpenPost(row.postId) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = row.content.trim().ifBlank { "—" }.take(120),
                                            fontSize = 14.sp,
                                            color = GrayDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = row.identityName,
                                            fontSize = 12.sp,
                                            color = GrayMiddle,
                                            modifier = Modifier.padding(top = 2.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val repeatShort = when (row.repeatRule) {
                                            ReminderRepeatRule.DAILY ->
                                                stringResource(R.string.reminder_repeat_short_daily)
                                            ReminderRepeatRule.WEEKLY ->
                                                stringResource(R.string.reminder_repeat_short_weekly)
                                            ReminderRepeatRule.MONTHLY ->
                                                stringResource(R.string.reminder_repeat_short_monthly)
                                            else -> null
                                        }
                                        Text(
                                            text = if (repeatShort != null) {
                                                stringResource(
                                                    R.string.me_reminders_subtitle_repeat,
                                                    fireStr,
                                                    untilStr,
                                                    repeatShort
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.me_reminders_subtitle,
                                                    fireStr,
                                                    untilStr
                                                )
                                            },
                                            fontSize = 12.sp,
                                            color = WeiboOrange,
                                            modifier = Modifier.padding(top = 4.dp)
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
                            }
                        }
                    }
                }
            }

            item {
                Divider()
                MenuItem(
                    title = stringResource(R.string.me_identity_title),
                    subtitle = stringResource(R.string.me_identity_subtitle),
                    onClick = onNavigateToIdentities
                )
            }

            item {
                Divider()
                MenuItem(
                    title = stringResource(R.string.me_posts_title),
                    subtitle = stringResource(R.string.me_posts_subtitle),
                    onClick = onNavigateToMyPosts
                )
            }

            activeIdentity?.let { identity ->
                if (identity.nationality.isNotEmpty() || identity.occupation.isNotEmpty()) {
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
                                    InfoRow(label = stringResource(R.string.label_nationality), value = identity.nationality)
                                }
                                if (identity.occupation.isNotEmpty()) {
                                    InfoRow(label = stringResource(R.string.label_occupation), value = identity.occupation)
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
        }
    }
}

@Composable
private fun MenuItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GrayLight
            )
        }
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
