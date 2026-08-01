package com.kartal.seslikitap.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.UnknownHostException

/**
 * Hata mesajı kullanıcının **ne yapacağını** anlatmalı.
 *
 * "İstek başarısız" mesajı sahada hiçbir işe yaramadı: anahtar mı yanlış, kota mı bitti,
 * internet mi yok ayırt edilemiyordu. Bu yüzden mesaj üretimi test altında.
 */
class HttpErrorsTest {

    @Test
    fun `gecersiz anahtar acikca soylenir`() {
        val message = httpError(401, """{"detail":"Invalid API key"}""").describeNetworkError()

        assertTrue(message, message.contains("401"))
        assertTrue(message, message.contains("anahtar geçersiz"))
        // Sağlayıcının kendi açıklaması da taşınmalı.
        assertTrue(message, message.contains("Invalid API key"))
    }

    @Test
    fun `kota asimi acikca soylenir`() {
        val message = httpError(429, "rate limit").describeNetworkError()
        assertTrue(message, message.contains("kota veya hız sınırı"))
    }

    @Test
    fun `reddedilen istek gövdesiyle birlikte gosterilir`() {
        val message = httpError(422, """{"detail":{"message":"voice_id not found"}}""")
            .describeNetworkError()

        assertTrue(message, message.contains("422"))
        assertTrue(message, message.contains("voice_id not found"))
    }

    @Test
    fun `saglayici tarafi hatasi ayrilir`() {
        val message = httpError(503, "").describeNetworkError()
        assertTrue(message, message.contains("sağlayıcı tarafında hata"))
    }

    @Test
    fun `ag hatasi ayrilir`() {
        val message = UnknownHostException("api.elevenlabs.io").describeNetworkError()
        assertTrue(message, message.startsWith("Ağ hatası"))
    }

    @Test
    fun `cok uzun govde kirpilir`() {
        val message = httpError(400, "x".repeat(5_000)).describeNetworkError()
        assertTrue("Mesaj çok uzun: ${message.length}", message.length < 400)
    }

    @Test
    fun `beklenmeyen hata mesaji korunur`() {
        val message = IOException("soket kapandı").describeNetworkError()
        assertTrue(message, message.contains("soket kapandı"))
    }

    private fun httpError(code: Int, body: String): HttpException =
        HttpException(
            Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
        )
}
