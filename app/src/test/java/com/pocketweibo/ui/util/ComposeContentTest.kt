package com.pocketweibo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeContentTest {

    @Test
    fun shortTextIsUnchanged() {
        assertEquals("hello", clipComposeText("hello"))
    }

    @Test
    fun textAtLimitIsUnchanged() {
        val text = "x".repeat(COMPOSE_MAX_CHARS)
        assertEquals(text, clipComposeText(text))
    }

    @Test
    fun oversizedPasteKeepsFirstMaxCharsInsteadOfBeingRejected() {
        val pasted = "a".repeat(COMPOSE_MAX_CHARS + 750)
        val clipped = clipComposeText(pasted)

        assertEquals(COMPOSE_MAX_CHARS, clipped.length)
        assertEquals(pasted.take(COMPOSE_MAX_CHARS), clipped)
    }
}
