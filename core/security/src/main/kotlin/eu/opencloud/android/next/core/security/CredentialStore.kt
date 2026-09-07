package eu.opencloud.android.next.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import eu.opencloud.android.next.core.model.auth.AuthTokens
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun saveBasicPassword(
        accountId: String,
        password: String,
    )

    fun readBasicPassword(accountId: String): String?

    fun saveTokens(
        accountId: String,
        tokens: AuthTokens,
    )

    fun readTokens(accountId: String): AuthTokens?

    fun remove(accountId: String)
}

class KeystoreCredentialStore(
    context: Context,
) : CredentialStore {
    private val preferences = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)

    override fun saveBasicPassword(
        accountId: String,
        password: String,
    ) {
        preferences.edit().putString("basic.$accountId", encrypt(password)).apply()
    }

    override fun readBasicPassword(accountId: String): String? =
        preferences.getString("basic.$accountId", null)?.let(::decrypt)

    override fun saveTokens(
        accountId: String,
        tokens: AuthTokens,
    ) {
        val serialized =
            listOf(
                tokens.accessToken,
                tokens.refreshToken.orEmpty(),
                tokens.expiresAtEpochSeconds.toString(),
                tokens.tokenType,
                tokens.scope.orEmpty(),
            ).joinToString("\u0000")
        preferences.edit().putString("tokens.$accountId", encrypt(serialized)).apply()
    }

    override fun readTokens(accountId: String): AuthTokens? =
        preferences
            .getString("tokens.$accountId", null)
            ?.let(::decrypt)
            ?.split("\u0000")
            ?.takeIf { it.size == 5 }
            ?.let { values ->
                AuthTokens(
                    accessToken = values[0],
                    refreshToken = values[1].ifBlank { null },
                    expiresAtEpochSeconds = values[2].toLongOrNull() ?: return null,
                    tokenType = values[3],
                    scope = values[4].ifBlank { null },
                )
            }

    override fun remove(accountId: String) {
        preferences
            .edit()
            .remove("basic.$accountId")
            .remove("tokens.$accountId")
            .apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(value: String): String? =
        runCatching {
            val (iv, encrypted) = value.split(":", limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                String(doFinal(encrypted), StandardCharsets.UTF_8)
            }
        }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            ).apply {
                init(
                    KeyGenParameterSpec
                        .Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "opencloud.next.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
