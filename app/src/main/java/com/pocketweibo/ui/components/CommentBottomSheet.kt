package com.pocketweibo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.data.local.dao.CommentWithIdentity
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.copyPlainToClipboard
import com.pocketweibo.ui.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentBottomSheet(
    sheetState: SheetState,
    comments: List<CommentWithIdentity>,
    activeIdentityId: Long?,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onEditComment: (Long, String) -> Unit = { _, _ -> },
    onLikeComment: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var commentText by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.comment_sheet_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close_cd),
                        tint = GrayMiddle
                    )
                }
            }
            
            Divider()
            
            if (comments.isEmpty()) {
                Text(
                    text = stringResource(R.string.comments_empty),
                    fontSize = 14.sp,
                    color = GrayMiddle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        CommentItem(
                            comment = comment,
                            isOwnComment = comment.identityId == activeIdentityId,
                            onDelete = { onDeleteComment(comment.id) }
                        )
                    }
                }
            }
            
            Divider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text(stringResource(R.string.comment_hint), fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true
                )
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onSendComment(commentText)
                            commentText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.comment_send_cd),
                        tint = if (commentText.isNotBlank()) Color(0xFFFD8225) else GrayMiddle
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CommentItem(
    comment: CommentWithIdentity,
    isOwnComment: Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipLabel = stringResource(R.string.clipboard_label_comment)
    val copiedToast = stringResource(R.string.toast_comment_copied)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    context.copyPlainToClipboard(clipLabel, comment.content, toast = copiedToast)
                }
            )
    ) {
        Avatar(
            name = comment.identityName,
            color = Color(0xFF4A90D9),
            size = 36.dp,
            avatarResName = comment.identityAvatarResName
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
                    text = comment.identityName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = " · ${context.resources.formatRelativeTime(comment.createdAt, RelativeTimePreset.CommentSheet)}",
                    fontSize = 12.sp,
                    color = GrayMiddle
                )
            }
            Text(
                text = comment.content,
                fontSize = 14.sp,
                color = GrayDark,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            if (isOwnComment) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = WeiboOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.delete),
                            fontSize = 12.sp,
                            color = WeiboOrange
                        )
                    }
                }
            }
        }
    }
}
