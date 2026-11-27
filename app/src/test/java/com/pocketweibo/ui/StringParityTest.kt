package com.pocketweibo.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** RULES.md §1: every string/plural/array key exists in every locale directory. */
class StringParityTest {

    private fun resDir(): File =
        listOf(File("src/main/res"), File("app/src/main/res")).first { it.isDirectory }

    private fun keys(dir: File): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        listOf("strings.xml", "plurals.xml").map { File(dir, it) }.filter { it.isFile }
            .forEach { file ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                listOf("string", "plurals", "string-array").forEach { tag ->
                    val nodes = doc.getElementsByTagName(tag)
                    val names = result.getOrPut(tag) { mutableSetOf() }
                    (0 until nodes.length).forEach {
                        names.add(nodes.item(it).attributes.getNamedItem("name").nodeValue)
                    }
                }
            }
        return result
    }

    @Test
    fun everyLocaleHasSameKeys() {
        val res = resDir()
        val locales = res.listFiles { file ->
            file.isDirectory && file.name.startsWith("values") && File(file, "strings.xml").isFile
        }!!.sortedBy { it.name }
        assertEquals(
            "Expected values/, values-en/ and values-zh-rTW/",
            setOf("values", "values-en", "values-zh-rTW"),
            locales.map { it.name }.toSet()
        )

        val expected = keys(locales.first { it.name == "values" })
        locales.forEach { locale ->
            val actual = keys(locale)
            assertEquals("resource key groups in ${locale.name}", expected.keys, actual.keys)
            expected.forEach { (tag, names) ->
                val other = actual.getValue(tag)
                assertEquals(
                    "$tag in ${locale.name}: only in values/ = ${names - other}; only in ${locale.name} = ${other - names}",
                    names,
                    other
                )
            }
        }
    }
}
