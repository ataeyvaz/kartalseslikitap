package com.kartal.seslikitap.data.provider.googlecloud

import android.util.Base64
import com.kartal.seslikitap.data.remote.googlecloud.GoogleCloudTtsApi
import com.kartal.seslikitap.data.remote.googlecloud.TtsAudioConfig
import com.kartal.seslikitap.data.remote.googlecloud.TtsInput
import com.kartal.seslikitap.data.remote.googlecloud.TtsSynthesizeRequest
import com.kartal.seslikitap.data.remote.googlecloud.TtsVoiceSelection
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Cloud Text-to-Speech sağlayıcısı (BYOK).
 *
 * On-device motorun aksine SSML destekler; bu yüzden metin hazırlama katmanı buraya
 * duraklama/tonlama işaretlemesi eklenmiş hâlde gönderir (plan Bölüm 1).
 */
@Singleton
class GoogleCloudTtsProvider @Inject constructor(
    private val api: GoogleCloudTtsApi,
    private val apiKeyStore: ApiKeyStore,
    private val voiceMappingResolver: GoogleCloudTtsVoiceMappingResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TtsProvider {

    override val id: ProviderId = ProviderIds.GoogleCloudTts
    override val name: String = "Google Cloud TTS (doğal okuma)"
    override val requiresApiKey: Boolean = true
    override val isOnDevice: Boolean = false
    override val supportsSsml: Boolean = true

    override suspend fun isAvailable(): Boolean = !apiKeyStore.getKey(id).isNullOrBlank()

    override suspend fun availableVoices(): List<ProviderVoice> = voiceMappingResolver.availableVoices()

    override suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream =
        withContext(ioDispatcher) {
            if (text.isBlank()) throw TtsProviderException(id, "Seslendirilecek metin boş")

            val apiKey = apiKeyStore.getKey(id)
                ?: throw TtsProviderException(id, "Google Cloud API anahtarı girilmemiş")

            val languageCode = voice.languageTag ?: Locale.getDefault().toLanguageTag()
            val resolved = voiceMappingResolver.resolveVoice(voice)

            val request = TtsSynthesizeRequest(
                // supportsSsml=true olduğu için gelen metin zaten <speak> ile sarılmış olur.
                input = if (text.startsWith("<speak")) TtsInput(ssml = text) else TtsInput(text = text),
                voice = TtsVoiceSelection(
                    languageCode = resolved?.languageTag ?: languageCode,
                    name = resolved?.id,
                    // Somut ses adı bulunduysa cinsiyet göndermeyiz; ad zaten sesi belirler.
                    ssmlGender = if (resolved != null) null else GoogleVoiceMapping.toSsmlGender(voice.gender),
                ),
                audioConfig = TtsAudioConfig(
                    audioEncoding = AUDIO_ENCODING,
                    speakingRate = GoogleVoiceMapping.clampSpeakingRate(voice.speakingRate),
                    pitch = GoogleVoiceMapping.pitchToSemitones(voice.pitch),
                ),
            )

            val response = try {
                api.synthesize(apiKey, request)
            } catch (e: Exception) {
                throw TtsProviderException(id, "Google Cloud TTS isteği başarısız: ${e.message}", e)
            }

            if (response.audioContent.isBlank()) {
                throw TtsProviderException(id, "Google Cloud TTS boş ses döndürdü")
            }

            AudioStream.InMemory(
                bytes = Base64.decode(response.audioContent, Base64.DEFAULT),
                mimeType = AudioStream.MIME_MP3,
            )
        }

    private companion object {
        const val AUDIO_ENCODING = "MP3"
    }
}
