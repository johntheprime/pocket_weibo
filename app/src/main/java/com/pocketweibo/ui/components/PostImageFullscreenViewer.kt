package com.pocketweibo.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage

@Composable
fun PostImageFullscreenViewer(
    paths: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    if (paths.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val last = paths.lastIndex
    var page by remember(paths, initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, last))
    }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val model = remember(page, paths) { imageModelForPath(context, paths[page]) }
            if (model != null) {
                ZoomableImagePage(
                    model = model,
                    resetKey = page,
                    contentDescription = stringResource(R.string.post_image_viewer_cd),
                    onDoubleTapExit = onDismiss
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.post_image_close_cd),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            if (paths.size > 1) {
                if (page > 0) {
                    IconButton(
                        onClick = { page-- },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.post_image_prev_cd),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                if (page < last) {
                    IconButton(
                        onClick = { page++ },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.post_image_next_cd),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    model: Any,
    resetKey: Int,
    contentDescription: String,
    onDoubleTapExit: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(resetKey) { mutableFloatStateOf(1f) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(false)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(resetKey) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset += pan
                    }
                }
                .pointerInput(resetKey) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                onDoubleTapExit()
                            }
                        }
                    )
                }
        )
    }
}

private fun imageModelForPath(context: android.content.Context, rel: String): Any? {
    return when {
        rel.startsWith("content:") -> Uri.parse(rel)
        rel.startsWith("file:") -> Uri.parse(rel)
        else -> {
            val f = PostAttachmentStorage.fileForRelativePath(context, rel)
            if (f.isFile) f else null
        }
    }
}
