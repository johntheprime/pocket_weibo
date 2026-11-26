package com.pocketweibo.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** REVIEW-3 §1.1 / §8: hostile ZIP input must never write outside the staging root. */
class ZipSafetyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((name, body) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    @Test
    fun safeChild_rejectsTraversal() {
        val root = tmp.newFolder("root")
        assertNull(ZipSafety.safeChild(root, "../evil.txt"))
        assertNull(ZipSafety.safeChild(root, "identity_avatars/../../shared_prefs/x.xml"))
        assertTrue(ZipSafety.safeChild(root, "post_attachments/1/0.jpg") != null)
    }

    @Test
    fun extract_skipsTraversalEntries_andKeepsSafeOnes() {
        val root = tmp.newFolder("staging")
        val outside = File(tmp.root, "evil.txt")
        val rejected = ZipSafety.extractTo(
            ByteArrayInputStream(
                zipOf(
                    "data.json" to "{}",
                    "../evil.txt" to "pwned",
                    "post_attachments/../../evil.txt" to "pwned",
                    "post_attachments/1/0.jpg" to "img"
                )
            ),
            root
        )
        assertEquals(2, rejected.size)
        assertFalse(outside.exists())
        assertEquals("{}", File(root, "data.json").readText())
        assertEquals("img", File(root, "post_attachments/1/0.jpg").readText())
    }

    @Test
    fun normalizeName_dropsDirectoriesAndLeadingSlashes() {
        assertNull(ZipSafety.normalizeName("post_attachments/"))
        assertEquals("data.json", ZipSafety.normalizeName("/data.json"))
        assertEquals("a/b.txt", ZipSafety.normalizeName("a\\b.txt"))
    }
}
