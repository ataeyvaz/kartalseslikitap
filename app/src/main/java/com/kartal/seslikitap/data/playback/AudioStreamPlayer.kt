package com.kartal.seslikitap.data.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.kartal.seslikitap.domain.model.AudioStream
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.TtsProviderException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bulut TTS sağlayıcılarının ürettiği [AudioStream]'i oynatır.
 *
 * Cihaz üstü motor kendi konuşur; bulut sağlayıcılar ses verisi döndürdüğü için oynatma
 * bu katmanın işidir. ExoPlayer ana iş parçacığına bağlıdır, bu yüzden tüm çağrılar
 * [Dispatchers.Main] üzerinde yapılır.
 */
@Singleton
class AudioStreamPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var player: ExoPlayer? = null

    /** Ses bitene kadar askıda kalır. Coroutine iptal edilirse oynatma durur. */
    suspend fun play(stream: AudioStream) = withContext(Dispatchers.Main) {
        val mediaItem = stream.toMediaItem()
        val exoPlayer = player ?: ExoPlayer.Builder(context).build().also { player = it }

        suspendCancellableCoroutine { continuation ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && continuation.isActive) {
                        exoPlayer.removeListener(this)
                        continuation.resume(Unit)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (continuation.isActive) {
                        exoPlayer.removeListener(this)
                        continuation.resumeWithException(
                            TtsProviderException(
                                ProviderIds.GoogleCloudTts,
                                "Ses oynatılamadı: ${error.errorCodeName}",
                                error,
                            ),
                        )
                    }
                }
            }

            continuation.invokeOnCancellation {
                // İptal ana iş parçacığından gelmeyebilir; ExoPlayer'a kendi thread'inden dokun.
                exoPlayer.applicationLooper.let {
                    android.os.Handler(it).post { runCatching { exoPlayer.stop() } }
                }
            }

            exoPlayer.addListener(listener)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun stop() {
        player?.let { p ->
            android.os.Handler(p.applicationLooper).post { runCatching { p.stop() } }
        }
    }

    fun release() {
        player?.let { p ->
            android.os.Handler(p.applicationLooper).post { runCatching { p.release() } }
        }
        player = null
    }

    /**
     * Bellekteki ses geçici bir dosyaya yazılır: ExoPlayer'a byte dizisi doğrudan
     * verilemez ve dosya yolu, ileride sayfa sesini önbelleğe almanın da temeli olur.
     */
    private fun AudioStream.toMediaItem(): MediaItem = when (this) {
        is AudioStream.LocalFile -> MediaItem.fromUri(file.toURI().toString())
        is AudioStream.InMemory -> {
            val cacheDir = File(context.cacheDir, "tts").apply { mkdirs() }
            val target = File(cacheDir, "${UUID.randomUUID()}.mp3")
            target.writeBytes(bytes)
            MediaItem.fromUri(target.toURI().toString())
        }
    }
}
