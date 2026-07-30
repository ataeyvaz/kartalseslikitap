package com.kartal.seslikitap.domain.correction

import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kayıtlı metin düzeltme sağlayıcılarının tek giriş noktası.
 *
 * Bulut tabanlı bir dil modeli düzelticisi (ör. Claude) eklemek için tek gereken,
 * [TextCorrectionProvider]'ı uygulayan bir sınıf yazıp DI modülüne `@IntoSet` ile
 * kaydetmektir; buradaki ve üstündeki hiçbir kod değişmez.
 */
@Singleton
class TextCorrectionRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TextCorrectionProvider>,
    private val settingsRepository: SettingsRepository,
) {
    fun all(): List<TextCorrectionProvider> = providers.sortedBy { it.name }

    fun byId(id: ProviderId): TextCorrectionProvider? = providers.firstOrNull { it.id == id }

    /** Hiçbir şey yapmayan sağlayıcı; seçili sağlayıcı kullanılamazsa buraya düşülür. */
    fun noOp(): TextCorrectionProvider =
        byId(ProviderIds.NoCorrection)
            ?: error("Düzeltme yapmayan varsayılan sağlayıcı kayıtlı değil")

    suspend fun active(): TextCorrectionProvider {
        val selectedId = settingsRepository.getSettings().textCorrectionProviderId
        return byId(selectedId)?.takeIf { it.isAvailable() } ?: noOp()
    }
}
