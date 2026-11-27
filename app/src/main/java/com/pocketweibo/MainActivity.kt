package com.pocketweibo

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pocketweibo.ui.components.MainTab
import com.pocketweibo.ui.components.SwipeBackHandler
import com.pocketweibo.ui.components.WeiboBottomTabBar
import com.pocketweibo.ui.screens.compose.ComposeScreen
import com.pocketweibo.ui.screens.compose.PublishedOnlyScreen
import com.pocketweibo.ui.screens.detail.PostDetailScreen
import com.pocketweibo.ui.screens.topics.TopicManageScreen
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
import com.pocketweibo.ui.util.findActivity
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val composeIntentViewModel: ComposeIntentViewModel by viewModels()

    companion object {
        const val EXTRA_OPEN_POST_ID = "com.pocketweibo.EXTRA_OPEN_POST_ID"
        /** Launcher long-press shortcut on the app icon (logo): isolated private composer. */
        const val ACTION_NEW_POST = "com.pocketweibo.action.NEW_POST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Roadmap P1: no runBlocking on the main thread. Apply the mirrored language synchronously
        // (cheap SharedPreferences read); the first launch after upgrade back-fills it asynchronously.
        UiPreferences.languageCodeFromMirror(applicationContext)?.let { UiPreferences.applyLanguageCode(it) }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) { UiPreferences.refreshLanguageMirror(applicationContext) }
        // False means this instance relaunched itself into a clean task; do not build UI here.
        if (!processLaunchIntent(intent)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val dynamicColor by UiPreferences.dynamicColorFlow(applicationContext)
                .collectAsStateWithLifecycle(initialValue = false)
            PocketWeiboTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                MainScreen(
                    composeIntentViewModel = composeIntentViewModel,
                    onExitPrivateMode = { finishAffinity() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!processLaunchIntent(intent)) finish()
    }

    /** Returns false only when the shortcut intent must be relaunched with a cleared task. */
    private fun processLaunchIntent(intent: Intent?): Boolean {
        if (intent == null) return true
        if (intent.action == ACTION_NEW_POST) {
            if (intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK == 0) {
                // Static shortcut intents cannot declare flags; relaunch into a fresh task so
                // a feed that was already open is never left behind this composer.
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = ACTION_NEW_POST
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
                return false
            }
            composeIntentViewModel.requestNewPostCompose(privateMode = true)
            return true
        }
        val postId = intent.getLongExtra(EXTRA_OPEN_POST_ID, -1L)
        if (postId > 0L) {
            composeIntentViewModel.requestOpenPost(postId)
            return true
        }
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            composeIntentViewModel.offerShareText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        return true
    }
}

