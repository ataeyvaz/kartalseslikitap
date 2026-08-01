package com.kartal.seslikitap.data.provider.googlecloud

import android.graphics.Bitmap
import android.util.Base64
import com.kartal.seslikitap.data.remote.describeNetworkError
import com.kartal.seslikitap.data.remote.googlecloud.GoogleCloudVisionApi
import com.kartal.seslikitap.data.remote.googlecloud.VisionAnnotateRequest
import com.kartal.seslikitap.data.remote.googlecloud.VisionFeature
import com.kartal.seslikitap.data.remote.googlecloud.VisionImage
import com.kartal.seslikitap.data.remote.googlecloud.VisionImageContext
import com.kartal.seslikitap.data.remote.googlecloud.VisionRequest
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.BoundingBox
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.model.OcrTextBlock
import com.kartal.seslikitap.domain.provider.OcrProvider
import com.kartal.seslikitap.domain.provider.OcrProviderException
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.repository.SettingsRepository
import com.kartal.seslikitap.domain.security.ApiKeyStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Cloud Vision tabanlı OCR sağlayıcısı (BYOK).
 *
 * `DOCUMENT_TEXT_DETECTION` yoğun metinli kitap sayfaları için `TEXT_DETECTION`'dan
 * belirgin biçimde daha iyidir: satır/paragraf yapısını korur ve blok bazlı güven skoru döner.
 */
@Singleton
class GoogleCloudVisionOcrProvider @Inject constructor(
    private val api: GoogleCloudVisionApi,
    private val apiKeyStore: ApiKeyStore,
    private val settingsRepository: SettingsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OcrProvider {

    override val id: ProviderId = ProviderIds.GoogleCloudVision
    override val name: String = "Google Cloud Vision (bulut)"
    override val requiresApiKey: Boolean = true
    override val isOnDevice: Boolean = false

    override suspend fun isAvailable(): Boolean = !apiKeyStore.getKey(id).isNullOrBlank()

    override suspend fun recognize(image: Bitmap): OcrResult = withContext(ioDispatcher) {
        val apiKey = apiKeyStore.getKey(id)
            ?: throw OcrProviderException(id, "Google Cloud API anahtarı girilmemiş")

        val languageTag = settingsRepository.getSettings().languageTag
        val request = VisionAnnotateRequest(
            requests = listOf(
                VisionRequest(
                    image = VisionImage(content = image.toBase64Jpeg()),
                    features = listOf(VisionFeature(type = FEATURE_DOCUMENT_TEXT)),
                    imageContext = languageTag?.let { VisionImageContext(listOf(it)) },
                ),
            ),
        )

        val response = try {
            api.annotate(apiKey, request)
        } catch (e: Exception) {
            throw OcrProviderException(
                id,
                "Google Cloud Vision isteği başarısız — ${e.describeNetworkError()}",
                e,
            )
        }

        val first = response.responses.firstOrNull()
            ?: throw OcrProviderException(id, "Google Cloud Vision boş yanıt döndü")
        first.error?.takeIf { it.code != 0 }?.let {
            throw OcrProviderException(id, "Google Cloud Vision hatası: ${it.message}")
        }

        val annotation = first.fullTextAnnotation ?: return@withContext OcrResult.empty(id)

        val blocks = annotation.pages.flatMap { page ->
            page.blocks.map { block ->
                OcrTextBlock(
                    text = block.toText(),
                    confidence = block.confidence,
                    boundingBox = block.boundingBox?.vertices?.takeIf { it.isNotEmpty() }?.let { vertices ->
                        BoundingBox(
                            left = vertices.minOf { it.x },
                            top = vertices.minOf { it.y },
                            right = vertices.maxOf { it.x },
                            bottom = vertices.maxOf { it.y },
                        )
                    },
                )
            }
        }

        OcrResult(
            text = annotation.text,
            blocks = blocks,
            confidence = blocks.mapNotNull { it.confidence }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            providerId = id,
            recognizedLanguages = annotation.pages
                .flatMap { it.property?.detectedLanguages.orEmpty() }
                .map { it.languageCode }
                .filter(String::isNotBlank)
                .distinct(),
        )
    }

    private fun Bitmap.toBase64Jpeg(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun com.kartal.seslikitap.data.remote.googlecloud.VisionBlock.toText(): String =
        paragraphs.joinToString("\n") { paragraph ->
            paragraph.words.joinToString(" ") { word ->
                word.symbols.joinToString("") { it.text }
            }
        }

    private companion object {
        const val FEATURE_DOCUMENT_TEXT = "DOCUMENT_TEXT_DETECTION"
        const val JPEG_QUALITY = 90
    }
}
