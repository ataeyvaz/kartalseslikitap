package com.kartal.seslikitap.data.remote.aws

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 imzalayıcı.
 *
 * Textract, diğer sağlayıcıların aksine basit bir API anahtarı kabul etmez; her istek
 * imzalanmalıdır. Tek bir uç nokta için AWS SDK'sını (ve taşıdığı onlarca MB'ı) projeye
 * sokmak yerine imzalama burada elle yapılır.
 *
 * Saf Kotlin: AWS'in yayınladığı test vektörleriyle JVM üzerinde doğrulanabilir.
 */
object AwsSigV4Signer {

    data class Credentials(
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String,
    )

    data class SignedHeaders(val headers: Map<String, String>)

    /**
     * İsteği imzalar ve gönderilecek başlıkları döner.
     *
     * @param headers İmzaya dahil edilecek başlıklar (host dahil edilmeli değil, buradan eklenir).
     */
    fun sign(
        credentials: Credentials,
        service: String,
        host: String,
        httpMethod: String,
        canonicalUri: String,
        canonicalQueryString: String,
        headers: Map<String, String>,
        payload: ByteArray,
        timestamp: Date = Date(),
    ): SignedHeaders {
        val amzDate = amzDateFormat().format(timestamp)
        val dateStamp = dateStampFormat().format(timestamp)

        val payloadHash = payload.sha256().toHex()
        val allHeaders = buildMap {
            putAll(headers)
            put("host", host)
            put("x-amz-date", amzDate)
            put("x-amz-content-sha256", payloadHash)
        }

        // Kanonik başlıklar: adlar küçük harfe, değerler kırpılmış, ada göre sıralı.
        val sortedHeaders = allHeaders
            .map { (name, value) -> name.lowercase(Locale.ROOT) to value.trim() }
            .sortedBy { it.first }
        val canonicalHeaders = sortedHeaders.joinToString("") { "${it.first}:${it.second}\n" }
        val signedHeaderNames = sortedHeaders.joinToString(";") { it.first }

        val canonicalRequest = listOf(
            httpMethod,
            canonicalUri,
            canonicalQueryString,
            canonicalHeaders,
            signedHeaderNames,
            payloadHash,
        ).joinToString("\n")

        val credentialScope = "$dateStamp/${credentials.region}/$service/$TERMINATOR"
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            credentialScope,
            canonicalRequest.toByteArray(Charsets.UTF_8).sha256().toHex(),
        ).joinToString("\n")

        val signingKey = signingKey(credentials.secretAccessKey, dateStamp, credentials.region, service)
        val signature = hmacSha256(signingKey, stringToSign).toHex()

        val authorization = "$ALGORITHM " +
            "Credential=${credentials.accessKeyId}/$credentialScope, " +
            "SignedHeaders=$signedHeaderNames, " +
            "Signature=$signature"

        return SignedHeaders(
            headers = allHeaders + mapOf("Authorization" to authorization),
        )
    }

    /** İmzalama anahtarı: gizli anahtardan tarih/bölge/servis zinciriyle türetilir. */
    fun signingKey(
        secretAccessKey: String,
        dateStamp: String,
        region: String,
        service: String,
    ): ByteArray {
        val kDate = hmacSha256("$SECRET_PREFIX$secretAccessKey".toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        return hmacSha256(kService, TERMINATOR)
    }

    fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun amzDateFormat() = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun dateStampFormat() = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val TERMINATOR = "aws4_request"
    private const val SECRET_PREFIX = "AWS4"
    private const val HMAC_ALGORITHM = "HmacSHA256"
}
