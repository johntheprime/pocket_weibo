package com.myweibo.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myweibo.MyWeiboApp
import com.myweibo.ui.components.PostCard
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.WeiboOrange

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val posts by app.repository.allPosts.collectAsState(initial = emptyList())
    var showCategoryDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(
            title = "首页",
            showDropdown = true,
            onTitleClick = { showCategoryDropdown = !showCategoryDropdown },
            leftIcon = {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = "搜索",
                    tint = WeiboOrange,
                    modifier = Modifier.size(24.dp)
                )
            },
            rightIcon = {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = WeiboOrange,
                    modifier = Modifier.size(24.dp)
                )
            }
        )

        Divider(thickness = 0.5.dp)

        if (posts.isEmpty()) {
            EmptyFeed()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onLikeClick = { /* TODO */ },
                        onCommentClick = { /* TODO */ },
                        onShareClick = { /* TODO */ }
                    )
                    Divider(thickness = 6.dp, color = Background)
                }
            }
        }
    }
}

@Composable
private fun EmptyFeed() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "还没有微博",
                fontSize = 16.sp,
                color = GrayMiddle
            )
            Text(
                text = "点击中间+号发布第一条",
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
