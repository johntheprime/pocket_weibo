package com.pocketweibo.ui.screens.compose

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketweibo.R
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.data.local.entity.IdentityEntity
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Composable
fun ComposeScreen(
    onDismiss: () -> Unit,
    initialShareText: String = "",
    onConsumeInitialShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val prepMutex = remember { Mutex() }

    var selectedIdentity by remember { mutableStateOf<IdentityEntity?>(null) }
    var content by remember { mutableStateOf("") }
    var showIdentityPicker by remember { mutableStateOf(false) }
    var preparedImageFiles by remember { mutableStateOf(listOf<File>()) }
    var isPreparingImages by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var useOriginalForThisPost by remember { mutableStateOf(false) }
    var showMentionDialog by remember { mutableStateOf(false) }

    var lastContentEditedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val composeOpenedAt = remember { SystemClock.elapsedRealtime() }
    var lastPostedAt by remember { mutableStateOf(0L) }

    val preparedSnapshot by rememberUpdatedState(preparedImageFiles)
    DisposableEffect(Unit) {
        onDispose {
            preparedSnapshot.forEach { f -> if (f.exists()) f.delete() }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            prepMutex.withLock {
                isPreparingImages = true
                try {
                    val merged = preparedImageFiles.toMutableList()
                    val slotsLeft = (9 - merged.size).coerceAtLeast(0)
                    if (slotsLeft == 0) return@withLock
                    val toProcess = uris.take(slotsLeft)
                    for (u in toProcess) {
                        val f = PostAttachmentStorage.prepareOneGalleryImage(
                            context,
                            u,
                            useOriginalForThisPost
                        )
                        if (f != null) merged.add(f)
                    }
                    preparedImageFiles = merged.take(9)
                } finally {
                    isPreparingImages = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialShareText.isNotBlank()) {
            content = initialShareText.trim().take(2000)
            lastContentEditedAt = SystemClock.elapsedRealtime()
            onConsumeInitialShare()
        } else {
            val draft = app.repository.loadDraft()
            if (draft != null) {
                content = draft.first
                lastContentEditedAt = SystemClock.elapsedRealtime()
                val identityId = draft.second
                val identity = identities.find { it.id == identityId }
                if (identity != null) {
                    selectedIdentity = identity
                } else if (activeIdentity != null) {
                    selectedIdentity = activeIdentity
                }
            }
        }
    }

    fun saveAndDismiss() {
        scope.launch {
            if (content.isNotBlank() && selectedIdentity != null) {
                app.repository.saveDraft(content, selectedIdentity!!.id)
            }
            onDismiss()
        }
    }

    LaunchedEffect(activeIdentity) {
        if (selectedIdentity == null && activeIdentity != null) {
            selectedIdentity = activeIdentity
        }
    }

    fun performSend() {
        if (selectedIdentity == null) return
        if (!content.isNotBlank() && preparedImageFiles.isEmpty()) return
        if (isSending || isPreparingImages) return
        isSending = true
        lastPostedAt = SystemClock.elapsedRealtime()
        val identityId = selectedIdentity!!.id
        val text = content
        val filesToSend = preparedImageFiles.toList()
        scope.launch {
            try {
                app.repository.insertPostWithPreparedGallery(
                    identityId = identityId,
                    content = text,
                    preparedFiles = filesToSend
                )
                app.repository.clearDraft()
                preparedImageFiles = emptyList()
                onDismiss()
            } finally {
                isSending = false
            }
        }
    }

    ShakeToSendEffect(
        canSend = (content.isNotBlank() || preparedImageFiles.isNotEmpty()) &&
            selectedIdentity != null &&
            !isSending &&
            !isPreparingImages,
        lastContentEditedAtMark = lastContentEditedAt,
        composeOpenedAtMark = composeOpenedAt,
        lastPostedAtMark = lastPostedAt,
        onShakeSend = { performSend() }
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isPreparingImages) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = WeiboOrange
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (content.isNotBlank()) {
                        scope.launch { app.repository.saveDraft(content, selectedIdentity?.id ?: 0L) }
                    }
                    onDismiss() 
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_cd),
                        tint = GrayDark
                    )
                }

                Text(
                    text = stringResource(R.string.title_compose),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )

                Button(
                    onClick = { performSend() },
                    enabled = (content.isNotBlank() || preparedImageFiles.isNotEmpty()) &&
                        selectedIdentity != null &&
                        !isSending &&
                        !isPreparingImages,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeiboOrange,
                        disabledContainerColor = GrayLight
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.compose_send))
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Surface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showIdentityPicker = !showIdentityPicker }
                    ) {
                        if (selectedIdentity != null) {
                            Avatar(
                                name = selectedIdentity!!.name,
                                color = Color(0xFF4A90D9),
                                size = 32.dp,
                                avatarResName = selectedIdentity!!.avatarResName
                            )
                            Text(
                                text = selectedIdentity!!.name,
                                fontSize = 14.sp,
                                color = GrayDark,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(GrayLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "?", fontSize = 14.sp, color = GrayMiddle)
                            }
                            Text(
                                text = stringResource(R.string.compose_select_identity),
                                fontSize = 14.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    if (showIdentityPicker && identities.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(identities) { identity ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (selectedIdentity?.id == identity.id)
                                                Color(0xFF4A90D9)
                                            else
                                                GrayLight,
                                            CircleShape
                                        )
                                        .border(
                                            width = if (selectedIdentity?.id == identity.id) 2.dp else 0.dp,
                                            color = if (selectedIdentity?.id == identity.id) GrayDark else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedIdentity = identity
                                            showIdentityPicker = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = identity.name.first().toString().uppercase(),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    TextField(
                        value = content,
                        onValueChange = {
                            if (it.length <= 2000) {
                                content = it
                                lastContentEditedAt = SystemClock.elapsedRealtime()
                            }
                        },
                        placeholder = { Text(stringResource(R.string.compose_content_hint), color = GrayMiddle) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent
                        ),
                        minLines = 5
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "${content.length}/2000",
                            fontSize = 12.sp,
                            color = if (content.length > 1900) Color(0xFFFF5136) else GrayMiddle
                        )
                    }
                    Text(
                        text = stringResource(R.string.compose_shake_hint),
                        fontSize = 11.sp,
                        color = GrayMiddle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.compose_per_post_original_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GrayDark
                            )
                            Text(
                                text = stringResource(R.string.compose_per_post_original_subtitle),
                                fontSize = 11.sp,
                                color = GrayMiddle,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Switch(
                            checked = useOriginalForThisPost,
                            onCheckedChange = { useOriginalForThisPost = it }
                        )
                    }
                    if (isPreparingImages) {
                        Text(
                            text = stringResource(R.string.compose_preparing_images),
                            fontSize = 11.sp,
                            color = WeiboOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }

                    if (preparedImageFiles.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(preparedImageFiles, key = { it.absolutePath }) { file ->
                                Box(
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(file)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.compose_image_cd),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = {
                                            if (file.exists()) file.delete()
                                            preparedImageFiles =
                                                preparedImageFiles.filter { it.absolutePath != file.absolutePath }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.compose_remove_image_cd),
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionButton(
                    icon = Icons.Default.Image,
                    label = stringResource(R.string.compose_label_image),
                    onClick = { imagePickerLauncher.launch("image/*") }
                )
                ActionButton(
                    icon = Icons.Default.AlternateEmail,
                    label = stringResource(R.string.compose_label_mention),
                    onClick = { showMentionDialog = true }
                )
            }

            if (identities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.compose_no_identity_hint),
                        fontSize = 14.sp,
                        color = GrayMiddle
                    )
                }
            }
        }

        if (showMentionDialog) {
            val others = identities.filter { it.id != selectedIdentity?.id }
            AlertDialog(
                onDismissRequest = { showMentionDialog = false },
                title = { Text(stringResource(R.string.compose_mention_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (others.isEmpty()) {
                            Text(
                                text = stringResource(R.string.compose_mention_empty),
                                fontSize = 14.sp,
                                color = GrayMiddle
                            )
                        } else {
                            others.forEach { id ->
                                TextButton(
                                    onClick = {
                                        content = "${content}@${id.name} "
                                        lastContentEditedAt = SystemClock.elapsedRealtime()
                                        showMentionDialog = false
                                    }
                                ) {
                                    Text("@${id.name}")
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMentionDialog = false }) {
                        Text(stringResource(R.string.close_cd))
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = WeiboOrange,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = GrayMiddle,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
