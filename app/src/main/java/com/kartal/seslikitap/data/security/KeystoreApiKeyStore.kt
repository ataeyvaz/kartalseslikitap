package com.kartal.seslikitap.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.security.ApiKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kimlik bilgilerini Android Keystore'daki bir AES anahtarıyla şifreleyip
 * SharedPreferences'ta saklar.
 *
 * Şifreleme anahtarı donanım destekli Keystore'da yaşar ve uygulama dışına çıkarılamaz;
 * dosyaya yalnızca şifreli metin + IV yazılır. Bu yüzden yedekten kopyalanan dosya başka
 * bir cihazda çözülemez. Bu değerlerin Room'da tutulmama sebebi de budur: veritabanı
 * yedeklenebilir/dışa aktarılabilir.
 */
@Singleton
class KeystoreApiKeyStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ApiKeyStore {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun getCredential(providerId: ProviderId, fieldId: String): String? =
        withContext(ioDispatcher) {
            val stored = preferences.getString(storageKey(providerId, fieldId), null)
                ?: return@withContext null
            runCatching { decrypt(stored) }.getOrNull()
        }

    override suspend fun setCredential(providerId: ProviderId, fieldId: String, value: String) =
        withContext(ioDispatcher) {
            val trimmed = value.trim()
            val key = storageKey(providerId, fieldId)
            if (trimmed.isEmpty()) {
                preferences.edit().remove(key).apply()
            } else {
                preferences.edit().putString(key, encrypt(trimmed)).apply()
            }
        }

    override suspend fun clearCredentials(providerId: ProviderId) = withContext(ioDispatcher) {
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.substringBefore(SEPARATOR_FIELD) == providerId.value }
            .forEach(editor::remove)
        editor.apply()
    }

    override fun observeStoredFields(): Flow<Map<ProviderId, Set<String>>> = callbackFlow {
        fun snapshot(): Map<ProviderId, Set<String>> =
            preferences.all.keys
                .mapNotNull { storageKey ->
                    val providerValue = storageKey.substringBefore(SEPARATOR_FIELD)
                    val fieldId = storageKey.substringAfter(SEPARATOR_FIELD, "")
                    if (fieldId.isEmpty()) null else ProviderId(providerValue) to fieldId
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, fields) -> fields.toSet() }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(snapshot())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun storageKey(providerId: ProviderId, fieldId: String) =
        "${providerId.value}$SEPARATOR_FIELD$fieldId"

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // IV her şifrelemede yeniden üretilir ve şifreli metinle birlikte saklanır.
        return "${cipher.iv.encodeBase64()}$SEPARATOR_PAYLOAD${encrypted.encodeBase64()}"
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR_PAYLOAD)
        require(parts.size == 2) { "Bozuk şifreli kayıt" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, parts[0].decodeBase64()),
        )
        return String(cipher.doFinal(parts[1].decodeBase64()), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "provider_api_keys"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "kartal_api_key_wrapper"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256

        /** Depolama anahtarında sağlayıcı ile alanı ayırır. */
        const val SEPARATOR_FIELD = "/"

        /** Şifreli kayıtta IV ile gövdeyi ayırır. */
        const val SEPARATOR_PAYLOAD = ":"
    }
}
