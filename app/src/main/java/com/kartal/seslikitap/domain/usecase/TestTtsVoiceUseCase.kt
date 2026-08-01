package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.data.playback.AudioStreamPlayer
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.DirectSpeechTtsProvider
import com.kartal.seslikitap.domain.provider.TtsProviderRegistry
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import com.kartal.seslikitap.domain.provider.applyPresets
import com.kartal.seslikitap.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Aktif TTS sağlayıcısını kısa bir cümleyle dener ve **hangi sesin kullanıldığını** söyler.
 *
 * Bu, ayarlar ekranındaki en önemli geri bildirim: anahtarı girip kitap okumaya
 * başladığında hâlâ cihaz sesini duyuyorsan, sebebini burada anında görürsün.
 */
class TestTtsVoiceUseCase @Inject constructor(
    private val ttsRegistry: TtsProviderRegistry,
    private val voiceMappingResolvers: Set<@JvmSuppressWildcards VoiceMappingResolver>,
    private val settingsRepository: SettingsRepository,
    private val prepareSpeechText: PrepareSpeechTextUseCase,
    private val audioStreamPlayer: AudioStreamPlayer,
) {
    suspend operator fun invoke(): TtsTestResult {
        val provider = ttsRegistry.active()
        val settings = settingsRepository.getSettings()

        val config = VoiceConfig(
            gender = settings.defaultNarratorGender,
            speakingRate = settings.playbackSpeed,
            pitch = settings.pitch,
            languageTag = settings.languageTag,
        ).applyPresets()

        val voice = voiceMappingResolvers
            .firstOrNull { it.providerId == provider.id }
            ?.resolveVoice(config)

        val text = prepareSpeechText(SAMPLE_TEXT, provider)
        if (provider is DirectSpeechTtsProvider) {
            provider.speak(text, config)
        } else {
            audioStreamPlayer.play(provider.synthesize(text, config))
        }

        return TtsTestResult(
            providerName = provider.name,
            voiceName = voice?.displayName,
            voiceId = voice?.id,
        )
    }

    private companion object {
        const val SAMPLE_TEXT =
            "Merhaba, bu bir deneme. Kitabın bu sesle okunacak."
    }
}

data class TtsTestResult(
    val providerName: String,
    val voiceName: String?,
    val voiceId: String?,
) {
    /** Kullanıcıya gösterilecek özet: "ElevenLabs · Kendi Sesim". */
    val summary: String
        get() = buildString {
            append(providerName)
            voiceName?.let { append(" · $it") }
        }
}
