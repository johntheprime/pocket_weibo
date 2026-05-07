package com.pocketweibo.ui.util

import com.pocketweibo.data.media.PostVoice

/** Text shown in lists/cards for stored voice-only placeholder. */
fun postOrCommentBodyForDisplay(content: String, audioPath: String, voiceOnlyLabel: String): String =
    if (PostVoice.hasAudio(audioPath) && PostVoice.isPlaceholder(content)) voiceOnlyLabel else content
