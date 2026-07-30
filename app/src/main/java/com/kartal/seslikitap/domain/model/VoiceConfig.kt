package com.kartal.seslikitap.domain.model

/** Kitap bazında kullanıcıdan alınan anlatıcı cinsiyeti (bkz. plan Bölüm 4.5). */
enum class NarratorGender {
    FEMALE,
    MALE,
    NEUTRAL,
}

/**
 * Sağlayıcıdan bağımsız ses isteği.
 *
 * Provider'lar "çocuk kitabı" veya "cinsiyet" kavramını bilmez; sadece bu nesneyi alır ve
 * [com.kartal.seslikitap.domain.provider.VoiceMappingResolver] üzerinden kendi ses
 * kütüphanesindeki bir sese eşler.
 */
data class VoiceConfig(
    val gender: NarratorGender = NarratorGender.NEUTRAL,
    val isChildrenPreset: Boolean = false,
    val speakingRate: Float = DEFAULT_SPEAKING_RATE,
    val pitch: Float = DEFAULT_PITCH,
    /** BCP-47 dil etiketi, örn. "tr-TR". null ise cihaz dili kullanılır. */
    val languageTag: String? = null,
) {
    companion object {
        const val DEFAULT_SPEAKING_RATE = 1.0f
        const val DEFAULT_PITCH = 1.0f

        /** Çocuk kitabı modunda konuşma hızı çarpanı (planda ~%85-90). */
        const val CHILDREN_RATE_MULTIPLIER = 0.88f

        /** Çocuk kitabı modunda biraz daha canlı/sıcak tonlama için pitch çarpanı. */
        const val CHILDREN_PITCH_MULTIPLIER = 1.08f
    }
}
