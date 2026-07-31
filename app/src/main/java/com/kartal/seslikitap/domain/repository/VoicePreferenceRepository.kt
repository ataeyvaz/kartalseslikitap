package com.kartal.seslikitap.domain.repository

import com.kartal.seslikitap.domain.provider.ProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Kullanıcının sağlayıcı başına sabitlediği sesler.
 *
 * Sabitlenmiş ses, otomatik seçimin (cinsiyet + doğallık skoru) önüne geçer: kullanıcı
 * kendi klonladığı sesi seçtiyse kitabın anlatıcı cinsiyeti ne olursa olsun o ses kullanılır.
 */
interface VoicePreferenceRepository {

    fun observeAll(): Flow<Map<ProviderId, PinnedVoice>>

    suspend fun getPinnedVoice(providerId: ProviderId): PinnedVoice?

    suspend fun pinVoice(providerId: ProviderId, voiceId: String, displayName: String)

    suspend fun clearPinnedVoice(providerId: ProviderId)
}

data class PinnedVoice(
    val voiceId: String,
    val displayName: String,
)
