package com.pocketweibo.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class AutoBackupCryptoTest {

    @Test
    fun encryptDecrypt_roundTrip() {
        val key = AutoBackupCrypto.keyFromDeviceStrings("test-android-id", "com.pocketweibo.test")
        val plain = """{"kind":"pocket_weibo_text_auto_backup","x":1}""".toByteArray(Charsets.UTF_8)
        val enc = AutoBackupCrypto.encryptWithKey(plain, key)
        val dec = AutoBackupCrypto.decryptWithKey(enc, key)
        assertArrayEquals(plain, dec)
    }

    @Test
    fun wrongKey_failsDecrypt() {
        val k1 = AutoBackupCrypto.keyFromDeviceStrings("a", "pkg")
        val k2 = AutoBackupCrypto.keyFromDeviceStrings("b", "pkg")
        val enc = AutoBackupCrypto.encryptWithKey("data".toByteArray(), k1)
        org.junit.Assert.assertThrows(Exception::class.java) {
            AutoBackupCrypto.decryptWithKey(enc, k2)
        }
    }
}
