package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.domain.model.UserSettings

/**
 * "Güven skoru düşükse buluta geç" kararı (plan Bölüm 1).
 *
 * Bulut çağrısı kullanıcıya **para** demektir; bu yüzden karar saf ve test edilebilir bir
 * fonksiyonda toplanmıştır ve tereddütte hayır der.
 */
object CloudFallbackPolicy {

    fun shouldFallback(
        confidence: Float?,
        settings: UserSettings,
        isCloudProviderAvailable: Boolean,
        isAlreadyCloud: Boolean,
    ): Boolean = when {
        // Kullanıcı açıkça izin vermediyse asla ücretli çağrı yapma.
        !settings.autoFallbackToCloud -> false
        // Zaten bulutta tanındıysa ikinci kez göndermek sadece maliyet üretir.
        isAlreadyCloud -> false
        // Anahtar yoksa/sağlayıcı hazır değilse geçilecek bir yer yok.
        !isCloudProviderAvailable -> false
        // Sağlayıcı skor bildirmiyorsa körlemesine para harcama.
        confidence == null -> false
        else -> confidence < settings.cloudFallbackConfidenceThreshold
    }
}
