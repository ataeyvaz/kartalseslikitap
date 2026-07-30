package com.kartal.seslikitap.data.provider.elevenlabs

import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsVoiceSettings
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig

/**
 * ElevenLabs'a özgü saf eşleme kuralları.
 *
 * Ağ ve Android bağımlılığı yok; JVM birim testleriyle doğrulanır.
 */
object ElevenLabsVoiceMapping {

    /** ElevenLabs ses cinsiyetini serbest biçimli `labels` sözlüğünde taşır. */
    fun genderOf(labels: Map<String, String>): NarratorGender =
        when (labels["gender"]?.lowercase()) {
            "female" -> NarratorGender.FEMALE
            "male" -> NarratorGender.MALE
            else -> NarratorGender.NEUTRAL
        }

    /**
     * Kitap dinleme deneyiminde anlatım (narration) için etiketlenmiş sesler en iyisidir;
     * klonlanmış/profesyonel sesler de hazır seslerin önüne geçer.
     */
    fun qualityOf(labels: Map<String, String>, category: String): Float {
        var quality = when (category.lowercase()) {
            "professional" -> 0.9f
            "cloned" -> 0.8f
            else -> 0.7f
        }
        val useCase = labels["use_case"] ?: labels["use case"] ?: ""
        if (useCase.contains("narrat", ignoreCase = true)) quality += 0.15f
        if (useCase.contains("audiobook", ignoreCase = true)) quality += 0.15f
        return quality.coerceAtMost(1.0f)
    }

    /** Çocuk kitabında canlı/karakterli anlatım ve genç ses tonu daha uygundur. */
    fun isChildFriendly(labels: Map<String, String>): Boolean {
        val useCase = labels["use_case"] ?: labels["use case"] ?: ""
        val age = labels["age"]?.lowercase() ?: ""
        return useCase.contains("character", ignoreCase = true) ||
            useCase.contains("animation", ignoreCase = true) ||
            age.contains("young")
    }

    /** ElevenLabs hız aralığı dardır; kullanıcının seçtiği hız buraya kırpılır. */
    fun clampSpeed(rate: Float): Double = rate.toDouble().coerceIn(MIN_SPEED, MAX_SPEED)

    /**
     * Ses ayarları. Çocuk kitabı modunda `style` yükseltilip `stability` düşürülür:
     * ElevenLabs'ta bu ikili tonlamayı canlandırır. Uzun düz metinde ise tutarlılık
     * (yüksek stability) daha iyi bir dinleme deneyimi verir.
     */
    fun settingsFor(config: VoiceConfig): ElevenLabsVoiceSettings = ElevenLabsVoiceSettings(
        stability = if (config.isChildrenPreset) CHILD_STABILITY else NARRATION_STABILITY,
        similarityBoost = SIMILARITY_BOOST,
        style = if (config.isChildrenPreset) CHILD_STYLE else NARRATION_STYLE,
        useSpeakerBoost = true,
        speed = clampSpeed(config.speakingRate),
    )

    private const val MIN_SPEED = 0.7
    private const val MAX_SPEED = 1.2
    private const val NARRATION_STABILITY = 0.55
    private const val CHILD_STABILITY = 0.35
    private const val NARRATION_STYLE = 0.0
    private const val CHILD_STYLE = 0.35
    private const val SIMILARITY_BOOST = 0.75
}
