package com.kartal.seslikitap.domain.provider

import com.kartal.seslikitap.domain.model.AudioStream
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.security.CredentialField

/**
 * Değiştirilebilir TTS sağlayıcısı.
 *
 * Sağlayıcı, [VoiceConfig]'i kendi ses kütüphanesindeki bir sese eşlemek için
 * [VoiceMappingResolver] kullanır; "çocuk kitabı" / "anlatıcı cinsiyeti" gibi ürün
 * kavramlarını bilmez.
 */
interface TtsProvider {

    val id: ProviderId

    val name: String

    val requiresApiKey: Boolean

    val isOnDevice: Boolean

    /** SSML girdisini destekliyor mu? Desteklemiyorsa metin düz gönderilir. */
    val supportsSsml: Boolean get() = false

    /** Kullanıcıdan istenecek kimlik bilgisi alanları (bkz. [OcrProvider.credentialFields]). */
    val credentialFields: List<CredentialField>
        get() = if (requiresApiKey) listOf(CredentialField.ApiKey) else emptyList()

    suspend fun isAvailable(): Boolean = true

    /** Sağlayıcının sunduğu sesler; ayarlar ekranı ve eşleme katmanı kullanır. */
    suspend fun availableVoices(): List<ProviderVoice>

    /**
     * Metni sese dönüştürür.
     *
     * @throws TtsProviderException sentez başarısız olursa.
     */
    suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream
}

/**
 * Sentezlenmiş dosya üretmeden doğrudan cihazda konuşabilen sağlayıcılar için ek yetenek.
 *
 * Android TTS gibi on-device motorlarda "önce dosyaya yaz, sonra oynat" adımını atlayıp
 * anında okumaya başlamak mümkündür — MVP'nin "çek → OCR → oku" akışı bunu kullanır.
 * Bulut sağlayıcılar bu arayüzü uygulamaz; onlarda [TtsProvider.synthesize] + Media3 yolu izlenir.
 */
interface DirectSpeechTtsProvider {

    /** Konuşma bitene kadar askıda kalır. İptal edilirse konuşma durdurulur. */
    suspend fun speak(text: String, voice: VoiceConfig)

    fun stop()
}

/** Bir sağlayıcının kendi ses kimliği (örn. Android voice name, ElevenLabs voice_id). */
data class ProviderVoice(
    val id: String,
    val displayName: String,
    val gender: NarratorGender,
    val languageTag: String,
    /** Sağlayıcı tarafında çocuk kitabı okumaya uygun etiketlenmiş ses mi (plan Bölüm 4.5). */
    val isChildFriendly: Boolean = false,
    /** Ağ gerektiren (dolayısıyla offline çalışmayan) bir ses mi. */
    val requiresNetwork: Boolean = false,
    /** Sağlayıcının bildirdiği kalite; karşılaştırma için 0..1'e normalize edilmiş. */
    val quality: Float = 0.5f,
)
