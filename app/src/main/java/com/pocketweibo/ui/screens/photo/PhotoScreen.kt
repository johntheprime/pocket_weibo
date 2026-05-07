package com.pocketweibo.ui.screens.photo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.data.local.dao.PostWithIdentity
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.components.WeiboTitleBar
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.util.RelativeTimePreset
import com.pocketweibo.ui.util.formatRelativeTime
import com.pocketweibo.ui.util.identityDisplayName

@Composable
fun PhotoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketWeiboApp
    val posts by app.repository.allPosts.collectAsState(initial = emptyList())
    val photoPosts = remember(posts) {
        posts
            .filter { PostAttachmentStorage.parseStoredPaths(it.imageUris).isNotEmpty() }
            .sortedByDescending { it.createdAt }
    }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val cardHeight = remember(screenHeightDp) {
        (screenHeightDp * 0.56f).dp.coerceIn(240.dp, 540.dp)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(title = stringResource(R.string.title_photo))
        Divider(thickness = 0.5.dp)
        if (photoPosts.isEmpty()) {
            PhotoEmptyState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(photoPosts, key = { it.id }) { post ->
                    PhotoFeedCard(
                        post = post,
                        imageHeight = cardHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(thickness = 0.5.dp, color = Color(0xFFE8E8E8))
                }
            }
        }
    }
}

@Composable
private fun PhotoFeedCard(
    post: PostWithIdentity,
    imageHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = context.resources
    var overlayVisible by remember(post.id) { mutableStateOf(true) }
    val model = rememberFirstImageModel(post.imageUris)
    val displayName = identityDisplayName(post.identityName)
    val timeText = resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard)

    Box(
        modifier = modifier
            .height(imageHeight)
            .clickable { overlayVisible = !overlayVisible }
    ) {
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.photo_feed_image_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = GrayMiddle,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        if (overlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.12f),
                                0.72f to Color.Black.copy(alpha = 0.5f),
                                1f to Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
                if (post.content.isNotBlank()) {
                    Text(
                        text = post.content,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFirstImageModel(imageUris: String): Any? {
    val context = LocalContext.current
    val paths = remember(imageUris) { PostAttachmentStorage.parseStoredPaths(imageUris) }
    val rel = paths.firstOrNull() ?: return null
    return remember(rel) {
        when {
            rel.startsWith("content:") -> Uri.parse(rel)
            rel.startsWith("file:") -> Uri.parse(rel)
            else -> {
                val f = PostAttachmentStorage.fileForRelativePath(context, rel)
                if (f.isFile) f else null
            }
        }
    }
}

@Composable
private fun PhotoEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = GrayMiddle,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.photo_empty_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666),
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.photo_empty_subtitle),
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
