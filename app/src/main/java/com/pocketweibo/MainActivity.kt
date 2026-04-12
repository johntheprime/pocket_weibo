package com.pocketweibo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pocketweibo.ui.components.MainTab
import com.pocketweibo.ui.components.SwipeBackHandler
import com.pocketweibo.ui.components.WeiboBottomTabBar
import com.pocketweibo.ui.screens.compose.ComposeScreen
import com.pocketweibo.ui.screens.detail.PostDetailScreen
import com.pocketweibo.ui.screens.discover.DiscoverScreen
import com.pocketweibo.ui.screens.home.HomeScreen
import com.pocketweibo.ui.screens.identity.IdentityDetailScreen
import com.pocketweibo.ui.screens.identity.IdentityListScreen
import com.pocketweibo.ui.screens.me.MeScreen
import com.pocketweibo.ui.screens.me.MyPostsScreen
import com.pocketweibo.ui.screens.message.MessageScreen
import com.pocketweibo.ui.theme.PocketWeiboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketWeiboTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var showCompose by remember { mutableStateOf(false) }
    var showMyPosts by remember { mutableStateOf(false) }
    var showIdentityList by remember { mutableStateOf(false) }
    var identityDetailId by remember { mutableStateOf<Long?>(null) }
    var postDetailId by remember { mutableStateOf<Long?>(null) }

    fun navigateBack() {
        when {
            identityDetailId != null -> identityDetailId = null
            showIdentityList -> showIdentityList = false
            postDetailId != null -> postDetailId = null
            showMyPosts -> showMyPosts = false
            showCompose -> showCompose = false
        }
    }

    val canSwipeBack = identityDetailId != null || showIdentityList || postDetailId != null || showMyPosts || showCompose

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showCompose && !showMyPosts && !showIdentityList && identityDetailId == null && postDetailId == null) {
                WeiboBottomTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (tab != MainTab.PLUS) {
                            selectedTab = tab
                        }
                    },
                    onPlusClick = { showCompose = true }
                )
            }
        }
    ) { paddingValues ->
        SwipeBackHandler(
            onSwipeBack = { navigateBack() },
            enabled = canSwipeBack
        ) {
            when {
                identityDetailId != null -> {
                    IdentityDetailScreen(
                        identityId = identityDetailId!!,
                        onBack = { identityDetailId = null },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showIdentityList -> {
                    IdentityListScreen(
                        onBack = { showIdentityList = false },
                        onIdentityClick = { id -> identityDetailId = id },
                        onAddIdentity = { identityDetailId = 0L },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                postDetailId != null -> {
                    PostDetailScreen(
                        postId = postDetailId!!,
                        onBack = { postDetailId = null },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showCompose -> {
                    ComposeScreen(
                        onDismiss = { showCompose = false },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showMyPosts -> {
                    MyPostsScreen(
                        onBack = { showMyPosts = false },
                        onPostClick = { postId -> postDetailId = postId },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                else -> {
                    when (selectedTab) {
                        MainTab.HOME -> HomeScreen(
                            onPostClick = { postId -> postDetailId = postId },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.MESSAGE -> MessageScreen(modifier = Modifier.padding(paddingValues))
                        MainTab.DISCOVER -> DiscoverScreen(
                            onPostClick = { postId -> postDetailId = postId },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.ME -> MeScreen(
                            onNavigateToMyPosts = { showMyPosts = true },
                            onNavigateToIdentities = { showIdentityList = true },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.PLUS -> {}
                    }
                }
            }
        }
    }
}
