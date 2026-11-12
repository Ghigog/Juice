package com.juice.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.juice.core.intake.GroqKey
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Encrypts and decrypts the key's bytes. Swapped for a fake in tests, which have no AndroidKeyStore. */
interface KeyCipher {
    /** Returns the IV followed by the ciphertext. */
    fun encrypt(plain: ByteArray): ByteArray

    /** Reverses [encrypt]; throws when [sealed] is corrupt or the key is gone. */
    fun decrypt(sealed: ByteArray): ByteArray
}

/** AES/GCM with a non-exportable key held in the AndroidKeyStore. */
class AndroidKeyStoreCipher(private val alias: String = DEFAULT_ALIAS) : KeyCipher {
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val iv = sealed.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv)) }
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    companion object {
        const val DEFAULT_ALIAS = "juice_groq_key"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * The person's Groq key, encrypted in a file under noBackupFilesDir so it is never backed up
 * and never sits in DataStore. A file that will not decrypt is deleted and reads as no key.
 */
class GroqKeyStore(context: Context, private val cipher: KeyCipher = AndroidKeyStoreCipher()) {
    private val file = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
    private val _hasKey = MutableStateFlow(file.exists())

    /** True while a key is stored. */
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun save(key: GroqKey) {
        file.writeBytes(cipher.encrypt(key.value.toByteArray(Charsets.UTF_8)))
        _hasKey.value = true
    }

    fun load(): GroqKey? {
        if (!file.exists()) return null
        val key = runCatching { GroqKey.parse(String(cipher.decrypt(file.readBytes()), Charsets.UTF_8)) }.getOrNull()
        if (key == null) remove()
        return key
    }

    fun remove() {
        file.delete()
        _hasKey.value = false
    }

    private companion object {
        const val FILE_NAME = "groq_key.bin"
    }
}
