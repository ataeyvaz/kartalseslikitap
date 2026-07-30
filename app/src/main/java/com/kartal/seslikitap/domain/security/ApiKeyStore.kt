package com.kartal.seslikitap.domain.security

import com.kartal.seslikitap.domain.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Kullanıcının kendi kimlik bilgilerinin deposu (plan Bölüm 2, BYOK).
 *
 * Sözleşme: bu değerler **hiçbir zaman** kendi sunucumuza gönderilmez, yedeklenen
 * veritabanında tutulmaz ve düz metin saklanmaz.
 */
interface ApiKeyStore {

    suspend fun getCredential(providerId: ProviderId, fieldId: String): String?

    suspend fun setCredential(providerId: ProviderId, fieldId: String, value: String)

    /** Sağlayıcının tüm alanlarını siler. */
    suspend fun clearCredentials(providerId: ProviderId)

    /**
     * Sağlayıcı başına **dolu olan alan kimlikleri**. Değerler asla dışarı verilmez;
     * UI yalnızca hangi alanların tanımlı olduğunu görür.
     */
    fun observeStoredFields(): Flow<Map<ProviderId, Set<String>>>

    // --- Tek anahtarlı sağlayıcılar için kısayollar ---

    suspend fun getKey(providerId: ProviderId): String? =
        getCredential(providerId, CredentialField.DEFAULT_FIELD_ID)

    suspend fun setKey(providerId: ProviderId, key: String) =
        setCredential(providerId, CredentialField.DEFAULT_FIELD_ID, key)
}
