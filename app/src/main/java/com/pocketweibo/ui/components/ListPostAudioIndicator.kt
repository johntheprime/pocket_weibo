package com.pocketweibo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pocketweibo.R
import com.pocketweibo.data.media.PostVoice
import com.pocketweibo.ui.theme.GrayMiddle

@Composable
fun ListPostAudioIndicator(
    audioPath: String,
    modifier: Modifier = Modifier,
) {
    val has = remember(audioPath) { PostVoice.hasAudio(audioPath) }
    if (!has) return
    Icon(
        imageVector = Icons.Default.Mic,
        contentDescription = stringResource(R.string.post_list_audio_cd),
        tint = GrayMiddle.copy(alpha = 0.38f),
        modifier = modifier.size(13.dp)
    )
}
