package com.kartal.seslikitap.data.provider.piper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ata'nın kendi sesiyle eğitilmiş Piper modeli: tamamen cihazda, internetsiz, API anahtarsız.
 *
 * Metin cümlelere bölünür ([TurkishPhonemizer.sentences]); ilk cümle hazır olur olmaz çalınır, sonraki cümle
 * o sırada arka planda hazırlanır — uzun bir sayfada da okuma hemen başlar (Sözcük'teki sesli okumayla aynı düzen).
 *
 * Konuşma hızı sesin `length_scale`'ine uygulanır. Perde (pitch) ayarı bu seste desteklenmez: VITS modeli perdeyi
 * kendi üretir, sonradan değiştirmek sesi bozar.
 */
@Singleton
class PiperTtsProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val voice: PiperVoice,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TtsProvider, DirectSpeechTtsProvider {

    override val id: ProviderId = ProviderIds.PiperAta
    override val name: String = "Ata'nın sesi (cihaz üzerinde)"
    override val requiresApiKey: Boolean = false
    override val isOnDevice: Boolean = true

    @Volatile
    private var currentTrack: AudioTrack? = null

    @Volatile
    private var currentJob: Job? = null

    override suspend fun isAvailable(): Boolean = voice.isBundled()

    override suspend fun availableVoices(): List<ProviderVoice> = listOf(voiceInfo())

    fun voiceInfo() = ProviderVoice(
        id = voice.id,
        displayName = voice.displayName,
        gender = voice.gender,
        languageTag = "tr-TR",
        requiresNetwork = false,
        quality = 0.9f,
    )

    /** Metnin tamamını tek bir WAV dosyasına sentezler (cümleler arasında kısa sessizlikle). */
    override suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream = withContext(ioDispatcher) {
        val chunks = chunks(text)
        val rate = this@PiperTtsProvider.voice.sampleRate()
        val pause = ShortArray((rate * SENTENCE_PAUSE_SECONDS).toInt())
        val pieces = chunks.flatMap { listOf(synthesizeChunk(it, voice), pause) }
        val file = File(File(context.cacheDir, "tts").apply { mkdirs() }, "${UUID.randomUUID()}.wav")
        writeWav(file, pieces, rate)
        AudioStream.LocalFile(file, AudioStream.MIME_WAV)
    }

    /** Cümle cümle sentezleyip çalar; okuma bitene kadar askıda kalır, iptal edilirse hemen susar. */
    override suspend fun speak(text: String, voice: VoiceConfig) = withContext(ioDispatcher) {
        val chunks = chunks(text)
        val rate = this@PiperTtsProvider.voice.sampleRate()
        val track = createTrack(rate)
        currentTrack = track
        currentJob = coroutineContext[Job]
        try {
            coroutineScope {
                // kapasite 1: çalınan cümlenin yanında en fazla bir cümle hazır bekler
                val ready = Channel<ShortArray>(capacity = 1)
                launch {
                    try {
                        for (chunk in chunks) ready.send(synthesizeChunk(chunk, voice))
                    } finally {
                        ready.close()
                    }
                }
                val pause = ShortArray((rate * SENTENCE_PAUSE_SECONDS).toInt())
                var written = 0L
                track.play()
                for (pcm in ready) {
                    written += writeFully(track, pcm)
                    written += writeFully(track, pause)
                }
                // AudioTrack akış kipinde yazılanı tampondan çalmaya devam eder: sonuna kadar bekle
                while (track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    (track.playbackHeadPosition.toLong() and 0xffffffffL) < written
                ) {
                    ensureActive()
                    delay(PLAYBACK_POLL_MS)
                }
            }
        } finally {
            currentTrack = null
            currentJob = null
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
    }

    /** Okumayı keser: çalan ses hemen susar, [speak] iptal edilir. */
    override fun stop() {
        currentJob?.cancel()
        currentTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.flush() }
        }
    }

    private fun chunks(text: String): List<String> {
        if (text.isBlank()) throw TtsProviderException(id, "Seslendirilecek metin boş")
        return TurkishPhonemizer.sentences(text).map { text.substring(it) }.filter { it.isNotBlank() }
    }

    private suspend fun synthesizeChunk(text: String, config: VoiceConfig): ShortArray = try {
        voice.synthesize(text, config.speakingRate.coerceIn(MIN_RATE, MAX_RATE))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw TtsProviderException(id, "Ata'nın sesi üretilemedi: ${e.message}", e)
    }

    private fun createTrack(rate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, rate / 2 * 2))   // ~0,5 sn
            .build()
    }

    /**
     * Parçayı tamamen yazar. Beklemesiz yazılır, tampon doluysa kısa aralıklarla yeniden denenir: engelleyen
     * write() duraklatılmış bir kanalda sonsuza dek bekleyebilir, bu yolla iptal her an işler.
     */
    private suspend fun writeFully(track: AudioTrack, pcm: ShortArray): Long = coroutineScope {
        var offset = 0
        while (offset < pcm.size) {
            ensureActive()
            val count = track.write(pcm, offset, minOf(WRITE_BLOCK, pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING)
            when {
                count < 0 -> throw TtsProviderException(id, "Ses çalınamadı (AudioTrack hata kodu $count)")
                count == 0 -> delay(WRITE_RETRY_MS)
                else -> offset += count
            }
        }
        offset.toLong()
    }

    private fun writeWav(file: File, pieces: List<ShortArray>, rate: Int) {
        val samples = pieces.sumOf { it.size }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples * 2)
        }
        file.outputStream().buffered().use { out ->
            out.write(header.array())
            val bytes = ByteBuffer.allocate(WRITE_BLOCK * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (piece in pieces) {
                var offset = 0
                while (offset < piece.size) {
                    val count = minOf(WRITE_BLOCK, piece.size - offset)
                    bytes.clear()
                    for (i in 0 until count) bytes.putShort(piece[offset + i])
                    out.write(bytes.array(), 0, count * 2)
                    offset += count
                }
            }
        }
    }

    private companion object {
        const val SENTENCE_PAUSE_SECONDS = 0.18f   // cümleler arası sessizlik (Sözcük'le aynı)
        const val WRITE_BLOCK = 2205               // ~0,1 sn: durdurma bu sıklıkla denetlenir
        const val PLAYBACK_POLL_MS = 50L
        const val WRITE_RETRY_MS = 20L
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}
