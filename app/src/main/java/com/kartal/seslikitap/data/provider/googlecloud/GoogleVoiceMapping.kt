package com.kartal.seslikitap.data.provider.googlecloud

import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.VoiceScoring

/**
 * Google Cloud TTS'e özgü saf eşleme kuralları.
 *
 * Ağ ve Android bağımlılığı olmadığı için JVM birim testleriyle doğrulanabilir; bu kurallar
 * sesin doğallığını doğrudan etkilediği için test edilmeye değer.
 */
object GoogleVoiceMapping {

    /** Google'ın ses aileleri, doğallık sırasına göre. Studio > Neural2 > WaveNet > Standard. */
    fun qualityOf(voiceName: String): Float = when {
        voiceName.contains("Studio", ignoreCase = true) -> 1.0f
        voiceName.contains("Neural2", ignoreCase = true) -> 0.9f
        voiceName.contains("Wavenet", ignoreCase = true) -> 0.75f
        voiceName.contains("Polyglot", ignoreCase = true) -> 0.7f
        else -> 0.4f
    }

    fun genderOf(ssmlGender: String): NarratorGender = when (ssmlGender.uppercase()) {
        "FEMALE" -> NarratorGender.FEMALE
        "MALE" -> NarratorGender.MALE
        else -> NarratorGender.NEUTRAL
    }

    fun toSsmlGender(gender: NarratorGender): String = when (gender) {
        NarratorGender.FEMALE -> "FEMALE"
        NarratorGender.MALE -> "MALE"
        NarratorGender.NEUTRAL -> "NEUTRAL"
    }

    /**
     * Google'da pitch **yarım ton** cinsindendir (-20..20), bizim modelimizde ise çarpan.
     * 1.0 çarpanı 0 yarım tona karşılık gelir; 1.08 (çocuk ön ayarı) yaklaşık +1 yarım ton olur.
     */
    fun pitchToSemitones(pitchMultiplier: Float): Double =
        ((pitchMultiplier - 1.0f) * SEMITONES_PER_UNIT).toDouble().coerceIn(MIN_PITCH, MAX_PITCH)

    /** Google konuşma hızı 0.25..4.0 aralığını kabul eder. */
    fun clampSpeakingRate(rate: Float): Double = rate.toDouble().coerceIn(MIN_RATE, MAX_RATE)

    /**
     * İstenen sese en uygun adayı seçer. Seçim kuralı sağlayıcıdan bağımsızdır
     * ([VoiceScoring]); burada yalnızca Google'a özgü kalite/etiket bilgisi üretilir.
     */
    fun selectVoice(candidates: List<ProviderVoice>, config: VoiceConfig): ProviderVoice? =
        VoiceScoring.select(candidates, config)

    /** Çocuk kitabı okumaya uygun kabul edilen ses aileleri. */
    fun isChildFriendly(voiceName: String): Boolean =
        voiceName.contains("Studio", ignoreCase = true) ||
            voiceName.contains("Neural2", ignoreCase = true)

    private const val SEMITONES_PER_UNIT = 12f
    private const val MIN_PITCH = -20.0
    private const val MAX_PITCH = 20.0
    private const val MIN_RATE = 0.25
    private const val MAX_RATE = 4.0
}
