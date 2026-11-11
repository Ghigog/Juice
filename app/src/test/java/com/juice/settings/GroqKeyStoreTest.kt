package com.juice.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.juice.core.intake.GroqKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reverses the bytes behind a fixed one-byte IV: enough to prove the file is not plaintext. */
private class FakeCipher : KeyCipher {
    override fun encrypt(plain: ByteArray) = byteArrayOf(7) + plain.reversedArray().map { (it + 1).toByte() }.toByteArray()
    override fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.isNotEmpty() && sealed[0] == 7.toByte()) { "corrupt" }
        return sealed.copyOfRange(1, sealed.size).map { (it - 1).toByte() }.toByteArray().reversedArray()
    }
}

@RunWith(RobolectricTestRunner::class)
class GroqKeyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = GroqKeyStore(context, FakeCipher())
    private val file get() = File(context.noBackupFilesDir, "groq_key.bin")

    private fun pasted() = assertNotNull(GroqKey.parse("  gsk_abcdefgh1234 \n"))

    @Test fun savedKeyLoadsBackAndFileHoldsNoPlaintext() {
        store.save(pasted())
        val loaded = assertNotNull(store.load())
        assertEquals("gsk_abcdefgh1234", loaded.value)
        assertEquals("gsk_…1234", loaded.masked)
        assertTrue(store.hasKey.value)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("gsk_abcdefgh1234"))
    }

    @Test fun removeDeletesFileAndKey() {
        store.save(pasted())
        store.remove()
        assertNull(store.load())
        assertFalse(store.hasKey.value)
        assertFalse(file.exists())
    }

    @Test fun corruptFileLoadsNullAndIsDeleted() {
        store.save(pasted())
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store.load())
        assertFalse(file.exists())
        assertFalse(store.hasKey.value)
    }

    @Test fun loadWithNothingSavedIsNull() = assertNull(store.load())
}
