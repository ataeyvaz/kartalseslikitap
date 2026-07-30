package com.kartal.seslikitap.domain.provider

import android.graphics.Bitmap
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.security.CredentialField

/**
 * Değiştirilebilir OCR sağlayıcısı.
 *
 * Üst katmanlar asla somut bir sağlayıcıyı (ML Kit, Cloud Vision...) bilmez; sadece
 * [OcrProviderRegistry] üzerinden aktif sağlayıcıyı ister. Yeni bir sağlayıcı eklemek,
 * bu arayüzü uygulayıp Hilt multibinding ile set'e eklemekten ibarettir.
 */
interface OcrProvider {

    val id: ProviderId

    /** Ayarlar ekranında gösterilecek isim. */
    val name: String

    val requiresApiKey: Boolean

    val isOnDevice: Boolean

    /**
     * Kullanıcıdan istenecek kimlik bilgisi alanları. Tek anahtarla çalışan sağlayıcılarda
     * varsayılan yeterlidir; Azure/AWS gibi ek alan isteyenler bunu genişletir.
     */
    val credentialFields: List<CredentialField>
        get() = if (requiresApiKey) listOf(CredentialField.ApiKey) else emptyList()

    /**
     * Sağlayıcı şu an kullanılabilir mi? (model indirilmiş mi, API anahtarı girilmiş mi...)
     * Ayarlar ekranı bunu kullanarak sağlayıcıyı pasif gösterebilir.
     */
    suspend fun isAvailable(): Boolean = true

    /**
     * Görüntüyü metne çevirir.
     *
     * @throws OcrProviderException tanıma başarısız olursa.
     */
    suspend fun recognize(image: Bitmap): OcrResult
}
