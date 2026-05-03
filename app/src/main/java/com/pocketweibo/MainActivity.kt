package com.pocketweibo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.pocketweibo.ui.screens.me.MeSettingsScreen
import com.pocketweibo.ui.screens.me.MyPostsScreen
import com.pocketweibo.ui.screens.message.MessageScreen
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.ui.ComposeIntentViewModel
import com.pocketweibo.ui.theme.PocketWeiboTheme
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private val composeIntentViewModel: ComposeIntentViewModel by viewModels()

    companion object {
        const val EXTRA_OPEN_POST_ID = "com.pocketweibo.EXTRA_OPEN_POST_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        runBlocking { UiPreferences.applyStored(applicationContext) }
        super.onCreate(savedInstanceState)
        processLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            PocketWeiboTheme(darkTheme = darkTheme) {
                MainScreen(composeIntentViewModel = composeIntentViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        processLaunchIntent(intent)
    }

    private fun processLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val postId = intent.getLongExtra(EXTRA_OPEN_POST_ID, -1L)
        if (postId > 0L) {
            composeIntentViewModel.requestOpenPost(postId)
            return
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            composeIntentViewModel.offerShareText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

@Composable
fun MainScreen(composeIntentViewModel: ComposeIntentViewModel) {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var showCompose by remember { mutableStateOf(false) }
    var showMyPosts by remember { mutableStateOf(false) }
    var showIdentityList by remember { mutableStateOf(false) }
    var identityDetailId by remember { mutableStateOf<Long?>(null) }
    var postDetailId by remember { mutableStateOf<Long?>(null) }
    var showMeSettings by remember { mutableStateOf(false) }

    val pendingShareText by composeIntentViewModel.pendingShareText.collectAsState()
    val openPostId by composeIntentViewModel.openPostId.collectAsState()

    LaunchedEffect(pendingShareText) {
        if (!pendingShareText.isNullOrBlank()) {
            showCompose = true
        }
    }
    LaunchedEffect(openPostId) {
        val id = openPostId ?: return@LaunchedEffect
        postDetailId = id
        composeIntentViewModel.consumeOpenPost()
    }

    fun navigateBack() {
        when {
            identityDetailId != null -> identityDetailId = null
            showIdentityList -> showIdentityList = false
            postDetailId != null -> postDetailId = null
            showMyPosts -> showMyPosts = false
            showCompose -> showCompose = false
            showMeSettings -> showMeSettings = false
        }
    }

    val canSwipeBack = identityDetailId != null || showIdentityList || postDetailId != null || showMyPosts || showCompose || showMeSettings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showCompose && !showMyPosts && !showIdentityList && identityDetailId == null && postDetailId == null && !showMeSettings) {
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
                        initialShareText = pendingShareText.orEmpty(),
                        onConsumeInitialShare = { composeIntentViewModel.consumeShareText() },
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
                showMeSettings -> {
                    MeSettingsScreen(
                        onBack = { showMeSettings = false },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                else -> {
                    when (selectedTab) {
                        MainTab.HOME -> HomeScreen(
                            onPostClick = { postId -> postDetailId = postId },
                            onOpenSettings = { showMeSettings = true },
                            onNavigateToDiscover = { selectedTab = MainTab.DISCOVER },
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
                            onNavigateToSettings = { showMeSettings = true },
                            onEditActiveIdentity = { id -> identityDetailId = id },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.PLUS -> {}
                    }
                }
            }
        }
    }
}
