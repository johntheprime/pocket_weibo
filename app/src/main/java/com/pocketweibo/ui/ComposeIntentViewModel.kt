package com.pocketweibo.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Carries ACTION_SEND text into [com.pocketweibo.ui.screens.compose.ComposeScreen]. */
class ComposeIntentViewModel : ViewModel() {
    private val _pendingShareText = MutableStateFlow<String?>(null)
    val pendingShareText: StateFlow<String?> = _pendingShareText.asStateFlow()

    fun offerShareText(text: String?) {
        val t = text?.trim().orEmpty()
        _pendingShareText.value = if (t.isEmpty()) null else t.take(2000)
    }

    fun consumeShareText() {
        _pendingShareText.value = null
    }
}
