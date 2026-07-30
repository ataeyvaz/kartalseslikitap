package com.kartal.seslikitap.domain.model

import java.io.File

/**
 * TTS sağlayıcısının ürettiği ses çıktısı.
 *
 * On-device sağlayıcılar (Android TTS) dosyaya sentezler; bulut sağlayıcılar bellekte
 * byte dizisi döndürür. Her iki durum da Media3/ExoPlayer tarafından oynatılabilir.
 */
sealed interface AudioStream {

    val mimeType: String

    data class LocalFile(
        val file: File,
        override val mimeType: String = MIME_WAV,
    ) : AudioStream

    data class InMemory(
        val bytes: ByteArray,
        override val mimeType: String = MIME_MP3,
    ) : AudioStream {
        override fun equals(other: Any?): Boolean =
            this === other || (other is InMemory && mimeType == other.mimeType && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }

    companion object {
        const val MIME_WAV = "audio/wav"
        const val MIME_MP3 = "audio/mpeg"
    }
}
