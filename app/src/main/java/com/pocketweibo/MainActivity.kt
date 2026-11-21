package com.pocketweibo

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pocketweibo.ui.components.MainTab
import com.pocketweibo.ui.components.SwipeBackHandler
import com.pocketweibo.ui.components.WeiboBottomTabBar
import com.pocketweibo.ui.screens.compose.ComposeScreen
import com.pocketweibo.ui.screens.detail.PostDetailScreen
import com.pocketweibo.ui.screens.topics.TopicsScreen
import com.pocketweibo.ui.screens.topics.TopicsSection
import com.pocketweibo.ui.screens.home.HomeScreen
import com.pocketweibo.ui.screens.identity.IdentityDetailScreen
import com.pocketweibo.ui.screens.identity.IdentityListScreen
import com.pocketweibo.ui.screens.me.MeScreen
import com.pocketweibo.ui.screens.me.IdentityScheduleScreen
import com.pocketweibo.ui.screens.me.MeSettingsScreen
import com.pocketweibo.ui.screens.photo.PhotoScreen
import com.pocketweibo.data.prefs.UiPreferences
import com.pocketweibo.ui.ComposeIntentViewModel
import com.pocketweibo.ui.theme.PocketWeiboTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    private val composeIntentViewModel: ComposeIntentViewModel by viewModels()

    companion object {
        const val EXTRA_OPEN_POST_ID = "com.pocketweibo.EXTRA_OPEN_POST_ID"
        /** Launcher long-press shortcut on the app icon (logo): jump straight into a new post. */
        const val ACTION_NEW_POST = "com.pocketweibo.action.NEW_POST"
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
        if (intent.action == ACTION_NEW_POST) {
            composeIntentViewModel.requestNewPostCompose()
            return
        }
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
    val homeListState = rememberLazyListState()
    val topicListState = rememberLazyListState()
    val discoverSearchListState = rememberLazyListState()
    val topicTimelineListState = rememberLazyListState()
    val todoListState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedTopicKey by remember { mutableStateOf<String?>(null) }
    var showTopicGlobalSearch by remember { mutableStateOf(false) }
    var topicQuery by remember { mutableStateOf("") }
    var topicSection by remember { mutableStateOf(TopicsSection.TOPICS) }
    var showTopicCompleted by remember { mutableStateOf(false) }
    /** Topic a “Post again” draft came from; return there after publishing. */
    var topicComposeOriginKey by remember { mutableStateOf<String?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    var showIdentityList by remember { mutableStateOf(false) }
    var identityDetailId by remember { mutableStateOf<Long?>(null) }
    var postDetailId by remember { mutableStateOf<Long?>(null) }
    var showMeSettings by remember { mutableStateOf(false) }
    var showIdentitySchedule by remember { mutableStateOf(false) }
    var homeScrollToLatestSignal by remember { mutableStateOf(0) }
    /** Last `homeScrollToLatestSignal` value for which HomeScreen already ran the refresh affordance. */
    var homeScrollToLatestConsumed by remember { mutableStateOf(0) }
    var lastHomeTabTapUptime by remember { mutableStateOf(0L) }
    val mainScope = rememberCoroutineScope()

    val pendingShareText by composeIntentViewModel.pendingShareText.collectAsState()
    val openPostId by composeIntentViewModel.openPostId.collectAsState()
    val composeRequests by composeIntentViewModel.composeRequests.collectAsState()

    LaunchedEffect(pendingShareText) {
        if (!pendingShareText.isNullOrBlank()) {
            showCompose = true
        }
    }
    // Launcher long-press on the app icon (logo) → "New post" shortcut: close any open
    // detail/list screen and open the composer.
    LaunchedEffect(composeRequests) {
        if (composeRequests <= 0) return@LaunchedEffect
        identityDetailId = null
        showIdentityList = false
        postDetailId = null
        selectedTopicKey = null
        showTopicGlobalSearch = false
        topicComposeOriginKey = null
        showIdentitySchedule = false
        showMeSettings = false
        showCompose = true
    }
    LaunchedEffect(openPostId) {
        val id = openPostId ?: return@LaunchedEffect
        postDetailId = id
        composeIntentViewModel.consumeOpenPost()
    }

    fun navigateBack() {
        // Pop the most recently opened layer first: a post opened from a topic returns to
        // that topic; a topic opened from search returns to search; search returns to the list.
        when {
            postDetailId != null -> postDetailId = null
            selectedTopicKey != null -> selectedTopicKey = null
            showTopicGlobalSearch -> showTopicGlobalSearch = false
            identityDetailId != null -> identityDetailId = null
            showIdentityList -> showIdentityList = false
            showCompose -> showCompose = false
            showIdentitySchedule -> showIdentitySchedule = false
            showMeSettings -> showMeSettings = false
        }
    }

    val canSwipeBack = postDetailId != null || selectedTopicKey != null || showTopicGlobalSearch ||
        identityDetailId != null || showIdentityList || showCompose || showIdentitySchedule || showMeSettings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!showCompose && !showIdentityList && identityDetailId == null && postDetailId == null &&
                selectedTopicKey == null && !showTopicGlobalSearch && !showIdentitySchedule && !showMeSettings
            ) {
                WeiboBottomTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (tab != MainTab.PLUS) {
                            selectedTab = tab
                        }
                    },
                    onHomeTabClick = {
                        val now = SystemClock.uptimeMillis()
                        if (selectedTab == MainTab.HOME) {
                            if (lastHomeTabTapUptime != 0L && now - lastHomeTabTapUptime < 400L) {
                                mainScope.launch {
                                    homeListState.scrollToItem(0)
                                }
                                homeScrollToLatestSignal++
                                lastHomeTabTapUptime = 0L
                            } else {
                                lastHomeTabTapUptime = now
                            }
                        } else {
                            selectedTab = MainTab.HOME
                            lastHomeTabTapUptime = 0L
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
                        onDismiss = {
                            showCompose = false
                            topicComposeOriginKey = null
                        },
                        initialShareText = pendingShareText.orEmpty(),
                        onConsumeInitialShare = { composeIntentViewModel.consumeShareText() },
                        onPostPublished = {
                            val originKey = topicComposeOriginKey
                            topicComposeOriginKey = null
                            if (originKey != null) {
                                selectedTab = MainTab.DISCOVER
                                selectedTopicKey = originKey
                            } else {
                                selectedTab = MainTab.HOME
                                mainScope.launch {
                                    homeListState.scrollToItem(0)
                                }
                                homeScrollToLatestSignal++
                            }
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showIdentitySchedule -> {
                    IdentityScheduleScreen(
                        onBack = { showIdentitySchedule = false },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showMeSettings -> {
                    MeSettingsScreen(
                        onBack = { showMeSettings = false },
                        onOpenIdentities = {
                            showMeSettings = false
                            showIdentityList = true
                        },
                        onOpenSchedule = {
                            showMeSettings = false
                            showIdentitySchedule = true
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                else -> {
                    when (selectedTab) {
                        MainTab.HOME ->                         HomeScreen(
                            onPostClick = { postId -> postDetailId = postId },
                            onOpenSettings = { showMeSettings = true },
                            onNavigateToDiscover = { selectedTab = MainTab.DISCOVER },
                            listState = homeListState,
                            scrollToLatestSignal = homeScrollToLatestSignal,
                            scrollToLatestConsumedSignal = homeScrollToLatestConsumed,
                            onScrollToLatestConsumed = {
                                homeScrollToLatestConsumed = homeScrollToLatestSignal
                            },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.PHOTO -> PhotoScreen(modifier = Modifier.padding(paddingValues))
                        MainTab.DISCOVER -> TopicsScreen(
                            selectedTopicKey = selectedTopicKey,
                            showGlobalSearch = showTopicGlobalSearch,
                            onOpenSearch = { showTopicGlobalSearch = true },
                            onCloseSearch = { showTopicGlobalSearch = false },
                            topicQuery = topicQuery,
                            onTopicQueryChange = { topicQuery = it },
                            section = topicSection,
                            onSectionChange = { topicSection = it },
                            showCompleted = showTopicCompleted,
                            onToggleCompleted = { showTopicCompleted = !showTopicCompleted },
                            onOpenTopic = { key -> selectedTopicKey = key },
                            onCloseTopic = { selectedTopicKey = null },
                            onPostClick = { postId -> postDetailId = postId },
                            onDiscussAgain = { key, name ->
                                topicComposeOriginKey = key
                                composeIntentViewModel.offerShareText("#$name ")
                                selectedTopicKey = null
                                showCompose = true
                            },
                            topicListState = topicListState,
                            topicTimelineListState = topicTimelineListState,
                            todoListState = todoListState,
                            searchListState = discoverSearchListState,
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.ME -> MeScreen(
                            onNavigateToSettings = { showMeSettings = true },
                            onEditActiveIdentity = { id -> identityDetailId = id },
                            onOpenPost = { postId -> postDetailId = postId },
                            modifier = Modifier.padding(paddingValues)
                        )
                        MainTab.PLUS -> {}
                    }
                }
            }
        }
    }
}
