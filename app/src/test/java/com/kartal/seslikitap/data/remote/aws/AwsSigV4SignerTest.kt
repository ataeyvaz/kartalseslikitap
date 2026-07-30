package com.kartal.seslikitap.data.remote.aws

import com.kartal.seslikitap.data.remote.aws.AwsSigV4Signer.toHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Test

class AwsSigV4SignerTest {

    /**
     * AWS dokümantasyonundaki resmi imzalama anahtarı türetme örneği.
     * Bu vektör tutuyorsa HMAC zinciri ve karakter kodlaması doğrudur.
     */
    @Test
    fun `imzalama anahtari aws test vektoruyle eslesir`() {
        val key = AwsSigV4Signer.signingKey(
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            dateStamp = "20150830",
            region = "us-east-1",
            service = "iam",
        )

        assertEquals(
            "c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9",
            key.toHex(),
        )
    }

    @Test
    fun `authorization basligi beklenen bilesenleri tasir`() {
        val signed = AwsSigV4Signer.sign(
            credentials = AwsSigV4Signer.Credentials(
                accessKeyId = "AKIDEXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
                region = "eu-central-1",
            ),
            service = "textract",
            host = "textract.eu-central-1.amazonaws.com",
            httpMethod = "POST",
            canonicalUri = "/",
            canonicalQueryString = "",
            headers = mapOf(
                "content-type" to "application/x-amz-json-1.1",
                "x-amz-target" to "Textract.DetectDocumentText",
            ),
            payload = """{"Document":{"Bytes":"AAAA"}}""".toByteArray(),
            timestamp = utcDate("20240115T120000Z"),
        )

        val authorization = signed.headers.getValue("Authorization")
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 "))
        assertTrue(authorization.contains("Credential=AKIDEXAMPLE/20240115/eu-central-1/textract/aws4_request"))
        // İmzalanan başlıklar alfabetik ve küçük harf olmalı.
        assertTrue(
            authorization.contains(
                "SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date;x-amz-target",
            ),
        )
    }

    @Test
    fun `zorunlu amz basliklari eklenir`() {
        val signed = signSample()

        assertEquals("textract.eu-central-1.amazonaws.com", signed.headers["host"])
        assertEquals("20240115T120000Z", signed.headers["x-amz-date"])
        // Boş gövdenin SHA-256'sı sabittir; hash'in gerçekten hesaplandığını gösterir.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            signed.headers["x-amz-content-sha256"],
        )
    }

    @Test
    fun `ayni girdi ayni imzayi uretir`() {
        assertEquals(
            signSample().headers["Authorization"],
            signSample().headers["Authorization"],
        )
    }

    @Test
    fun `gövde degisince imza degisir`() {
        val first = signSample(payload = "a".toByteArray())
        val second = signSample(payload = "b".toByteArray())
        assertTrue(first.headers["Authorization"] != second.headers["Authorization"])
    }

    @Test
    fun `bolge adresi kucuk harfe cevrilir`() {
        assertEquals("textract.eu-central-1.amazonaws.com", TextractMapperHost("EU-Central-1"))
        assertEquals("https://textract.us-east-1.amazonaws.com/", TextractMapperUrl("us-east-1"))
    }

    private fun signSample(payload: ByteArray = ByteArray(0)) = AwsSigV4Signer.sign(
        credentials = AwsSigV4Signer.Credentials(
            accessKeyId = "AKIDEXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            region = "eu-central-1",
        ),
        service = "textract",
        host = "textract.eu-central-1.amazonaws.com",
        httpMethod = "POST",
        canonicalUri = "/",
        canonicalQueryString = "",
        headers = mapOf("content-type" to "application/x-amz-json-1.1"),
        payload = payload,
        timestamp = utcDate("20240115T120000Z"),
    )

    private fun utcDate(value: String) =
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(value)!!

    private fun TextractMapperHost(region: String) =
        com.kartal.seslikitap.data.provider.aws.TextractMapper.host(region)

    private fun TextractMapperUrl(region: String) =
        com.kartal.seslikitap.data.provider.aws.TextractMapper.buildEndpointUrl(region)
}
