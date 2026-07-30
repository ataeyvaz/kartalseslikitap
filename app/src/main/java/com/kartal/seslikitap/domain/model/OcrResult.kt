package com.kartal.seslikitap.domain.model

import com.kartal.seslikitap.domain.provider.ProviderId

/**
 * Sağlayıcıdan bağımsız OCR çıktısı.
 *
 * @param confidence 0f..1f aralığında ortalama güven skoru; sağlayıcı skor bildirmiyorsa null.
 *   Faz 2'deki "güven skoru düşükse buluta geç" mantığı bu alanı kullanır.
 */
data class OcrResult(
    val text: String,
    val blocks: List<OcrTextBlock>,
    val confidence: Float?,
    val providerId: ProviderId,
    val recognizedLanguages: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = text.isBlank()

    companion object {
        fun empty(providerId: ProviderId): OcrResult =
            OcrResult(text = "", blocks = emptyList(), confidence = null, providerId = providerId)
    }
}

data class OcrTextBlock(
    val text: String,
    val confidence: Float?,
    val boundingBox: BoundingBox?,
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
