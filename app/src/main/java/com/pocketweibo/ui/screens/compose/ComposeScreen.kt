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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.pocketweibo.data.local.entity.PostEntity
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface
import com.pocketweibo.ui.theme.WeiboOrange
import kotlinx.coroutines.launch

@Composable
fun ComposeScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val identities by app.repository.allIdentities.collectAsState(initial = emptyList())
    val activeIdentity by app.repository.activeIdentity.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var selectedIdentity by remember { mutableStateOf<IdentityEntity?>(null) }
    var content by remember { mutableStateOf("") }
    var showIdentityPicker by remember { mutableStateOf(false) }
    var imageUris by remember { mutableStateOf(listOf<Uri>()) }

    var lastContentEditedAt by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val composeOpenedAt = remember { SystemClock.elapsedRealtime() }
    var lastPostedAt by remember { mutableStateOf(0L) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            imageUris = (imageUris + uris).take(9)
        }
    }

    LaunchedEffect(Unit) {
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
        if (!content.isNotBlank() || selectedIdentity == null) return
        lastPostedAt = SystemClock.elapsedRealtime()
        scope.launch {
            app.repository.insertPost(
                PostEntity(
                    identityId = selectedIdentity!!.id,
                    content = content
                )
            )
            app.repository.clearDraft()
            onDismiss()
        }
    }

    ShakeToSendEffect(
        canSend = content.isNotBlank() && selectedIdentity != null,
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
                    enabled = content.isNotBlank() && selectedIdentity != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeiboOrange,
                        disabledContainerColor = GrayLight
                    )
                ) {
                    Text(stringResource(R.string.compose_send))
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

                    if (imageUris.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(imageUris) { uri ->
                                Box(
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(uri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(R.string.compose_image_cd),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = { imageUris = imageUris.filter { it != uri } },
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
                    icon = Icons.Default.LocationOn,
                    label = stringResource(R.string.compose_label_location),
                    onClick = { }
                )
                ActionButton(
                    icon = Icons.Default.AlternateEmail,
                    label = stringResource(R.string.compose_label_mention),
                    onClick = { }
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
