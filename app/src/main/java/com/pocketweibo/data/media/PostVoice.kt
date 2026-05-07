package com.pocketweibo.data.media

/** Voice-only posts persist this marker in [com.pocketweibo.data.local.entity.PostEntity.content]. */
object PostVoice {
    const val STORED_PLACEHOLDER = "<audio>"

    fun isPlaceholder(content: String): Boolean = content.trim() == STORED_PLACEHOLDER

    fun hasAudio(audioPath: String): Boolean = audioPath.isNotBlank()
}
