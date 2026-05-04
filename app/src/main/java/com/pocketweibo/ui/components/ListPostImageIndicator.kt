package com.pocketweibo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.theme.GrayMiddle

/**
 * Subtle list hint that a post has attachments (detail still shows the gallery).
 * Icon-only, low contrast — avoids a full extra line or strong accent color.
 */
@Composable
fun ListPostImageIndicator(
    imageUris: String,
    modifier: Modifier = Modifier
) {
    val hasImages = remember(imageUris) {
        PostAttachmentStorage.parseStoredPaths(imageUris).isNotEmpty()
    }
    if (!hasImages) return
    Icon(
        imageVector = Icons.Default.Image,
        contentDescription = stringResource(R.string.post_list_image_cd),
        tint = GrayMiddle.copy(alpha = 0.38f),
        modifier = modifier.size(13.dp)
    )
}
