package com.myweibo

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
import com.myweibo.ui.components.MainTab
import com.myweibo.ui.components.WeiboBottomTabBar
import com.myweibo.ui.screens.compose.ComposeScreen
import com.myweibo.ui.screens.discover.DiscoverScreen
import com.myweibo.ui.screens.home.HomeScreen
import com.myweibo.ui.screens.me.MeScreen
import com.myweibo.ui.screens.me.MyPostsScreen
import com.myweibo.ui.screens.message.MessageScreen
import com.myweibo.ui.theme.MyWeiboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyWeiboTheme {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showCompose && !showMyPosts) {
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
        when {
            showCompose -> {
                ComposeScreen(
                    onDismiss = { showCompose = false },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            showMyPosts -> {
                MyPostsScreen(
                    onBack = { showMyPosts = false },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                when (selectedTab) {
                    MainTab.HOME -> HomeScreen(modifier = Modifier.padding(paddingValues))
                    MainTab.MESSAGE -> MessageScreen(modifier = Modifier.padding(paddingValues))
                    MainTab.DISCOVER -> DiscoverScreen(modifier = Modifier.padding(paddingValues))
                    MainTab.ME -> MeScreen(
                        onNavigateToMyPosts = { showMyPosts = true },
                        modifier = Modifier.padding(paddingValues)
                    )
                    MainTab.PLUS -> {}
                }
            }
        }
    }
}
