package com.kartal.seslikitap.data.provider.androidtts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.TtsProviderException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TextToSpeech] motorunun tekil, asenkron başlatılan sarmalayıcısı.
 *
 * TextToSpeech kurulumu callback tabanlıdır ve hazır olmadan çağrılan her metot sessizce
 * başarısız olur; bu sınıf başlatmayı bir kez yapıp suspend fonksiyonla erişilebilir kılar.
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var engine: TextToSpeech? = null
    private var initStatus: CompletableDeferred<Int>? = null

    suspend fun get(): TextToSpeech = mutex.withLock {
        engine?.let { existing ->
            awaitReady(requireNotNull(initStatus))
            return@withLock existing
        }

        val status = CompletableDeferred<Int>()
        // Not: callback constructor dönmeden tetiklenebilir; bu yüzden motoru değil
        // sadece durumu callback üzerinden taşıyoruz.
        val tts = TextToSpeech(context) { result -> status.complete(result) }
        engine = tts
        initStatus = status
        awaitReady(status)
        tts
    }

    private suspend fun awaitReady(status: CompletableDeferred<Int>) {
        val result = status.await()
        if (result != TextToSpeech.SUCCESS) {
            throw TtsProviderException(
                ProviderIds.AndroidTts,
                "Android TTS motoru başlatılamadı (status=$result)",
            )
        }
    }

    /** Motoru başlatmayı dener; başarısız olursa null döner (kullanılabilirlik kontrolü için). */
    suspend fun getOrNull(): TextToSpeech? = runCatching { get() }.getOrNull()

    /** Zaten oluşturulmuş motoru döner; yoksa kurulum tetiklemeden null döner. */
    fun currentOrNull(): TextToSpeech? = engine

    fun shutdown() {
        engine?.shutdown()
        engine = null
        initStatus = null
    }
}
