package com.kartal.seslikitap.domain.provider

import com.kartal.seslikitap.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kayıtlı OCR sağlayıcılarının tek giriş noktası.
 *
 * Sağlayıcılar Hilt multibinding (`@IntoSet`) ile enjekte edilir; yeni sağlayıcı eklemek
 * için sadece DI modülüne bir satır eklenir.
 */
@Singleton
class OcrProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards OcrProvider>,
    private val settingsRepository: SettingsRepository,
) {
    fun all(): List<OcrProvider> = providers.sortedBy { it.name }

    fun byId(id: ProviderId): OcrProvider? = providers.firstOrNull { it.id == id }

    /** API anahtarı gerektirmeyen, offline çalışan varsayılan sağlayıcı. */
    fun defaultOnDevice(): OcrProvider =
        providers.firstOrNull { it.isOnDevice && !it.requiresApiKey }
            ?: throw ProviderUnavailableException(ProviderIds.MlKit, "Kayıtlı on-device OCR sağlayıcısı yok")

    /** Ayarlarda seçili sağlayıcı; yoksa on-device varsayılana düşer. */
    suspend fun active(): OcrProvider {
        val selectedId = settingsRepository.getSettings().defaultOcrProviderId
        return byId(selectedId)?.takeIf { it.isAvailable() } ?: defaultOnDevice()
    }
}

@Singleton
class TtsProviderRegistry @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TtsProvider>,
    private val settingsRepository: SettingsRepository,
) {
    fun all(): List<TtsProvider> = providers.sortedBy { it.name }

    fun byId(id: ProviderId): TtsProvider? = providers.firstOrNull { it.id == id }

    fun defaultOnDevice(): TtsProvider =
        providers.firstOrNull { it.isOnDevice && !it.requiresApiKey }
            ?: throw ProviderUnavailableException(ProviderIds.AndroidTts, "Kayıtlı on-device TTS sağlayıcısı yok")

    suspend fun active(): TtsProvider {
        val selectedId = settingsRepository.getSettings().defaultTtsProviderId
        return byId(selectedId)?.takeIf { it.isAvailable() } ?: defaultOnDevice()
    }
}
