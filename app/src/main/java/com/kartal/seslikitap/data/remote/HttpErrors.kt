package com.kartal.seslikitap.data.remote

import retrofit2.HttpException
import java.io.IOException

/**
 * Ağ hatalarını kullanıcının **ne yapacağını anlayabileceği** bir metne çevirir.
 *
 * "İstek başarısız" demek hata ayıklamayı imkânsız kılıyor: anahtar mı yanlış, kota mı
 * bitti, internet mi yok, yoksa API biçimi mi değişti — hepsi aynı mesaja dönüşüyordu.
 * Sağlayıcıların döndüğü gövde genelde tam olarak sebebi söyler, o yüzden taşınır.
 */
fun Throwable.describeNetworkError(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val explanation = when (code()) {
            401 -> "anahtar geçersiz"
            403 -> "anahtar bu işleme yetkili değil"
            404 -> "adres bulunamadı"
            422 -> "istek sağlayıcı tarafından reddedildi"
            429 -> "kota veya hız sınırı aşıldı"
            in 500..599 -> "sağlayıcı tarafında hata"
            else -> null
        }
        buildString {
            append("HTTP ${code()}")
            explanation?.let { append(" ($it)") }
            if (!body.isNullOrBlank()) append(": ${body.take(MAX_BODY_CHARS)}")
        }
    }

    is IOException -> "Ağ hatası: ${message ?: "bağlantı kurulamadı"}"

    else -> message ?: this::class.simpleName ?: "bilinmeyen hata"
}

private const val MAX_BODY_CHARS = 300
