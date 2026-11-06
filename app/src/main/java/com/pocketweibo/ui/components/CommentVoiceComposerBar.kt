package com.pocketweibo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.WeiboOrange

/**
 * Comment input with optional voice draft (mic + send). Recording state is owned by the caller.
 * When [isRecording] is true, shows a pulsing banner so users see that capture is active.
 * Tapping the banner calls [onRecordingBannerTap] (e.g. stop + send shortcut).
 * The mic is hidden while the user is composing text (field focused or non-blank text) so the
 * text path stays uncluttered; it stays visible when the field is empty and unfocused, and
 * always while recording so capture can be stopped from the bar.
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
    onRecordingBannerTap: () -> Unit,
    modifier: Modifier = Modifier,
    textFieldShape: RoundedCornerShape = RoundedCornerShape(24.dp),
    textMaxLines: Int = 4,
) {
    val bannerTapSemantics = stringResource(R.string.comment_voice_banner_stop_send_cd)
    val micCd = if (isRecording) {
        stringResource(R.string.compose_voice_tap_stop)
    } else {
        micContentDescription
    }

    val textFieldInteractionSource = remember { MutableInteractionSource() }
    val textFieldFocused by textFieldInteractionSource.collectIsFocusedAsState()
    val showMicButton =
        isRecording || (text.isBlank() && !textFieldFocused)

    Column(modifier = modifier) {
        if (preparedVoice && !isRecording) {
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
        if (isRecording) {
            CommentVoiceRecordingBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = bannerTapSemantics
                    }
                    .clickable(onClick = onRecordingBannerTap),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val fieldBorderColor = if (textFieldFocused) WeiboOrange else GrayLight
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 14.sp, color = GrayDark),
                interactionSource = textFieldInteractionSource,
                cursorBrush = SolidColor(WeiboOrange),
                maxLines = textMaxLines,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, fieldBorderColor, textFieldShape)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = commentHint,
                                fontSize = 14.sp,
                                color = GrayMiddle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (showMicButton) {
                IconButton(onClick = onMicClick) {
                    val micModifier = if (isRecording) {
                        Modifier
                            .background(Color(0xFFFFCDD2).copy(alpha = 0.95f), CircleShape)
                            .padding(6.dp)
                    } else {
                        Modifier
                    }
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = micCd,
                        tint = if (isRecording) Color(0xFFE53935) else WeiboOrange,
                        modifier = micModifier,
                    )
                }
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

@Composable
private fun CommentVoiceRecordingBanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "comment_rec_banner")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(
        modifier = modifier
            .background(
                color = Color(0xFFFFEBEE).copy(alpha = 0.35f + 0.45f * pulse),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = Color(0xFFC62828).copy(alpha = pulse),
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.compose_voice_recording),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB71C1C),
            )
            Text(
                text = stringResource(R.string.comment_voice_banner_stop_send_hint),
                fontSize = 12.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { RecordingPulseDot(dotIndex = it) }
        }
    }
}

@Composable
private fun RecordingPulseDot(dotIndex: Int) {
    val transition = rememberInfiniteTransition(label = "rec_dot_$dotIndex")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 520,
                delayMillis = dotIndex * 160,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .background(Color(0xFFE53935).copy(alpha = alpha), CircleShape),
    )
}
