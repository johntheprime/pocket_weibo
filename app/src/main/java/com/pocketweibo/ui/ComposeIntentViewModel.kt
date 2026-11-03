package com.pocketweibo.ui

import androidx.lifecycle.ViewModel
import com.pocketweibo.ui.util.clipComposeText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries [Intent.ACTION_SEND] text into [com.pocketweibo.ui.screens.compose.ComposeScreen]
 * and deep-link post ids (e.g. from reminder notifications).
 */
class ComposeIntentViewModel : ViewModel() {
    private val _pendingShareText = MutableStateFlow<String?>(null)
    val pendingShareText: StateFlow<String?> = _pendingShareText.asStateFlow()

    private val _openPostId = MutableStateFlow<Long?>(null)
    val openPostId: StateFlow<Long?> = _openPostId.asStateFlow()

    /** Incremented for every "new post" request (launcher shortcut long-press on the app icon / logo). */
    private val _composeRequests = MutableStateFlow(0)
    val composeRequests: StateFlow<Int> = _composeRequests.asStateFlow()

    fun offerShareText(text: String?) {
        val t = text?.trim().orEmpty()
        _pendingShareText.value = if (t.isEmpty()) null else clipComposeText(t)
    }

    fun consumeShareText() {
        _pendingShareText.value = null
    }

    fun requestOpenPost(postId: Long) {
        if (postId > 0L) _openPostId.value = postId
    }

    fun consumeOpenPost() {
        _openPostId.value = null
    }

    fun requestNewPostCompose() {
        _composeRequests.value += 1
    }
}
