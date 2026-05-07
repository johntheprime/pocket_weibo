package com.pocketweibo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pocketweibo.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.GrayDark
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: PostWithIdentity,
    onCommentClick: () -> Unit,
    onRemindClick: () -> Unit,
    onShareClick: () -> Unit,
    onPostClick: () -> Unit,
    /** List feeds are text-first; images remain visible in post detail. */
    showPostImages: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showSelectableCopy by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    val displayName = identityDisplayName(post.identityName)
    val hasStoredImages = remember(post.id, post.imageUris) {
        PostAttachmentStorage.parseStoredPaths(post.imageUris).isNotEmpty()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA), RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onPostClick,
                    onLongClick = { showSelectableCopy = true }
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Avatar(
                    name = displayName,
                    color = Color(0xFF4A90D9),
                    size = 40.dp,
                    avatarResName = post.identityAvatarResName,
                    customAvatarUri = post.identityCustomAvatarUri
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard),
                            fontSize = 11.sp,
                            color = GrayMiddle,
                            maxLines = 1
                        )
                        if (!showPostImages && hasStoredImages) {
                            ListPostImageIndicator(
                                imageUris = post.imageUris,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = post.content,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (showPostImages && hasStoredImages) {
                PostImageGallery(
                    imageUris = post.imageUris,
                    maxHeight = 88.dp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton(
                    icon = Icons.Default.Share,
                    text = stringResource(R.string.action_repost),
                    count = null,
                    onClick = onShareClick
                )

                Spacer(modifier = Modifier.width(10.dp))

                ActionButton(
                    icon = Icons.Default.Schedule,
                    text = stringResource(R.string.post_card_remind_cd),
                    count = null,
                    onClick = onRemindClick
                )

                Spacer(modifier = Modifier.width(10.dp))

                ActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    text = stringResource(R.string.action_comment),
                    count = post.commentCount,
                    onClick = onCommentClick
                )
            }
        }
        if (showSelectableCopy) {
            SelectableCopyDialog(
                body = post.content,
                onDismiss = { showSelectableCopy = false }
            )
        }
    }
}

/** Keeps action icons on one vertical line across cards; counts sit in this slot right-aligned. */
private val ActionButtonCountSlotWidth = 20.dp

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    count: Int? = null,
    isLiked: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState().value
    
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .background(
                if (isPressed) Color(0xFFE0E0E0) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = when {
                isLiked -> Color(0xFFFF5136)
                else -> GrayMiddle
            },
            modifier = Modifier.size(20.dp)
        )
        Box(
            modifier = Modifier
                .width(ActionButtonCountSlotWidth)
                .padding(start = 2.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (count != null && count > 0) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    color = when {
                        isLiked -> Color(0xFFFF5136)
                        else -> GrayMiddle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}