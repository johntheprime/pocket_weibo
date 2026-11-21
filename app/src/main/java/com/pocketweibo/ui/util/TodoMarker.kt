package com.pocketweibo.ui.util

/**
 * Uppercase TODO is a first-class marker only when it starts the post or appears as a hashtag.
 * Lowercase `todo`, mid-word matches, and plain mentions in the middle of a post are ignored.
 */
private const val MARKER = "TODO"
private const val HASHTAG_MARKER = "#TODO"

/** CJK ideographs/kana act as word boundaries here, so `#TODO买菜` is a valid marker. */
private fun isCjkBoundary(c: Char): Boolean =
    c in '\u3040'..'\u30FF' || // kana
        c in '\u3400'..'\u9FFF' || // CJK unified ideographs
        c in '\uF900'..'\uFAFF' || // compatibility ideographs
        c in '\uAC00'..'\uD7AF' // Hangul syllables

private fun hasMarkerBoundary(content: String, endIndex: Int): Boolean {
    if (endIndex >= content.length) return true
    val next = content[endIndex]
    return next.isWhitespace() || !next.isLetterOrDigit() || isCjkBoundary(next)
}

/** True when the post carries an explicit uppercase TODO marker. */
fun isTodoMarked(content: String): Boolean {
    val trimmed = content.trimStart()
    if (trimmed.startsWith(MARKER) && hasMarkerBoundary(trimmed, MARKER.length)) return true

    var index = content.indexOf(HASHTAG_MARKER)
    while (index >= 0) {
        if (hasMarkerBoundary(content, index + HASHTAG_MARKER.length)) return true
        index = content.indexOf(HASHTAG_MARKER, index + HASHTAG_MARKER.length)
    }
    return false
}
