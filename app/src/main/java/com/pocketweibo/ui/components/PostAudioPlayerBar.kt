package com.pocketweibo.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.data.media.CommentAttachmentStorage
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.WeiboOrange

@Composable
fun PostAudioPlayerBar(
    relativeAudioPath: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val file = remember(relativeAudioPath) {
        if (relativeAudioPath.isBlank()) null
        else {
            val norm = relativeAudioPath.replace('\\', '/').trimStart('/')
            when {
                norm.startsWith("${CommentAttachmentStorage.REL_ROOT}/") ->
                    CommentAttachmentStorage.fileForRelativePath(context, norm).takeIf { it.isFile }
                else -> PostAttachmentStorage.fileForRelativePath(context, relativeAudioPath).takeIf { it.isFile }
            }
        }
    } ?: return

    var playing by remember(relativeAudioPath) { mutableStateOf(false) }
    val player = remember(relativeAudioPath) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    playing = false
                    runCatching { seekTo(0) }
                }
            }
        }.getOrNull()
    }

    DisposableEffect(relativeAudioPath) {
        onDispose {
            runCatching {
                player?.stop()
                player?.release()
            }
        }
    }

    if (player == null) return

    val playCd = stringResource(R.string.post_audio_play_cd)
    val pauseCd = stringResource(R.string.post_audio_pause_cd)
    val badge = stringResource(R.string.post_audio_badge)

    Row(
        modifier = modifier
            .background(WeiboOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = WeiboOrange,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = badge,
            fontSize = if (compact) 12.sp else 14.sp,
            color = GrayDark,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    player.start()
                    playing = true
                }
            },
        ) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) pauseCd else playCd,
                tint = WeiboOrange,
            )
        }
    }
}
