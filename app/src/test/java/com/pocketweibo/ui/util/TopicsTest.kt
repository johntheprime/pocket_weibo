package com.pocketweibo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicsTest {

    @Test
    fun extractsDistinctHashtagsKeepingFirstDisplayName() {
        val tags = extractTopicTags("聊聊 #AI 与 #ai 的未来，#苏轼 也说说")

        assertEquals(listOf("AI", "苏轼"), tags.map { it.displayName })
        assertEquals(listOf("ai", "苏轼"), tags.map { it.key })
    }

    @Test
    fun stopsAtPunctuationAndWhitespace() {
        val tags = extractTopicTags("#话题一，接着 #话题二：再 #话题三\n#话题四")

        assertEquals(
            listOf("话题一", "话题二", "话题三", "话题四"),
            tags.map { it.displayName }
        )
    }

    @Test
    fun ignoresBareWordWithoutHashtag() {
        assertTrue(extractTopicTags("AI 会不会改变历史").isEmpty())
    }

    @Test
    fun todoIsReservedForTheTodoList() {
        val tags = extractTopicTags("#TODO 与 #AI")

        assertEquals(listOf("AI"), tags.map { it.displayName })
        assertTrue(isReservedTopicKey("TODO"))
        assertFalse(isReservedTopicKey("AI"))
    }
}
