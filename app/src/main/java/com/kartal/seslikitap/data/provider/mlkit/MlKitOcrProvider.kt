package com.kartal.seslikitap.data.provider.mlkit

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.BoundingBox
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.model.OcrTextBlock
import com.kartal.seslikitap.domain.provider.OcrProvider
import com.kartal.seslikitap.domain.provider.OcrProviderException
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faz 1'in varsayılan OCR sağlayıcısı: Google ML Kit Text Recognition v2 (Latin).
 *
 * Tamamen cihaz üzerinde çalışır, internet ve API anahtarı gerektirmez.
 */
@Singleton
class MlKitOcrProvider @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OcrProvider {

    override val id: ProviderId = ProviderIds.MlKit
    override val name: String = "ML Kit (cihaz üzerinde)"
    override val requiresApiKey: Boolean = false
    override val isOnDevice: Boolean = true

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(image: Bitmap): OcrResult = withContext(ioDispatcher) {
        val visionText = try {
            recognizer.process(InputImage.fromBitmap(image, 0)).await()
        } catch (e: Exception) {
            throw OcrProviderException(id, "ML Kit metin tanıma başarısız oldu", e)
        }

        val blocks = visionText.textBlocks.map { block ->
            OcrTextBlock(
                text = block.text,
                confidence = block.lines.averageConfidence(),
                boundingBox = block.boundingBox?.let {
                    BoundingBox(it.left, it.top, it.right, it.bottom)
                },
            )
        }

        OcrResult(
            text = visionText.text,
            blocks = blocks,
            // Metin uzunluğuna göre ağırlıklı ortalama: uzun bloklar skoru daha çok etkiler.
            confidence = blocks.weightedConfidence(),
            providerId = id,
            recognizedLanguages = visionText.textBlocks
                .mapNotNull { it.recognizedLanguage.takeIf(String::isNotBlank) }
                .distinct(),
        )
    }
}

private fun List<com.google.mlkit.vision.text.Text.Line>.averageConfidence(): Float? {
    val scores = mapNotNull { it.confidence.takeIf { c -> !c.isNaN() } }
    return if (scores.isEmpty()) null else scores.average().toFloat()
}

private fun List<OcrTextBlock>.weightedConfidence(): Float? {
    val scored = filter { it.confidence != null && it.text.isNotBlank() }
    if (scored.isEmpty()) return null
    val totalWeight = scored.sumOf { it.text.length }.toDouble()
    if (totalWeight == 0.0) return null
    return scored.sumOf { it.confidence!!.toDouble() * it.text.length }
        .div(totalWeight)
        .toFloat()
        .coerceIn(0f, 1f)
}
