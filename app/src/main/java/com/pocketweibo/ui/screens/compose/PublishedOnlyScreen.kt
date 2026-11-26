package com.pocketweibo.ui.screens.compose

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketweibo.PocketWeiboApp
import com.pocketweibo.R
import com.pocketweibo.data.media.PostAttachmentStorage
import com.pocketweibo.ui.components.Avatar
import com.pocketweibo.ui.components.RelativeTimeText
import com.pocketweibo.ui.theme.Background
import com.pocketweibo.ui.theme.GrayDark
import com.pocketweibo.ui.theme.GrayLight
import com.pocketweibo.ui.theme.GrayMiddle
import com.pocketweibo.ui.theme.Surface
import com.pocketweibo.ui.theme.WeiboOrange
import com.pocketweibo.ui.util.RelativeTimePreset

/** How long the privacy-shortcut result screen stays visible before exiting automatically. */
internal const val PUBLISHED_ONLY_AUTO_CLOSE_MS = 1_000L

/**
 * Privacy-shortcut end screen: shows only the post that was just published. No feed, search,
 * topic links or bottom tabs are reachable from here.
 * Exits automatically after [PUBLISHED_ONLY_AUTO_CLOSE_MS]; "Done" exits immediately.
 */
@Composable
fun PublishedOnlyScreen(
    postId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    // Keyed on postId only: recomposition (e.g. the posts Flow emitting) must not restart the timer.
    // If the user taps Done first, the screen leaves composition and this coroutine is cancelled.
    LaunchedEffect(postId) {
        delay(PUBLISHED_ONLY_AUTO_CLOSE_MS)
        currentOnDone()
    }
    val app = context.applicationContext as PocketWeiboApp
    val posts by app.repository.allPosts.collectAsStateWithLifecycle(initialValue = emptyList())
    val post = remember(posts, postId) { posts.firstOrNull { it.id == postId } }
    val imageCount = remember(post) {
        post?.let { PostAttachmentStorage.parseStoredPaths(it.imageUris).size } ?: 0
    }
    val authorName = post?.identityName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.deleted_identity_label)

    Surface(modifier = modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.private_publish_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WeiboOrange,
                        disabledContainerColor = GrayLight
                    )
                ) {
                    Text(stringResource(R.string.private_publish_done))
                }
            }

            Text(
                text = stringResource(R.string.private_publish_hint),
                fontSize = 13.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (post == null) {
                    Text(
                        text = stringResource(R.string.private_publish_unavailable),
                        fontSize = 14.sp,
                        color = GrayMiddle,
                        modifier = Modifier.padding(top = 48.dp)
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(
                                    name = authorName,
                                    color = WeiboOrange,
                                    size = 36.dp,
                                    avatarResName = post.identityAvatarResName,
                                    customAvatarUri = post.identityCustomAvatarUri
                                )
                                Column(modifier = Modifier.padding(start = 10.dp)) {
                                    Text(
                                        text = authorName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GrayDark
                                    )
                                    RelativeTimeText(
                                        timestamp = post.createdAt,
                                        preset = RelativeTimePreset.FeedCard,
                                        fontSize = 12.sp,
                                        color = GrayMiddle
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (post.content.isNotBlank()) {
                                Text(
                                    text = post.content,
                                    fontSize = 15.sp,
                                    color = GrayDark
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.private_publish_images_only),
                                    fontSize = 14.sp,
                                    color = GrayMiddle
                                )
                            }
                            if (imageCount > 0) {
                                Text(
                                    text = stringResource(R.string.private_publish_image_count, imageCount),
                                    fontSize = 12.sp,
                                    color = GrayMiddle,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
