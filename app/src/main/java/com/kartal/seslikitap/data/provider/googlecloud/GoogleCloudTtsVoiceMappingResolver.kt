package com.kartal.seslikitap.data.provider.googlecloud

import com.kartal.seslikitap.data.remote.googlecloud.GoogleCloudTtsApi
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import com.kartal.seslikitap.domain.repository.VoicePreferenceRepository
import com.kartal.seslikitap.domain.security.ApiKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Cloud TTS ses listesini [VoiceConfig]'e eşler.
 *
 * Ses listesi ücretli API çağrısı gerektirmez ama ağ turudur; bir kez alınıp önbelleğe konur.
 */
@Singleton
class GoogleCloudTtsVoiceMappingResolver @Inject constructor(
    private val api: GoogleCloudTtsApi,
    private val apiKeyStore: ApiKeyStore,
    private val voicePreferenceRepository: VoicePreferenceRepository,
) : VoiceMappingResolver {

    override val providerId: ProviderId = ProviderIds.GoogleCloudTts

    private val cacheMutex = Mutex()
    private var cachedVoices: List<ProviderVoice>? = null

    suspend fun availableVoices(): List<ProviderVoice> = cacheMutex.withLock {
        cachedVoices?.let { return@withLock it }

        val apiKey = apiKeyStore.getKey(providerId) ?: return@withLock emptyList()
        val voices = runCatching { api.listVoices(apiKey) }.getOrNull()?.voices.orEmpty()

        voices.flatMap { voice ->
            voice.languageCodes.map { languageCode ->
                ProviderVoice(
                    id = voice.name,
                    displayName = voice.name,
                    gender = GoogleVoiceMapping.genderOf(voice.ssmlGender),
                    languageTag = languageCode,
                    isChildFriendly = GoogleVoiceMapping.isChildFriendly(voice.name),
                    requiresNetwork = true,
                    quality = GoogleVoiceMapping.qualityOf(voice.name),
                )
            }
        }.also { cachedVoices = it }
    }

    override suspend fun resolveVoice(config: VoiceConfig): ProviderVoice? {
        // Kullanıcının sabitlediği ses otomatik seçimin önüne geçer.
        voicePreferenceRepository.getPinnedVoice(providerId)?.let { pinned ->
            availableVoices().firstOrNull { it.id == pinned.voiceId }?.let { return it }
        }

        val targetLanguage = config.languageTag ?: Locale.getDefault().toLanguageTag()
        val candidates = availableVoices()
            .filter { it.languageTag.equals(targetLanguage, ignoreCase = true) }
            .ifEmpty {
                availableVoices().filter {
                    it.languageTag.substringBefore('-')
                        .equals(targetLanguage.substringBefore('-'), ignoreCase = true)
                }
            }

        return GoogleVoiceMapping.selectVoice(candidates, config)
    }

    override suspend fun invalidateCache() = cacheMutex.withLock { cachedVoices = null }
}
