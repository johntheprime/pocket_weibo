package com.pocketweibo.ui

import androidx.lifecycle.ViewModel
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

    fun offerShareText(text: String?) {
        val t = text?.trim().orEmpty()
        _pendingShareText.value = if (t.isEmpty()) null else t.take(2000)
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
}
