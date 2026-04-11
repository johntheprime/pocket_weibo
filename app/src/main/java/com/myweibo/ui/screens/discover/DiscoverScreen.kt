package com.myweibo.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myweibo.MyWeiboApp
import com.myweibo.data.local.entity.IdentityEntity
import com.myweibo.ui.components.Avatar
import com.myweibo.ui.components.WeiboTitleBar
import com.myweibo.ui.theme.Background
import com.myweibo.ui.theme.GrayDark
import com.myweibo.ui.theme.GrayLight
import com.myweibo.ui.theme.GrayMiddle
import com.myweibo.ui.theme.WeiboOrange

@Composable
fun DiscoverScreen(
    onPostClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyWeiboApp
    val viewModel: DiscoverViewModel = viewModel(
        factory = DiscoverViewModel.Factory(app.repository)
    )
    
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val trendingIdentities by viewModel.trendingIdentities.collectAsState()
    val trendingPosts by viewModel.trendingPosts.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        WeiboTitleBar(title = "发现")

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("搜索内容、身份", color = GrayMiddle) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GrayMiddle
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除",
                            tint = GrayMiddle
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        if (searchQuery.isEmpty()) {
            TrendingContent(
                trendingIdentities = trendingIdentities,
                trendingPosts = trendingPosts,
                onIdentityClick = { identity ->
                    viewModel.updateSearchQuery(identity.name)
                },
                onPostClick = onPostClick
            )
        } else {
            SearchResultsContent(
                results = searchResults,
                onIdentityClick = { identity ->
                    viewModel.updateSearchQuery(identity.name)
                }
            )
        }
    }
}

@Composable
private fun TrendingContent(
    trendingIdentities: List<IdentityEntity>,
    trendingPosts: List<com.myweibo.data.local.dao.PostWithIdentity>,
    onIdentityClick: (IdentityEntity) -> Unit,
    onPostClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "热门身份",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = GrayDark,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trendingIdentities) { identity ->
                    TrendingIdentityChip(
                        identity = identity,
                        onClick = { onIdentityClick(identity) }
                    )
                }
            }
        }
        
        if (trendingPosts.isNotEmpty()) {
            item {
                Divider(modifier = Modifier.padding(top = 20.dp))
                Text(
                    text = "热门微博",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            
            items(trendingPosts) { post ->
                TrendingPostItem(
                    post = post,
                    onClick = { onPostClick(post.id) }
                )
            }
        }
        
        item {
            Divider(modifier = Modifier.padding(top = 20.dp))
            Text(
                text = "搜索提示",
                fontSize = 14.sp,
                color = GrayMiddle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        
        item {
            SearchHintItem(
                title = "搜索身份",
                description = "输入身份名称，如：苏轼、莎士比亚",
                icon = Icons.Default.Person
            )
        }
        
        item {
            SearchHintItem(
                title = "搜索内容",
                description = "输入微博内容关键词进行搜索",
                icon = Icons.Default.Search
            )
        }
    }
}

@Composable
private fun TrendingIdentityChip(
    identity: IdentityEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF4A90D9)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = identity.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SearchHintItem(
    title: String,
    description: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WeiboOrange,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun TrendingPostItem(
    post: com.myweibo.data.local.dao.PostWithIdentity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Avatar(
                name = post.identityName,
                color = Color(0xFF4A90D9),
                size = 40.dp,
                avatarResName = post.identityAvatarResName
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = post.identityName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = post.content,
                    fontSize = 13.sp,
                    color = GrayDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "${post.likeCount} 赞",
                        fontSize = 12.sp,
                        color = GrayMiddle
                    )
                    Text(
                        text = "${post.commentCount} 评论",
                        fontSize = 12.sp,
                        color = GrayMiddle
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: List<SearchResult>,
    onIdentityClick: (IdentityEntity) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GrayLight,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "未找到相关结果",
                    fontSize = 16.sp,
                    color = GrayMiddle,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            val identityResults = results.filterIsInstance<SearchResult.IdentityResult>()
            val postResults = results.filterIsInstance<SearchResult.PostResult>()
            
            if (identityResults.isNotEmpty()) {
                item {
                    Text(
                        text = "身份",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                items(identityResults) { result ->
                    IdentitySearchItem(
                        identity = result.identity,
                        onClick = { onIdentityClick(result.identity) }
                    )
                }
            }
            
            if (postResults.isNotEmpty()) {
                item {
                    Text(
                        text = "微博",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = GrayDark,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                items(postResults) { result ->
                    PostSearchItem(post = result.post)
                }
            }
        }
    }
}

@Composable
private fun IdentitySearchItem(
    identity: IdentityEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(
                name = identity.name,
                color = Color(0xFF4A90D9),
                size = 44.dp,
                avatarResName = identity.avatarResName
            )
            Column {
                Text(
                    text = identity.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = "历史人物 · 微博达人",
                    fontSize = 12.sp,
                    color = GrayMiddle
                )
            }
        }
    }
}

@Composable
private fun PostSearchItem(post: com.myweibo.data.local.dao.PostWithIdentity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Avatar(
                name = post.identityName,
                color = Color(0xFF4A90D9),
                size = 36.dp,
                avatarResName = post.identityAvatarResName
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.identityName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GrayDark
                )
                Text(
                    text = post.content,
                    fontSize = 13.sp,
                    color = GrayDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
