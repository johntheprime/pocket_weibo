package com.pocketweibo.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** REVIEW-3 §7.4 / RULES.md §1: every string/plural/array key exists in both zh and en. */
class StringParityTest {

    private fun keys(path: String): Map<String, Set<String>> {
        val file = listOf(File(path), File("app/$path")).first { it.isFile }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return listOf("string", "plurals", "string-array").associateWith { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length)
                .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
                .toSet()
        }
    }

    @Test
    fun zhAndEnHaveSameKeys() {
        val zh = keys("src/main/res/values/strings.xml")
        val en = keys("src/main/res/values-en/strings.xml")
        for (tag in zh.keys) {
            val a = zh.getValue(tag)
            val b = en.getValue(tag)
            assertEquals("$tag only in values/: ${a - b}; only in values-en/: ${b - a}", a, b)
        }
    }
}
