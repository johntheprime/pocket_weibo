package com.pocketweibo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.theme.WeiboOrange

/** Compact hint when list rows hide thumbnails; detail still shows the gallery. */
@Composable
fun ListPostImageIndicator(
    imageUris: String,
    modifier: Modifier = Modifier
) {
    val hasImages = remember(imageUris) {
        PostAttachmentStorage.parseStoredPaths(imageUris).isNotEmpty()
    }
    if (!hasImages) return
    val cd = stringResource(R.string.post_list_image_cd)
    Surface(
        shape = RoundedCornerShape(50),
        color = WeiboOrange.copy(alpha = 0.1f),
        modifier = modifier.semantics { contentDescription = cd }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = WeiboOrange.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(R.string.post_list_image_badge),
                fontSize = 11.sp,
                color = WeiboOrange.copy(alpha = 0.9f)
            )
        }
    }
}
