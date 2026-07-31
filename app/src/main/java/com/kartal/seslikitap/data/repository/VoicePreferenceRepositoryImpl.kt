package com.kartal.seslikitap.data.repository

import com.kartal.seslikitap.data.local.dao.VoicePreferenceDao
import com.kartal.seslikitap.data.local.entity.VoicePreferenceEntity
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.repository.PinnedVoice
import com.kartal.seslikitap.domain.repository.VoicePreferenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePreferenceRepositoryImpl @Inject constructor(
    private val dao: VoicePreferenceDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VoicePreferenceRepository {

    override fun observeAll(): Flow<Map<ProviderId, PinnedVoice>> =
        dao.observeAll().map { rows ->
            rows.associate { ProviderId(it.providerId) to PinnedVoice(it.voiceId, it.displayName) }
        }

    override suspend fun getPinnedVoice(providerId: ProviderId): PinnedVoice? =
        withContext(ioDispatcher) {
            dao.get(providerId.value)?.let { PinnedVoice(it.voiceId, it.displayName) }
        }

    override suspend fun pinVoice(providerId: ProviderId, voiceId: String, displayName: String) =
        withContext(ioDispatcher) {
            val trimmed = voiceId.trim()
            if (trimmed.isEmpty()) {
                dao.delete(providerId.value)
            } else {
                dao.upsert(
                    VoicePreferenceEntity(
                        providerId = providerId.value,
                        voiceId = trimmed,
                        displayName = displayName.ifBlank { trimmed },
                    ),
                )
            }
        }

    override suspend fun clearPinnedVoice(providerId: ProviderId) = withContext(ioDispatcher) {
        dao.delete(providerId.value)
    }
}
