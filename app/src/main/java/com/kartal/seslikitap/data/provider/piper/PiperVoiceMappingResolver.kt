package com.kartal.seslikitap.data.provider.piper

import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Piper sağlayıcısında şimdilik tek ses var ("Ata", erkek); cinsiyet tercihi ne olursa olsun o okunur.
 * Kadın sesi eğitildiğinde (Sözcük planı, Faz 7) burada cinsiyete göre seçim yapılır.
 */
@Singleton
class PiperVoiceMappingResolver @Inject constructor(
    private val provider: PiperTtsProvider,
) : VoiceMappingResolver {

    override val providerId: ProviderId = ProviderIds.PiperAta

    override suspend fun resolveVoice(config: VoiceConfig): ProviderVoice = provider.voiceInfo()
}
