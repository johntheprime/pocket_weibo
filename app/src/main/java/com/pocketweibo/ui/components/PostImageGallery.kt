package com.pocketweibo.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    maxHeight: androidx.compose.ui.unit.Dp = 120.dp
) {
    val context = LocalContext.current
    val paths = remember(imageUris) { PostAttachmentStorage.parseStoredPaths(imageUris) }
    if (paths.isEmpty()) return
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(paths, key = { it }) { rel ->
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
                Box(
                    modifier = Modifier
                        .size(maxHeight)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(model)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.post_image_cd),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
