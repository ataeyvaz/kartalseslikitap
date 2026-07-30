package com.kartal.seslikitap.data.provider.elevenlabs

import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsApi
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsSynthesizeRequest
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.AudioStream
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.TtsProvider
import com.kartal.seslikitap.domain.provider.TtsProviderException
import com.kartal.seslikitap.domain.security.ApiKeyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ElevenLabs TTS sağlayıcısı (BYOK) — en doğal ses seçeneği (plan Faz 3).
 *
 * İki önemli yetenek farkı var, bilinçli olarak öyle bırakıldı:
 *  - **SSML desteklenmez.** ElevenLabs bir SSML motoru değildir; `<speak>` gönderilirse
 *    etiketler sese karışabilir. Zaten prozodisi en güçlü sağlayıcı olduğu için düz metin
 *    en iyi sonucu verir; metin hazırlama katmanı bu yüzden ona düz metin gönderir.
 *  - **Pitch parametresi yoktur.** Ton, seçilen sesin kendi karakterinden gelir; kullanıcının
 *    ton ayarı bu sağlayıcıda yok sayılır (hız ise dar bir aralığa kırpılır).
 */
@Singleton
class ElevenLabsTtsProvider @Inject constructor(
    private val api: ElevenLabsApi,
    private val apiKeyStore: ApiKeyStore,
    private val voiceMappingResolver: ElevenLabsVoiceMappingResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TtsProvider {

    override val id: ProviderId = ProviderIds.ElevenLabs
    override val name: String = "ElevenLabs (en doğal, karakter başı ücretli)"
    override val requiresApiKey: Boolean = true
    override val isOnDevice: Boolean = false
    override val supportsSsml: Boolean = false

    override suspend fun isAvailable(): Boolean = !apiKeyStore.getKey(id).isNullOrBlank()

    override suspend fun availableVoices(): List<ProviderVoice> = voiceMappingResolver.availableVoices()

    override suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream =
        withContext(ioDispatcher) {
            if (text.isBlank()) throw TtsProviderException(id, "Seslendirilecek metin boş")

            val apiKey = apiKeyStore.getKey(id)
                ?: throw TtsProviderException(id, "ElevenLabs API anahtarı girilmemiş")

            val selectedVoice = voiceMappingResolver.resolveVoice(voice)
                ?: throw TtsProviderException(
                    id,
                    "ElevenLabs ses listesi alınamadı; anahtarı ve internet bağlantısını kontrol et",
                )

            val request = ElevenLabsSynthesizeRequest(
                text = text,
                modelId = MODEL_MULTILINGUAL_V2,
                voiceSettings = ElevenLabsVoiceMapping.settingsFor(voice),
            )

            val bytes = try {
                api.synthesize(apiKey, selectedVoice.id, OUTPUT_FORMAT, request)
                    .use { it.bytes() }
            } catch (e: Exception) {
                throw TtsProviderException(id, "ElevenLabs isteği başarısız: ${e.message}", e)
            }

            if (bytes.isEmpty()) throw TtsProviderException(id, "ElevenLabs boş ses döndürdü")

            AudioStream.InMemory(bytes = bytes, mimeType = AudioStream.MIME_MP3)
        }

    private companion object {
        /** Türkçe dahil çok dilli, kitap anlatımı için en uygun model. */
        const val MODEL_MULTILINGUAL_V2 = "eleven_multilingual_v2"
        const val OUTPUT_FORMAT = "mp3_44100_128"
    }
}
