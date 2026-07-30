package com.kartal.seslikitap.data.provider.androidtts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.AudioStream
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.DirectSpeechTtsProvider
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.TtsProvider
import com.kartal.seslikitap.domain.provider.TtsProviderException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Faz 1'in varsayılan TTS sağlayıcısı: cihazdaki Android [TextToSpeech] motoru.
 *
 * Ücretsiz ve internetsiz çalışır; doğallık açısından en zayıf seçenektir (plan Bölüm 1).
 * Faz 2/3'te eklenecek bulut sağlayıcılar aynı [TtsProvider] arayüzünü uygular.
 */
@Singleton
class AndroidTtsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: AndroidTtsEngine,
    private val voiceMappingResolver: AndroidTtsVoiceMappingResolver,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TtsProvider, DirectSpeechTtsProvider {

    override val id: ProviderId = ProviderIds.AndroidTts
    override val name: String = "Android TTS (cihaz üzerinde)"
    override val requiresApiKey: Boolean = false
    override val isOnDevice: Boolean = true

    override suspend fun isAvailable(): Boolean = engine.getOrNull() != null

    override suspend fun availableVoices(): List<ProviderVoice> = voiceMappingResolver.availableVoices()

    override suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream =
        withContext(ioDispatcher) {
            requireNonBlank(text)
            val tts = engine.get()
            tts.applyConfig(voice, voiceMappingResolver.resolveVoice(voice))

            val utteranceId = UUID.randomUUID().toString()
            val outputFile = File(synthesisCacheDir(), "$utteranceId.wav")

            awaitUtterance(tts, utteranceId) {
                tts.synthesizeToFile(text, Bundle(), outputFile, utteranceId)
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw TtsProviderException(id, "Sentezlenen ses dosyası oluşmadı")
            }
            AudioStream.LocalFile(outputFile, AudioStream.MIME_WAV)
        }

    override suspend fun speak(text: String, voice: VoiceConfig) = withContext(ioDispatcher) {
        requireNonBlank(text)
        val tts = engine.get()
        tts.applyConfig(voice, voiceMappingResolver.resolveVoice(voice))

        val utteranceId = UUID.randomUUID().toString()
        awaitUtterance(tts, utteranceId) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }
    }

    override fun stop() {
        // Motor henüz kurulmadıysa yapacak bir şey yok; kurulumu tetiklemeye değmez.
        engine.currentOrNull()?.let { runCatching { it.stop() } }
    }

    /**
     * [start] ile başlatılan seslendirmenin bitmesini bekler. Coroutine iptal edilirse
     * konuşma da durdurulur.
     */
    private suspend fun awaitUtterance(
        tts: TextToSpeech,
        utteranceId: String,
        start: () -> Int,
    ) = suspendCancellableCoroutine { continuation ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit

            override fun onDone(id: String?) {
                if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && continuation.isActive) {
                    continuation.resumeWithException(
                        TtsProviderException(
                            ProviderIds.AndroidTts,
                            "Android TTS seslendirme hatası (errorCode=$errorCode)",
                        ),
                    )
                }
            }

            // Eski (hata kodsuz) imza; abstract olduğu için uygulanması zorunlu.
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(id: String?) = onError(id, TextToSpeech.ERROR)
        })

        continuation.invokeOnCancellation { runCatching { tts.stop() } }

        val result = start()
        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
            continuation.resumeWithException(
                TtsProviderException(this.id, "Android TTS isteği reddedildi (result=$result)"),
            )
        }
    }

    private fun TextToSpeech.applyConfig(config: VoiceConfig, resolvedVoice: ProviderVoice?) {
        val locale = config.languageTag?.let(Locale::forLanguageTag) ?: Locale.getDefault()
        val languageResult = setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Dil paketi yoksa motorun varsayılan diliyle devam et; sessizce başarısız olmaktansa
            // okunabilir bir çıktı üretmek daha iyi.
            setLanguage(Locale.getDefault())
        }

        // Cinsiyet/çocuk tercihine karşılık gelen somut ses; bulunamazsa motorun varsayılanı kalır.
        resolvedVoice?.let { target ->
            runCatching { voices }.getOrNull()
                ?.firstOrNull { it.name == target.id }
                ?.let { voice = it }
        }

        setSpeechRate(config.speakingRate.coerceIn(MIN_RATE, MAX_RATE))
        setPitch(config.pitch.coerceIn(MIN_PITCH, MAX_PITCH))
    }

    private fun synthesisCacheDir(): File =
        File(context.cacheDir, "tts").apply { mkdirs() }

    private fun requireNonBlank(text: String) {
        if (text.isBlank()) throw TtsProviderException(id, "Seslendirilecek metin boş")
    }

    private companion object {
        const val MIN_RATE = 0.1f
        const val MAX_RATE = 3.0f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2.0f
    }
}
