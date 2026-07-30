package com.kartal.seslikitap.domain.usecase

import android.graphics.Bitmap
import android.util.Log
import com.kartal.seslikitap.data.image.PageImageStore
import com.kartal.seslikitap.domain.correction.Correction
import com.kartal.seslikitap.domain.correction.CorrectionResult
import com.kartal.seslikitap.domain.correction.TextCorrectionRegistry
import com.kartal.seslikitap.domain.imaging.PageCropper
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.provider.OcrProvider
import com.kartal.seslikitap.domain.provider.OcrProviderRegistry
import com.kartal.seslikitap.domain.repository.SettingsRepository
import java.io.File
import javax.inject.Inject

/**
 * "Çek -> kenar tespiti/perspektif düzeltme -> OCR" adımı.
 *
 * Aktif sağlayıcının güven skoru düşükse ve kullanıcı izin vermişse bulut sağlayıcıya
 * geçilir (plan Bölüm 1). Sağlayıcı seçimi [OcrProviderRegistry]'e bırakıldığı için
 * yeni bir OCR sağlayıcısı eklendiğinde bu use case değişmez.
 */
class RecognizePageUseCase @Inject constructor(
    private val imageStore: PageImageStore,
    private val pageCropper: PageCropper,
    private val ocrRegistry: OcrProviderRegistry,
    private val cleanOcrText: CleanOcrTextUseCase,
    private val correctionRegistry: TextCorrectionRegistry,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(imageFile: File): RecognizedPage {
        val original = imageStore.loadForOcr(imageFile)
        val cropResult = pageCropper.crop(original)
        val pageImage = cropResult.bitmap

        if (cropResult.wasCorrected) {
            // Kalıcı görüntü de düzeltilmiş hâl olsun: kullanıcı OCR'ın gördüğü kareyi görür.
            imageStore.save(pageImage, imageFile)
        }

        try {
            val primaryProvider = ocrRegistry.active()
            val primaryResult = primaryProvider.recognize(pageImage)

            val finalResult = maybeFallbackToCloud(primaryProvider, primaryResult, pageImage)
            val cleanedText = cleanOcrText(finalResult.text)
            val corrected = correct(cleanedText)

            return RecognizedPage(
                result = finalResult,
                cleanedText = corrected.text,
                corrections = corrected.corrections,
                wasPerspectiveCorrected = cropResult.wasCorrected,
                usedCloudFallback = finalResult.providerId != primaryResult.providerId,
            )
        } finally {
            pageImage.recycle()
            if (original !== pageImage && !original.isRecycled) original.recycle()
        }
    }

    /**
     * Düzeltme bir iyileştirmedir, ön koşul değil: sağlayıcı patlarsa ham metinle devam edilir.
     */
    private suspend fun correct(text: String): CorrectionResult {
        val provider = correctionRegistry.active()
        val languageTag = settingsRepository.getSettings().languageTag
        return runCatching { provider.correct(text, languageTag) }
            .onFailure { Log.w(TAG, "Metin düzeltme başarısız, ham metin kullanılıyor", it) }
            .getOrDefault(CorrectionResult.unchanged(text))
    }

    private suspend fun maybeFallbackToCloud(
        primaryProvider: OcrProvider,
        primaryResult: OcrResult,
        image: Bitmap,
    ): OcrResult {
        val settings = settingsRepository.getSettings()
        val cloudProvider = ocrRegistry.all()
            .firstOrNull { !it.isOnDevice && it.id != primaryProvider.id && it.isAvailable() }

        val shouldFallback = CloudFallbackPolicy.shouldFallback(
            confidence = primaryResult.confidence,
            settings = settings,
            isCloudProviderAvailable = cloudProvider != null,
            isAlreadyCloud = !primaryProvider.isOnDevice,
        )
        if (!shouldFallback || cloudProvider == null) return primaryResult

        // Bulut çağrısı başarısız olursa cihaz sonucuyla devam et: kullanıcı akışta kalsın.
        return runCatching { cloudProvider.recognize(image) }
            .onFailure { Log.w(TAG, "Bulut fallback başarısız, cihaz sonucu kullanılıyor", it) }
            .getOrDefault(primaryResult)
    }

    private companion object {
        const val TAG = "RecognizePageUseCase"
    }
}

data class RecognizedPage(
    val result: OcrResult,
    val cleanedText: String,
    val corrections: List<Correction> = emptyList(),
    val wasPerspectiveCorrected: Boolean = false,
    val usedCloudFallback: Boolean = false,
)
