package com.kartal.seslikitap.domain.provider

import com.kartal.seslikitap.domain.model.VoiceConfig

/**
 * Sağlayıcıdan bağımsız [VoiceConfig]'i, o sağlayıcının kendi ses kimliğine eşler
 * (plan Bölüm 4.5).
 *
 * Yeni bir TTS sağlayıcısı eklendiğinde sadece bu arayüzün o sağlayıcıya özel bir
 * uygulaması yazılır; üst katmanlarda hiçbir değişiklik gerekmez.
 */
interface VoiceMappingResolver {

    /** Bu çözümleyicinin hangi sağlayıcıya ait olduğu. */
    val providerId: ProviderId

    /**
     * Uygun sesi seçer. Hiçbir ses eşleşmezse null döner ve sağlayıcı kendi
     * varsayılan sesiyle devam eder.
     */
    suspend fun resolveVoice(config: VoiceConfig): ProviderVoice?

    /**
     * Önbelleğe alınmış ses listesini düşürür.
     *
     * API anahtarı değiştiğinde çağrılır: eski hesabın ses listesiyle istek yapmak
     * sessizce yanlış sesle okumaya yol açar. Önbelleklemeyen sağlayıcılarda gereksizdir.
     */
    suspend fun invalidateCache() = Unit
}

/**
 * Çocuk kitabı ön ayarını hız/pitch değerlerine uygular.
 * Provider'a gitmeden önce, provider-agnostik olarak çalıştırılır.
 */
fun VoiceConfig.applyPresets(): VoiceConfig =
    if (!isChildrenPreset) {
        this
    } else {
        copy(
            speakingRate = speakingRate * VoiceConfig.CHILDREN_RATE_MULTIPLIER,
            pitch = pitch * VoiceConfig.CHILDREN_PITCH_MULTIPLIER,
        )
    }
