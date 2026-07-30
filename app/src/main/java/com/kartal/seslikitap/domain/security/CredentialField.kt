package com.kartal.seslikitap.domain.security

/**
 * Bir sağlayıcının çalışmak için istediği tek bir kimlik bilgisi alanı.
 *
 * Sağlayıcıların hepsi tek anahtarla yetinmez: Azure kaynak adresini, AWS ise erişim
 * anahtarı + gizli anahtar + bölge üçlüsünü ister. Ayarlar ekranı bu listeyi okuyup
 * alanları kendisi üretir; yeni sağlayıcı eklendiğinde UI'da değişiklik gerekmez.
 */
data class CredentialField(
    val id: String,
    val label: String,
    /** Ekranda maskelensin mi? Bölge/adres gibi alanlar gizli değildir. */
    val isSecret: Boolean = true,
    val hint: String? = null,
) {
    companion object {
        const val DEFAULT_FIELD_ID = "api_key"

        /** Tek anahtarla çalışan sağlayıcılar için standart alan. */
        val ApiKey = CredentialField(
            id = DEFAULT_FIELD_ID,
            label = "API anahtarı",
            isSecret = true,
        )
    }
}
