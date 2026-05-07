package com.pocketweibo.ui.screens.photo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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

private const val PhotoZoomMaxScale = 4f

private fun clampPhotoPan(offset: Offset, scale: Float, widthPx: Float, heightPx: Float): Offset {
    if (scale <= 1f || widthPx <= 0f || heightPx <= 0f) return Offset.Zero
    val maxX = widthPx * (scale - 1f) / 2f
    val maxY = heightPx * (scale - 1f) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY)
    )
}

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
    var scale by remember(post.id) { mutableFloatStateOf(1f) }
    var offset by remember(post.id) { mutableStateOf(Offset.Zero) }
    val model = rememberFirstImageModel(post.imageUris)
    val displayName = identityDisplayName(post.identityName)
    val timeText = resources.formatRelativeTime(post.createdAt, RelativeTimePreset.FeedCard)

    BoxWithConstraints(
        modifier = modifier.height(imageHeight)
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        val nestedScrollConnection = remember(scale) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (scale > 1.02f && source == NestedScrollSource.Drag) {
                        return available
                    }
                    return Offset.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .clip(RectangleShape)
        ) {
            if (model != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.photo_feed_image_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(post.id, wPx, hPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, PhotoZoomMaxScale)
                                scale = newScale
                                offset = if (newScale <= 1f) {
                                    Offset.Zero
                                } else {
                                    clampPhotoPan(offset + pan, newScale, wPx, hPx)
                                }
                            }
                        }
                        .pointerInput(post.id, overlayVisible) {
                            detectTapGestures(onTap = { overlayVisible = !overlayVisible })
                        }
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

            if (overlayVisible && model != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.4f to Color.Black.copy(alpha = 0.35f),
                                    1f to Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                        .clickable { overlayVisible = false }
                        .padding(horizontal = 16.dp)
                        .padding(top = 28.dp, bottom = 14.dp),
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
