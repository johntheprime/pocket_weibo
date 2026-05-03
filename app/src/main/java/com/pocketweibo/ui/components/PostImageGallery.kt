package com.pocketweibo.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage

@Composable
fun PostImageGallery(
    imageUris: String,
    modifier: Modifier = Modifier,
    maxHeight: androidx.compose.ui.unit.Dp = 120.dp,
    enableImageClick: Boolean = false,
    onImageClick: ((index: Int) -> Unit)? = null,
    /** When true (e.g. post detail), opens [onImageClick] on **double** tap instead of single tap. */
    openFullScreenOnDoubleTap: Boolean = false,
    /** Overrides [R.string.post_image_cd] for the same image row (e.g. double-tap hint on detail). */
    imageContentDescription: String? = null
) {
    val context = LocalContext.current
    val paths = remember(imageUris) { PostAttachmentStorage.parseStoredPaths(imageUris) }
    if (paths.isEmpty()) return
    val clickable = enableImageClick && onImageClick != null
    val defaultCd = stringResource(R.string.post_image_cd)
    val contentDescription = imageContentDescription ?: defaultCd
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(paths, key = { _, rel -> rel }) { index, rel ->
            val model: Any? = remember(rel) {
                when {
                    rel.startsWith("content:") -> Uri.parse(rel)
                    rel.startsWith("file:") -> Uri.parse(rel)
                    else -> {
                        val f = PostAttachmentStorage.fileForRelativePath(context, rel)
                        if (f.isFile) f else null
                    }
                }
            }
            if (model != null) {
                val interactionModifier = when {
                    !clickable -> Modifier
                    openFullScreenOnDoubleTap -> Modifier.pointerInput(rel, index) {
                        detectTapGestures(onDoubleTap = { onImageClick!!.invoke(index) })
                    }
                    else -> Modifier.clickable { onImageClick!!.invoke(index) }
                }
                val boxModifier = Modifier
                    .size(maxHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .then(interactionModifier)
                Box(modifier = boxModifier) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
