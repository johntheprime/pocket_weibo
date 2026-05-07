package com.pocketweibo.ui.screens.detail

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pocketweibo.data.media.VoiceRecordingController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.data.prefs.nextShakeReminderFireAtMillis
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.CommentVoiceComposerBar
import com.pocketweibo.ui.components.PostAudioPlayerBar
import com.pocketweibo.ui.components.PostImageFullscreenViewer
import com.pocketweibo.ui.components.PostImageGallery
import com.pocketweibo.ui.components.SelectablePostBody
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.copyPlainToClipboard
import com.pocketweibo.ui.util.postOrCommentBodyForDisplay
import com.pocketweibo.ui.util.findActivity
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.formatReminderFireToastTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.reminder.ReminderRepeatRule
import com.pocketweibo.ui.reminder.ReminderPickerDialog
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingReminderSchedule(
    val fireAt: Long,
    val rule: String,
    val toastTextOverride: String?,
)

private const val COMMENT_VOICE_MAX_RECORD_MS = 120_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ... (Keep your ViewModel and State logic the same)
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val viewModel: PostDetailViewModel = viewModel(factory = PostDetailViewModel.Factory(app.repository))
    val scope = rememberCoroutineScope()

    var pendingSchedule by remember { mutableStateOf<PendingReminderSchedule?>(null) }
    val voiceRecorder = remember { VoiceRecordingController(context.applicationContext) }
    var preparedVoiceFile by remember { mutableStateOf<File?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            preparedVoiceFile?.delete()
        }
    }

    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceRecorder.startRecordingOnNextMainFrame { ok ->
                if (ok) {
                    isRecordingVoice = true
                }
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.toast_record_audio_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val notifyPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingSchedule
        pendingSchedule = null
        if (granted && pending != null) {
            viewModel.scheduleReminderAt(pending.fireAt, pending.rule)
            val toastText = pending.toastTextOverride
                ?: context.getString(R.string.reminder_scheduled_toast)
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        } else if (!granted && pending != null) {
            Toast.makeText(
                context,
                context.getString(R.string.reminder_permission_denied_toast),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val scheduleReminderAt: (Long, String, String?) -> Unit = { fireAt, repeatRule, toastTextOverride ->
        if (fireAt <= System.currentTimeMillis() + 5000L) {
            Toast.makeText(
                context,
                context.getString(R.string.post_detail_remind_time_past),
                Toast.LENGTH_SHORT
            ).show()
        } else if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSchedule = PendingReminderSchedule(fireAt, repeatRule, toastTextOverride)
            notifyPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.scheduleReminderAt(fireAt, repeatRule)
            val toastText = toastTextOverride
                ?: context.getString(R.string.reminder_scheduled_toast)
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        }
    }

    val openExactAlarmSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }
    }
    val openAppDetailsSettings: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    var detailOpenedAt by remember(postId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
        detailOpenedAt = SystemClock.elapsedRealtime()
    }

    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    var commentText by remember { mutableStateOf("") }
    var lastCommentEditedAt by remember(postId) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var lastShakeReminderScheduledAt by remember(postId) { mutableLongStateOf(0L) }
    var imageViewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val detailListState = rememberLazyListState()
    var scrollToNewestCommentAfterSend by remember { mutableStateOf(false) }

    LaunchedEffect(comments.size, comments.firstOrNull()?.id) {
        if (scrollToNewestCommentAfterSend && comments.isNotEmpty()) {
            scrollToNewestCommentAfterSend = false
            delay(48)
            val newestCommentIndex = 2
            if (detailListState.layoutInfo.totalItemsCount > newestCommentIndex) {
                runCatching { detailListState.scrollToItem(newestCommentIndex) }
            }
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (!isRecordingVoice) return@LaunchedEffect
        delay(COMMENT_VOICE_MAX_RECORD_MS)
        if (!voiceRecorder.isActive) return@LaunchedEffect
        val file = voiceRecorder.stopRecording(finishedByMaxDuration = true) { }
        isRecordingVoice = false
        Toast.makeText(
            context,
            context.getString(R.string.toast_compose_voice_max_duration),
            Toast.LENGTH_SHORT
        ).show()
        if (file != null) {
            preparedVoiceFile = file
        }
    }

    PostDetailShakeToReminderEffect(
        canSchedule = post != null,
        lastCommentEditedAtMark = lastCommentEditedAt,
        detailOpenedAtMark = detailOpenedAt,
        lastShakeReminderScheduledAtMark = lastShakeReminderScheduledAt,
        onShakeReminder = {
            scope.launch {
                val settings = withContext(Dispatchers.IO) {
                    UiPreferences.getShakeReminderSettings(app)
                }
                val fireAt = nextShakeReminderFireAtMillis(settings)
                if (fireAt <= System.currentTimeMillis() + 5000L) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.post_detail_remind_time_past),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                lastShakeReminderScheduledAt = SystemClock.elapsedRealtime()
                val whenText = context.resources.formatReminderFireToastTime(fireAt)
                val shakeToast = context.getString(R.string.toast_shake_reminder_set_fmt, whenText)
                scheduleReminderAt(fireAt, ReminderRepeatRule.NONE, shakeToast)
            }
        }
    )

    Box(modifier = modifier.fillMaxSize()) {
    // Use Scaffold: It is specifically designed to handle top bars and bottom bars
    // while managing inner content padding correctly.
    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                WeiboTitleBar(
                    title = stringResource(R.string.title_post_detail),
                    leftIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                stringResource(R.string.back_cd),
                                tint = WeiboOrange
                            )
                        }
                    }
                )
                Divider(thickness = 0.5.dp)
            }
        },
        bottomBar = {
            // Pinning this to the bottomBar slot of the Scaffold handles the keyboard transition best
            Surface(
                color = Color.White,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding() // This pushes the input bar up
                    .navigationBarsPadding() // This respects the system nav bar
            ) {
                Column {
                    Divider(thickness = 0.5.dp)
                    val canSendComment = commentText.isNotBlank() || preparedVoiceFile != null
                    CommentVoiceComposerBar(
                        text = commentText,
                        onTextChange = {
                            commentText = it
                            lastCommentEditedAt = SystemClock.elapsedRealtime()
                        },
                        commentHint = stringResource(R.string.post_detail_comment_hint),
                        preparedVoice = preparedVoiceFile != null,
                        voiceReadyLabel = stringResource(R.string.compose_voice_ready),
                        onClearVoiceDraft = {
                            preparedVoiceFile?.delete()
                            preparedVoiceFile = null
                        },
                        isRecording = isRecordingVoice,
                        onMicClick = {
                            if (isRecordingVoice) {
                                val file = voiceRecorder.stopRecording(finishedByMaxDuration = false) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_compose_voice_too_short),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isRecordingVoice = false
                                if (file != null) preparedVoiceFile = file
                            } else {
                                when {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) != PackageManager.PERMISSION_GRANTED -> {
                                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                    else -> {
                                        voiceRecorder.startRecordingOnNextMainFrame { ok ->
                                            if (ok) {
                                                isRecordingVoice = true
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        micContentDescription = stringResource(R.string.compose_voice_cd),
                        sendContentDescription = stringResource(R.string.post_detail_send_cd),
                        sendEnabled = canSendComment,
                        onSend = {
                            if (!canSendComment) return@CommentVoiceComposerBar
                            scrollToNewestCommentAfterSend = true
                            viewModel.addComment(commentText, preparedVoiceFile)
                            commentText = ""
                            preparedVoiceFile = null
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    ) { innerPadding ->
        // The innerPadding automatically accounts for the topBar and the bottomBar (including keyboard)
        LazyColumn(
            state = detailListState,
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(innerPadding)
                // Reserve space for the IME so the list scrolls above the keyboard
                .imePadding()
        ) {
            post?.let { currentPost ->
                item {
                    PostDetailCard(
                        post = currentPost,
                        onShareClick = {
                            sharePost(
                                context,
                                context.identityDisplayName(currentPost.identityName),
                                currentPost.content
                            )
                        },
                        onPostImageClick = { index ->
                            imageViewer = PostAttachmentStorage.parseStoredPaths(currentPost.imageUris) to index
                        },
                        onCopyPost = {
                            context.copyPlainToClipboard(
                                context.getString(R.string.post_detail_copy_label),
                                currentPost.content,
                                toast = context.getString(R.string.toast_clipboard_copied)
                            )
                        },
                        onDeletePost = { viewModel.deletePost(onBack) },
                        onScheduleReminderAt = scheduleReminderAt,
                        onOpenExactAlarmSettings = openExactAlarmSettings,
                        onOpenAppDetailsSettings = openAppDetailsSettings
                    )
                }

                item { CommentsHeader(commentCount = comments.size, comments = comments) }

                if (comments.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.comments_empty),
                                fontSize = 14.sp,
                                color = GrayMiddle
                            )
                        }
                    }
                } else {
                    items(comments, key = { it.id }) { comment ->
                        CommentCard(comment = comment)
                        Divider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

        imageViewer?.let { (paths, start) ->
            PostImageFullscreenViewer(
                paths = paths,
                initialIndex = start,
                onDismiss = { imageViewer = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostDetailCard(
    post: com.pocketweibo.data.local.dao.PostWithIdentity,
    onShareClick: () -> Unit,
    onPostImageClick: (Int) -> Unit,
    onCopyPost: () -> Unit,
    onDeletePost: () -> Unit,
    onScheduleReminderAt: (Long, String, String?) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit
) {
    val context = LocalContext.current
    val resources = context.resources
    val shareLabel = stringResource(R.string.post_detail_action_share)
    val commentCd = stringResource(R.string.post_detail_comment_cd)
    val moreCd = stringResource(R.string.post_detail_more_cd)
    val shareCd = stringResource(R.string.post_detail_share_cd)
    val displayName = identityDisplayName(post.identityName)
    var moreExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRemindPicker by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = displayName,
                    color = Color(0xFF4A90D9),
                    size = 56.dp,
                    avatarResName = post.identityAvatarResName,
                    customAvatarUri = post.identityCustomAvatarUri
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark
                    )
                    Text(
                        text = resources.formatRelativeTime(post.createdAt, RelativeTimePreset.PostDetail),
                        fontSize = 13.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = moreCd,
                            tint = GrayMiddle
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.post_detail_menu_copy)) },
                            onClick = {
                                moreExpanded = false
                                onCopyPost()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.post_detail_menu_share)) },
                            onClick = {
                                moreExpanded = false
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.post_detail_menu_remind)) },
                            onClick = {
                                moreExpanded = false
                                showRemindPicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.post_detail_menu_delete)) },
                            onClick = {
                                moreExpanded = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
            
            SelectablePostBody(
                text = post.content,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = GrayDark,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.padding(top = 12.dp)
            )

            if (PostAttachmentStorage.parseStoredPaths(post.imageUris).isNotEmpty()) {
                PostImageGallery(
                    imageUris = post.imageUris,
                    maxHeight = 160.dp,
                    modifier = Modifier.padding(top = 12.dp),
                    enableImageClick = true,
                    onImageClick = onPostImageClick,
                    imageContentDescription = stringResource(R.string.post_image_detail_cd)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = shareCd,
                            tint = GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = shareLabel,
                    onClick = onShareClick
                )
                Spacer(modifier = Modifier.width(24.dp))
                ActionButton(
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = commentCd,
                            tint = GrayMiddle,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = "${post.commentCount}",
                    onClick = { }
                )
            }

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text(stringResource(R.string.post_detail_delete_confirm_title)) },
                    text = { Text(stringResource(R.string.post_detail_delete_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeletePost()
                            }
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirm = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            if (showRemindPicker) {
                ReminderPickerDialog(
                    onDismissRequest = { showRemindPicker = false },
                    onScheduleAt = { fireAt, repeatRule ->
                        showRemindPicker = false
                        onScheduleReminderAt(fireAt, repeatRule, null)
                    },
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenAppDetailsSettings = onOpenAppDetailsSettings,
                    titleText = stringResource(R.string.post_detail_remind_title),
                    additionalHint = null
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
    tint: Color = GrayMiddle
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = text,
            fontSize = 13.sp,
            color = tint,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CommentsHeader(
    commentCount: Int,
    comments: List<CommentWithIdentity>
) {
    val context = LocalContext.current
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val header = stringResource(R.string.comments_header)
    val copyAllCd = stringResource(R.string.comments_copy_all_cd)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = header,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                if (commentCount > 0) {
                    Text(
                        text = " ($commentCount)",
                        fontSize = 15.sp,
                        color = GrayMiddle
                    )
                }
            }
            if (commentCount > 0) {
                TextButton(
                    onClick = {
                        val text = comments
                            .sortedBy { it.createdAt }
                            .mapIndexed { index, c ->
                                val line = postOrCommentBodyForDisplay(c.content, c.audioPath, voiceOnlyLabel).trim()
                                "${index + 1}. $line"
                            }
                            .joinToString(separator = "\n\n")
                        context.copyPlainToClipboard(
                            label = context.getString(R.string.clipboard_label_all_comments),
                            text = text,
                            toast = context.getString(R.string.toast_all_comments_copied)
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.semantics { contentDescription = copyAllCd }
                ) {
                    Text(
                        text = stringResource(R.string.comments_copy_all),
                        color = WeiboOrange,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentCard(comment: CommentWithIdentity) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.clipboard_label_comment)
    val copiedToast = stringResource(R.string.toast_comment_copied)
    val displayName = identityDisplayName(comment.identityName)
    val voiceOnlyLabel = stringResource(R.string.comment_body_voice_only)
    val bodyDisplay = postOrCommentBodyForDisplay(comment.content, comment.audioPath, voiceOnlyLabel)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    context.copyPlainToClipboard(clipLabel, bodyDisplay, toast = copiedToast)
                }
            ),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = displayName,
                    color = Color(0xFF4A90D9),
                    size = 40.dp,
                    avatarResName = comment.identityAvatarResName,
                    customAvatarUri = comment.identityCustomAvatarUri
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayDark
                        )
                        Text(
                            text = " · ${context.resources.formatRelativeTime(comment.createdAt, RelativeTimePreset.PostDetail)}",
                            fontSize = 12.sp,
                            color = GrayMiddle
                        )
                    }
                    Text(
                        text = bodyDisplay,
                        fontSize = 14.sp,
                        color = GrayDark,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (comment.audioPath.isNotBlank()) {
                        PostAudioPlayerBar(
                            relativeAudioPath = comment.audioPath,
                            modifier = Modifier.padding(top = 8.dp),
                            compact = true,
                        )
                    }
                }
            }
        }
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
