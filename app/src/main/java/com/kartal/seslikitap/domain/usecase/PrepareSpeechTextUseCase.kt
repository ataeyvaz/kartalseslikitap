package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.domain.provider.TtsProvider
import javax.inject.Inject

/**
 * Metni seslendirmeye hazırlar (plan Bölüm 1, "doğallık/akıcılık notu").
 *
 * Provider-agnostik bir adımdır: sağlayıcı SSML destekliyorsa noktalama/paragraf
 * duraklamaları SSML etiketleriyle, desteklemiyorsa (Android TTS) motorun kendi
 * prozodisine güvenilerek düz metin olarak üretilir. Etiketlerin sesli okunması
 * riskini almamak için SSML asla desteklemeyen sağlayıcıya gönderilmez.
 */
class PrepareSpeechTextUseCase @Inject constructor() {

    operator fun invoke(text: String, provider: TtsProvider): String {
        val normalized = text.trim()
        if (normalized.isEmpty()) return ""
        return if (provider.supportsSsml) normalized.toSsml() else normalized.toPlainSpeech()
    }

    /** Paragraf araları nefes payı, cümle sonları kısa duraklama. */
    private fun String.toSsml(): String {
        val paragraphs = split(PARAGRAPH_SEPARATOR)
            .map { it.trim() }
            .filter(String::isNotEmpty)
            .joinToString("\n") { paragraph ->
                // Önce kaçış, sonra etiket ekleme; ters sırada kendi etiketlerimizi kaçırırdık.
                val withSentenceBreaks = SENTENCE_END_REGEX.replace(paragraph.escapeXml()) { match ->
                    "${match.value}<break time=\"$SENTENCE_BREAK_MS\"/>"
                }
                "<p>$withSentenceBreaks</p>"
            }
        return "<speak>$paragraphs</speak>"
    }

    /**
     * Düz metinde tek yapabileceğimiz, motorun duraklama sezgisini beslemek:
     * paragraflar arasına satır sonu bırakılır, kırık boşluklar temizlenir.
     */
    private fun String.toPlainSpeech(): String =
        split(PARAGRAPH_SEPARATOR)
            .map { it.trim() }
            .filter(String::isNotEmpty)
            .joinToString("\n\n")

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private companion object {
        val PARAGRAPH_SEPARATOR = Regex("\\n\\s*\\n")
        val SENTENCE_END_REGEX = Regex("[.!?…]+[\"')\\]]?")
        const val SENTENCE_BREAK_MS = "350ms"
    }
}
