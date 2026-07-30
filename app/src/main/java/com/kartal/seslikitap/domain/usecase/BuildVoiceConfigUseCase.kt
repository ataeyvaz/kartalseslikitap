package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.applyPresets
import com.kartal.seslikitap.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Kitabın kullanıcı girdileri (çocuk kitabı mı, anlatıcı cinsiyeti) + genel ayarları
 * sağlayıcıdan bağımsız bir [VoiceConfig]'e dönüştürür.
 *
 * Provider seçimi bu adımdan sonra gelir; böylece hangi TTS sağlayıcısı aktifse olsun
 * aynı ürün kuralları uygulanır.
 */
class BuildVoiceConfigUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(book: Book): VoiceConfig {
        val settings = settingsRepository.getSettings()
        return VoiceConfig(
            gender = book.narratorGender,
            isChildrenPreset = book.isChildrenBook,
            speakingRate = settings.playbackSpeed,
            pitch = settings.pitch,
            languageTag = settings.languageTag,
        ).applyPresets()
    }
}
