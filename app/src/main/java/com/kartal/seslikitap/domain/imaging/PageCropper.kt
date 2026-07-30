package com.kartal.seslikitap.domain.imaging

import android.graphics.Bitmap

/**
 * Çekilen karede sayfa kenarlarını bulup perspektifi düzelten katman (plan Faz 1).
 *
 * OCR sağlayıcıları gibi bu da değiştirilebilir bir soyutlamadır: bugün OpenCV tabanlı
 * bir uygulaması var, yarın başka bir motor (ör. ML Kit Document Scanner) aynı arayüzü
 * uygulayabilir; çağıran taraf değişmez.
 */
interface PageCropper {

    /**
     * Sayfa dörtgenini bulup düzeltilmiş görüntüyü döner.
     *
     * Güvenilir bir dörtgen bulunamazsa **orijinal görüntüyle** döner: yanlış kırpmaktansa
     * ham kareyi OCR'a vermek her zaman daha iyidir.
     */
    suspend fun crop(bitmap: Bitmap): CropResult
}

data class CropResult(
    val bitmap: Bitmap,
    val quad: PageQuad?,
    val wasCorrected: Boolean,
)

/** Sayfa köşeleri, piksel koordinatlarında; sıra: sol-üst, sağ-üst, sağ-alt, sol-alt. */
data class PageQuad(
    val topLeft: PagePoint,
    val topRight: PagePoint,
    val bottomRight: PagePoint,
    val bottomLeft: PagePoint,
) {
    val points: List<PagePoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)
}

data class PagePoint(val x: Double, val y: Double)
