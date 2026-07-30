package com.kartal.seslikitap.data.provider.androidtts

import android.speech.tts.Voice
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android TTS motorunun ses listesini [VoiceConfig]'e eşler (plan Bölüm 4.5).
 *
 * Android, ses cinsiyetini API'de açıkça vermez; motorlar bunu ses adında kodlar
 * (örn. `tr-tr-x-ama#female_1-local`). Bu yüzden ad üzerinden çıkarım yapılır ve
 * eşleşme bulunamazsa cinsiyet nötr kabul edilip motorun varsayılanı kullanılır.
 */
@Singleton
class AndroidTtsVoiceMappingResolver @Inject constructor(
    private val engine: AndroidTtsEngine,
) : VoiceMappingResolver {

    override val providerId: ProviderId = ProviderIds.AndroidTts

    suspend fun availableVoices(): List<ProviderVoice> {
        val tts = engine.getOrNull() ?: return emptyList()
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        return voices.map { it.toProviderVoice() }
    }

    override suspend fun resolveVoice(config: VoiceConfig): ProviderVoice? {
        val candidates = availableVoices().filter { !it.requiresNetwork || config.languageTag != null }
        if (candidates.isEmpty()) return null

        val targetLanguage = config.languageTag ?: Locale.getDefault().toLanguageTag()
        val languageMatches = candidates.filter { it.matchesLanguage(targetLanguage) }
            .ifEmpty { candidates.filter { it.matchesLanguage(targetLanguage.substringBefore('-')) } }
            .ifEmpty { return null }

        return languageMatches.maxByOrNull { it.score(config) }
    }
}

private fun Voice.toProviderVoice(): ProviderVoice {
    val lowerName = name.lowercase(Locale.ROOT)
    val gender = when {
        FEMALE_MARKERS.any { lowerName.contains(it) } -> NarratorGender.FEMALE
        MALE_MARKERS.any { lowerName.contains(it) } -> NarratorGender.MALE
        else -> NarratorGender.NEUTRAL
    }
    return ProviderVoice(
        id = name,
        displayName = name,
        gender = gender,
        languageTag = locale.toLanguageTag(),
        // Android tarafında "çocuk kitabına uygun" diye bir etiket yok; bulut sağlayıcılar
        // (Faz 3) bu alanı gerçek etiketlerle dolduracak.
        isChildFriendly = false,
        requiresNetwork = isNetworkConnectionRequired,
        quality = quality.normalizedQuality(),
    )
}

private fun ProviderVoice.matchesLanguage(tag: String): Boolean =
    languageTag.equals(tag, ignoreCase = true) ||
        languageTag.substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true)

private fun ProviderVoice.score(config: VoiceConfig): Float {
    var score = quality
    if (config.gender != NarratorGender.NEUTRAL && gender == config.gender) score += 1.0f
    if (config.gender == NarratorGender.NEUTRAL && gender == NarratorGender.FEMALE) score += 0.1f
    if (config.isChildrenPreset && isChildFriendly) score += 0.5f
    // Offline çalışabilirlik Faz 1'in temel vaadi; ağ gerektiren sesleri geri plana at.
    if (requiresNetwork) score -= 0.75f
    return score
}

private fun Int.normalizedQuality(): Float = when {
    this >= Voice.QUALITY_VERY_HIGH -> 1.0f
    this >= Voice.QUALITY_HIGH -> 0.8f
    this >= Voice.QUALITY_NORMAL -> 0.6f
    this >= Voice.QUALITY_LOW -> 0.4f
    else -> 0.2f
}

private val FEMALE_MARKERS = listOf("female", "#f", "-f0", "woman")
private val MALE_MARKERS = listOf("male", "#m", "-m0", "man")
