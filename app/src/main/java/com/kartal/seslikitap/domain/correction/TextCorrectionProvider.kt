package com.kartal.seslikitap.domain.correction

import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.security.CredentialField

/**
 * OCR sonrası metin düzeltme katmanı — OCR/TTS gibi değiştirilebilir bir sağlayıcı.
 *
 * Tasarım kuralı: düzeltme **asla metin uydurmamalıdır**. Kitap metninde sessizce
 * değiştirilmiş bir cümle, gözle görülür bir OCR hatasından daha zararlıdır; kullanıcı
 * yanlışın farkına varamaz. Bu yüzden her uygulama, yaptığı değişiklikleri [CorrectionResult]
 * içinde tek tek raporlamak zorundadır.
 */
interface TextCorrectionProvider {

    val id: ProviderId

    val name: String

    val requiresApiKey: Boolean

    val isOnDevice: Boolean

    val credentialFields: List<CredentialField>
        get() = if (requiresApiKey) listOf(CredentialField.ApiKey) else emptyList()

    suspend fun isAvailable(): Boolean = true

    /**
     * Metni düzeltir.
     *
     * @param languageTag BCP-47 dil etiketi; sağlayıcı desteklemiyorsa metni olduğu gibi döner.
     * @throws TextCorrectionException düzeltme başarısız olursa. Çağıran taraf hatayı
     *   yutup ham metinle devam etmelidir: düzeltme bir iyileştirmedir, ön koşul değil.
     */
    suspend fun correct(text: String, languageTag: String?): CorrectionResult
}

data class CorrectionResult(
    val text: String,
    val corrections: List<Correction> = emptyList(),
) {
    val hasChanges: Boolean get() = corrections.isNotEmpty()

    companion object {
        fun unchanged(text: String) = CorrectionResult(text)
    }
}

data class Correction(
    val original: String,
    val corrected: String,
    val reason: CorrectionReason,
)

enum class CorrectionReason {
    /** Kaybolmuş Türkçe işaretler geri kondu: "sarki" -> "şarkı". */
    DIACRITIC_RESTORED,

    /** Birbirine benzeyen karakterler düzeltildi: "rn" -> "m", "1" -> "l". */
    GLYPH_CONFUSION,

    /** Sağlayıcı gerekçe bildirmedi (ör. dil modeli). */
    UNSPECIFIED,
}

class TextCorrectionException(
    val providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
