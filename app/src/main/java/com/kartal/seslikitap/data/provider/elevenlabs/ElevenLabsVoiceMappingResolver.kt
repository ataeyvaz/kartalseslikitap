package com.kartal.seslikitap.data.provider.elevenlabs

import com.kartal.seslikitap.data.remote.describeNetworkError
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsApi
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import com.kartal.seslikitap.domain.provider.VoiceScoring
import com.kartal.seslikitap.domain.repository.PinnedVoice
import com.kartal.seslikitap.domain.repository.VoicePreferenceRepository
import com.kartal.seslikitap.domain.provider.TtsProviderException
import com.kartal.seslikitap.domain.security.ApiKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ElevenLabs ses kütüphanesini [VoiceConfig]'e eşler.
 *
 * Google'dan farklı olarak ElevenLabs sesleri dile göre etiketlenmez: `eleven_multilingual_v2`
 * modeli aynı sesle onlarca dili konuşur. Bu yüzden dil filtresi uygulanmaz, seçim
 * cinsiyet ve anlatım kalitesine göre yapılır.
 */
@Singleton
class ElevenLabsVoiceMappingResolver @Inject constructor(
    private val api: ElevenLabsApi,
    private val apiKeyStore: ApiKeyStore,
    private val voicePreferenceRepository: VoicePreferenceRepository,
) : VoiceMappingResolver {

    override val providerId: ProviderId = ProviderIds.ElevenLabs

    private val cacheMutex = Mutex()
    private var cachedVoices: List<ProviderVoice>? = null

    suspend fun availableVoices(): List<ProviderVoice> = cacheMutex.withLock {
        cachedVoices?.let { return@withLock it }

        val apiKey = apiKeyStore.getKey(providerId)
            ?: throw TtsProviderException(providerId, "ElevenLabs API anahtarı girilmemiş")

        // Hata yutulmaz: anahtar yanlış mı, kota mı bitti, ağ mı yok — kullanıcı görmeli.
        val voices = try {
            api.listVoices(apiKey).voices
        } catch (e: Exception) {
            throw TtsProviderException(
                providerId,
                "ElevenLabs ses listesi alınamadı — ${e.describeNetworkError()}",
                e,
            )
        }

        voices.map { voice ->
            ProviderVoice(
                id = voice.voiceId,
                displayName = voice.name,
                gender = ElevenLabsVoiceMapping.genderOf(voice.labels),
                // Çok dilli model: ses belirli bir dile bağlı değil.
                languageTag = MULTILINGUAL,
                isChildFriendly = ElevenLabsVoiceMapping.isChildFriendly(voice.labels),
                requiresNetwork = true,
                quality = ElevenLabsVoiceMapping.qualityOf(voice.labels, voice.category),
            )
        }.also { cachedVoices = it }
    }

    override suspend fun resolveVoice(config: VoiceConfig): ProviderVoice? {
        // Kullanıcı bir ses sabitlediyse (ör. kendi klonladığı ses) o seçim her şeyin
        // önüne geçer; kitabın anlatıcı cinsiyeti veya kalite skoru dikkate alınmaz.
        val pinned = voicePreferenceRepository.getPinnedVoice(providerId)

        val voices = try {
            availableVoices()
        } catch (e: Exception) {
            // Sabitlenmiş ses varsa listeye ihtiyaç yok; okuma listeleme hatası yüzünden durmasın.
            if (pinned != null) return pinned.asProviderVoice(config) else throw e
        }

        if (pinned != null) {
            voices.firstOrNull { it.id == pinned.voiceId }?.let { return it }
            if (voices.isEmpty()) return pinned.asProviderVoice(config)
        }

        return VoiceScoring.select(voices, config)
    }

    private fun PinnedVoice.asProviderVoice(config: VoiceConfig) = ProviderVoice(
        id = voiceId,
        displayName = displayName,
        gender = config.gender,
        languageTag = MULTILINGUAL,
        requiresNetwork = true,
    )

    override suspend fun invalidateCache() = cacheMutex.withLock { cachedVoices = null }

    private companion object {
        const val MULTILINGUAL = "mul"
    }
}
