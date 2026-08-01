package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.data.playback.AudioStreamPlayer
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.provider.DirectSpeechTtsProvider
import com.kartal.seslikitap.domain.provider.TtsProvider
import com.kartal.seslikitap.domain.provider.TtsProviderRegistry
import javax.inject.Inject

/**
 * "OCR -> oku" adımı: aktif TTS sağlayıcısıyla metni seslendirir ve okuma bitene kadar askıda kalır.
 *
 * Cihaz üstü motorlar doğrudan konuşur; bulut sağlayıcılar ses verisi üretir ve oynatma
 * [AudioStreamPlayer]'a devredilir. Çağıran taraf bu ayrımı bilmez.
 */
class ReadPageAloudUseCase @Inject constructor(
    private val ttsRegistry: TtsProviderRegistry,
    private val buildVoiceConfig: BuildVoiceConfigUseCase,
    private val prepareSpeechText: PrepareSpeechTextUseCase,
    private val audioStreamPlayer: AudioStreamPlayer,
) {
    @Volatile
    private var lastUsedProvider: TtsProvider? = null

    /**
     * Okuyan sağlayıcının adı; kullanıcı hangi sesi duyduğunu bilebilsin diye UI'a taşınır.
     * Ayarlarda bulut sesi seçili sanıp cihaz sesini dinlemek en kolay düşülen tuzak.
     */
    @Volatile
    var lastUsedProviderName: String? = null
        private set

    suspend operator fun invoke(book: Book, text: String) {
        if (text.isBlank()) return

        val provider = ttsRegistry.active().also {
            lastUsedProvider = it
            lastUsedProviderName = it.name
        }
        val voiceConfig = buildVoiceConfig(book)
        val speechText = prepareSpeechText(text, provider)

        if (provider is DirectSpeechTtsProvider) {
            provider.speak(speechText, voiceConfig)
        } else {
            val audio = provider.synthesize(speechText, voiceConfig)
            audioStreamPlayer.play(audio)
        }
    }

    /** Okumayı anında keser. Coroutine iptali de aynı sonucu verir; bu yol UI'dan hızlı erişim içindir. */
    fun stop() {
        (lastUsedProvider as? DirectSpeechTtsProvider)?.stop()
        audioStreamPlayer.stop()
    }
}
