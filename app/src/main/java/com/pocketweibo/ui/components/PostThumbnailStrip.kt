package com.pocketweibo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage

/**
 * FP-P3: up to [maxCount] small square thumbnails for a feed card, with "+N" on the last one when
 * the post has more images. Coil decodes at the thumbnail pixel size to keep list memory low.
 */
@Composable
fun PostThumbnailStrip(
    imageUris: String,
    modifier: Modifier = Modifier,
    maxCount: Int = 3,
    thumbSize: Dp = 72.dp,
) {
    val context = LocalContext.current
    val paths = remember(imageUris) { PostAttachmentStorage.parseStoredPaths(imageUris) }
    val files = remember(paths) {
        paths.map { PostAttachmentStorage.fileForRelativePath(context, it) }.filter { it.isFile }
    }
    if (files.isEmpty()) return
    val px = with(LocalDensity.current) { thumbSize.roundToPx() }
    val shown = files.take(maxCount)
    val extra = files.size - shown.size
    val stripCd = stringResource(R.string.post_thumbnails_cd, files.size)
    Row(
        modifier = modifier.semantics { contentDescription = stripCd },
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEachIndexed { i, file ->
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file)
                        .size(px, px)
                        .memoryCacheKey("thumb:${file.path}:$px")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (extra > 0 && i == shown.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "+$extra", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