@Composable
fun MainScreen(
    composeIntentViewModel: ComposeIntentViewModel,
    onExitPrivateMode: () -> Unit = {}
) {
    val homeListState = rememberLazyListState()
    val topicListState = rememberLazyListState()
    val topicTimelineListState = rememberLazyListState()
    val todoListState = rememberLazyListState()
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var selectedTopicKey by remember { mutableStateOf<String?>(null) }
    var showTopicManage by remember { mutableStateOf(false) }
    var topicQuery by remember { mutableStateOf("") }
    var topicSection by remember { mutableStateOf(TopicsSection.TOPICS) }
    var showTopicCompleted by remember { mutableStateOf(false) }
    /** Topic a “Post again” draft came from; return there after publishing. */
    var topicComposeOriginKey by remember { mutableStateOf<String?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    /** True only for the isolated launcher-shortcut compose flow; never shows the feed. */
    var privateCompose by remember { mutableStateOf(false) }
    /** Set after a private publish so only the new post is shown before exiting. */
    var publishedPostId by remember { mutableStateOf<Long?>(null) }
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

    val pendingShareText by composeIntentViewModel.pendingShareText.collectAsStateWithLifecycle()
    val openPostId by composeIntentViewModel.openPostId.collectAsStateWithLifecycle()
    val composeRequests by composeIntentViewModel.composeRequests.collectAsStateWithLifecycle()

    // Private shortcut mode blocks screenshots and recents thumbnails for the whole flow.
    val context = LocalContext.current
    DisposableEffect(privateCompose) {
        val window = context.findActivity()?.window
        if (privateCompose) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(pendingShareText) {
        if (pendingShareText.isNullOrBlank()) return@LaunchedEffect
        if (privateCompose) {
            // An incoming share must not switch the isolated flow back to a feed-returning compose.
            composeIntentViewModel.consumeShareText()
            return@LaunchedEffect
        }
        privateCompose = false
        publishedPostId = null
        showCompose = true
    }
    // Launcher long-press on the app icon (logo) → "New post" shortcut: close any open
    // detail/list screen and open the composer in an isolated task with no feed behind it.
    LaunchedEffect(composeRequests) {
        if (composeRequests <= 0) return@LaunchedEffect
        privateCompose = composeIntentViewModel.privateCompose.value
        composeIntentViewModel.consumePrivateCompose()
        publishedPostId = null
        identityDetailId = null
        showIdentityList = false
        postDetailId = null
        selectedTopicKey = null
        showTopicManage = false
        topicComposeOriginKey = null
        showIdentitySchedule = false
        showMeSettings = false
        showCompose = true
    }
    LaunchedEffect(openPostId, privateCompose) {
        val id = openPostId ?: return@LaunchedEffect
        composeIntentViewModel.consumeOpenPost()
        // Reminder/deep-link post opens are ignored while the private shortcut is active.
        if (privateCompose) return@LaunchedEffect
        postDetailId = id
    }

    fun navigateBack() {
        if (privateCompose) {
            onExitPrivateMode()
            return
        }
        // Pop the most recently opened layer first: a post opened from a topic returns to
        // that topic; topic management returns to the Discover list.
        when {
            postDetailId != null -> postDetailId = null
            selectedTopicKey != null -> selectedTopicKey = null
            showTopicManage -> showTopicManage = false
            identityDetailId != null -> identityDetailId = null
            showIdentityList -> showIdentityList = false
            showCompose -> showCompose = false
            showIdentitySchedule -> showIdentitySchedule = false
            showMeSettings -> showMeSettings = false
        }
    }

    // Private mode disables in-app Back/swipe entirely: system Back finishes the isolated task.
    val canSwipeBack = !privateCompose && (postDetailId != null || selectedTopicKey != null ||
        showTopicManage || identityDetailId != null || showIdentityList || showCompose ||
        showIdentitySchedule || showMeSettings)

    /** Opens a topic referenced by a hashtag; back returns to the list/search it came from. */
    fun openTopic(displayName: String) {
        postDetailId = null
        selectedTab = MainTab.DISCOVER
        selectedTopicKey = displayName.lowercase(Locale.ROOT)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!privateCompose && publishedPostId == null && !showCompose && !showIdentityList &&
                identityDetailId == null && postDetailId == null && selectedTopicKey == null &&
                !showTopicManage && !showIdentitySchedule && !showMeSettings
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
                    onPlusClick = {
                        privateCompose = false
                        publishedPostId = null
                        showCompose = true
                    }
                )
            }
        }
    ) { paddingValues ->
        SwipeBackHandler(
            onSwipeBack = { navigateBack() },
            enabled = canSwipeBack
        ) {
            when {
                publishedPostId != null -> {
                    PublishedOnlyScreen(
                        postId = publishedPostId!!,
                        onDone = onExitPrivateMode,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
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
                        onTopicClick = ::openTopic,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                showCompose -> {
                    ComposeScreen(
                        onDismiss = {
                            showCompose = false
                            topicComposeOriginKey = null
                            // Cancelling a private compose exits instead of revealing the feed.
                            if (privateCompose && publishedPostId == null) onExitPrivateMode()
                        },
                        initialShareText = pendingShareText.orEmpty(),
                        onConsumeInitialShare = { composeIntentViewModel.consumeShareText() },
                        onPublished = { newPostId ->
                            if (privateCompose) {
                                publishedPostId = newPostId
                            } else {
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
                            }
                        },
                        privateMode = privateCompose,
                        onExitPrivateMode = onExitPrivateMode,
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
                showTopicManage -> {
                    TopicManageScreen(
                        onBack = { showTopicManage = false },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                else -> {
                    when (selectedTab) {
                        MainTab.HOME ->                         HomeScreen(
                            onPostClick = { postId -> postDetailId = postId },
                            onTopicClick = ::openTopic,
                            onOpenSettings = { showMeSettings = true },
                            onNavigateToDiscover = { selectedTab = MainTab.DISCOVER },
                            onCompose = { showCompose = true },
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
                            topicQuery = topicQuery,
                            onTopicQueryChange = { topicQuery = it },
                            section = topicSection,
                            onSectionChange = { topicSection = it },
                            showCompleted = showTopicCompleted,
                            onToggleCompleted = { showTopicCompleted = !showTopicCompleted },
                            onOpenTopic = { key -> selectedTopicKey = key },
                            onCloseTopic = { selectedTopicKey = null },
                            onTopicClick = ::openTopic,
                            onPostClick = { postId -> postDetailId = postId },
                            onDiscussAgain = { key, name ->
                                privateCompose = false
                                publishedPostId = null
                                topicComposeOriginKey = key
                                composeIntentViewModel.offerShareText(
                                    "#$name ",
                                    keepTrailingWhitespace = true
                                )
                                selectedTopicKey = null
                                showCompose = true
                            },
                            onOpenManage = { showTopicManage = true },
                            topicListState = topicListState,
                            topicTimelineListState = topicTimelineListState,
                            todoListState = todoListState,
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
