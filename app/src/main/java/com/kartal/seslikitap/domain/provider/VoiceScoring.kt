package com.kartal.seslikitap.domain.provider

import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig

/**
 * Sağlayıcıdan bağımsız ses seçim kuralı.
 *
 * Hangi seslerin var olduğu sağlayıcıya özgüdür (o yüzden her sağlayıcının kendi
 * [VoiceMappingResolver]'ı vardır), ama "kullanıcının istediği cinsiyet doğallığın önünde
 * gelir, çocuk kitabında uygun etiketli ses öne çıkar" kuralı bir **ürün** kararıdır ve
 * tüm sağlayıcılarda aynı olmalıdır.
 */
object VoiceScoring {

    fun select(candidates: List<ProviderVoice>, config: VoiceConfig): ProviderVoice? =
        candidates.maxByOrNull { score(it, config) }

    fun score(voice: ProviderVoice, config: VoiceConfig): Float {
        var score = voice.quality
        if (config.gender != NarratorGender.NEUTRAL) {
            // İstenen cinsiyet, ses kalitesinden daha ağır basar: kullanıcı bunu bilerek seçti.
            if (voice.gender == config.gender) score += GENDER_MATCH_BONUS else score -= GENDER_MISMATCH_PENALTY
        }
        if (config.isChildrenPreset && voice.isChildFriendly) score += CHILD_PRESET_BONUS
        return score
    }

    private const val GENDER_MATCH_BONUS = 1.0f
    private const val GENDER_MISMATCH_PENALTY = 0.5f
    private const val CHILD_PRESET_BONUS = 0.4f
}
