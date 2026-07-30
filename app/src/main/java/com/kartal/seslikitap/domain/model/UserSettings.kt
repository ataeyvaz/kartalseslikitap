package com.kartal.seslikitap.domain.model

import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds

/**
 * Uygulama genelindeki kullanıcı tercihleri.
 *
 * DİKKAT: API anahtarları bilinçli olarak burada **yok**. Plan Bölüm 2 gereği anahtarlar
 * Room'da değil, Android Keystore destekli EncryptedSharedPreferences'ta saklanacak
 * (Faz 2). Room veritabanı yedeklenebilir/dışa aktarılabilir olduğu için anahtar
 * barındırmamalıdır.
 */
data class UserSettings(
    val defaultOcrProviderId: ProviderId = ProviderIds.MlKit,
    val defaultTtsProviderId: ProviderId = ProviderIds.AndroidTts,
    /** OCR sonrası metin düzeltme; varsayılan olarak kapalı (metne dokunulmaz). */
    val textCorrectionProviderId: ProviderId = ProviderIds.NoCorrection,
    val defaultNarratorGender: NarratorGender = NarratorGender.NEUTRAL,
    val playbackSpeed: Float = VoiceConfig.DEFAULT_SPEAKING_RATE,
    val pitch: Float = VoiceConfig.DEFAULT_PITCH,
    /** Güven skoru düşükse otomatik bulut sağlayıcıya geçilsin mi (Faz 2, maliyet doğurur). */
    val autoFallbackToCloud: Boolean = false,
    /** Altına düşünce fallback tetiklenecek güven eşiği. */
    val cloudFallbackConfidenceThreshold: Float = 0.6f,
    /** Sentezde kullanılacak BCP-47 dil etiketi; null ise cihaz dili. */
    val languageTag: String? = null,
) {
    companion object {
        val Default = UserSettings()
    }
}
