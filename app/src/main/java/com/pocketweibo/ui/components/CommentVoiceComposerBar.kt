package com.pocketweibo.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange

/**
 * Comment input with optional voice draft (mic + send). Recording state is owned by the caller.
 */
@Composable
fun CommentVoiceComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    commentHint: String,
    preparedVoice: Boolean,
    voiceReadyLabel: String,
    onClearVoiceDraft: () -> Unit,
    isRecording: Boolean,
    onMicClick: () -> Unit,
    micContentDescription: String,
    sendContentDescription: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    textFieldShape: RoundedCornerShape = RoundedCornerShape(24.dp),
    textMaxLines: Int = 4,
) {
    Column(modifier = modifier) {
        if (preparedVoice) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = voiceReadyLabel,
                    fontSize = 12.sp,
                    color = GrayMiddle,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClearVoiceDraft) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.compose_voice_remove_cd),
                        tint = WeiboOrange,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(commentHint, fontSize = 14.sp, color = GrayMiddle)
                },
                modifier = Modifier.weight(1f),
                shape = textFieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeiboOrange,
                    unfocusedBorderColor = GrayLight,
                    focusedContainerColor = Color(0xFFF8F8F8),
                    unfocusedContainerColor = Color(0xFFF8F8F8),
                ),
                maxLines = textMaxLines,
            )
            IconButton(onClick = onMicClick) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = micContentDescription,
                    tint = when {
                        isRecording -> Color.Red
                        else -> WeiboOrange
                    },
                )
            }
            IconButton(onClick = onSend, enabled = sendEnabled) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = sendContentDescription,
                    tint = if (sendEnabled) WeiboOrange else GrayMiddle,
                )
            }
        }
    }
}
