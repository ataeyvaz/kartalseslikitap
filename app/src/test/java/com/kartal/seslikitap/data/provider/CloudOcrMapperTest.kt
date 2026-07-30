package com.kartal.seslikitap.data.provider

import com.kartal.seslikitap.data.provider.aws.TextractMapper
import com.kartal.seslikitap.data.provider.azure.AzureReadMapper
import com.kartal.seslikitap.data.remote.aws.TextractBlock
import com.kartal.seslikitap.data.remote.aws.TextractBoundingBox
import com.kartal.seslikitap.data.remote.aws.TextractGeometry
import com.kartal.seslikitap.data.remote.aws.TextractResponse
import com.kartal.seslikitap.data.remote.azure.AzureAnalyzeResponse
import com.kartal.seslikitap.data.remote.azure.AzureBlock
import com.kartal.seslikitap.data.remote.azure.AzureLine
import com.kartal.seslikitap.data.remote.azure.AzurePoint
import com.kartal.seslikitap.data.remote.azure.AzureReadResult
import com.kartal.seslikitap.data.remote.azure.AzureWord
import com.kartal.seslikitap.domain.provider.ProviderIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudOcrMapperTest {

    // --- Azure ---

    @Test
    fun `azure adresi sonunda egik cizgi olsa da olmasa da calisir`() {
        val withSlash = AzureReadMapper.buildAnalyzeUrl("https://kaynak.cognitiveservices.azure.com/")
        val withoutSlash = AzureReadMapper.buildAnalyzeUrl("https://kaynak.cognitiveservices.azure.com")
        assertEquals(withSlash, withoutSlash)
        assertTrue(withSlash.contains("/computervision/imageanalysis:analyze"))
        assertTrue(withSlash.contains("features=read"))
    }

    @Test
    fun `azure satirlari metne ve skora cevrilir`() {
        val response = AzureAnalyzeResponse(
            readResult = AzureReadResult(
                blocks = listOf(
                    AzureBlock(
                        lines = listOf(
                            AzureLine(
                                text = "Birinci satır",
                                boundingPolygon = listOf(
                                    AzurePoint(10, 20),
                                    AzurePoint(110, 20),
                                    AzurePoint(110, 50),
                                    AzurePoint(10, 50),
                                ),
                                words = listOf(
                                    AzureWord("Birinci", 0.9f),
                                    AzureWord("satır", 0.7f),
                                ),
                            ),
                            AzureLine(text = "İkinci satır", words = listOf(AzureWord("İkinci", 0.8f))),
                        ),
                    ),
                ),
            ),
        )

        val result = AzureReadMapper.toOcrResult(response, ProviderIds.AzureVision)

        assertEquals("Birinci satır\nİkinci satır", result.text)
        assertEquals(0.8f, result.blocks.first().confidence!!, 0.001f)
        assertEquals(ProviderIds.AzureVision, result.providerId)
        val box = result.blocks.first().boundingBox!!
        assertEquals(10, box.left)
        assertEquals(50, box.bottom)
    }

    @Test
    fun `azure bos yanit bos sonuc verir`() {
        val result = AzureReadMapper.toOcrResult(AzureAnalyzeResponse(), ProviderIds.AzureVision)
        assertTrue(result.isEmpty)
        assertNull(result.confidence)
    }

    // --- AWS Textract ---

    @Test
    fun `textract yuzde guveni sifir bir araligina cevrilir`() {
        val response = TextractResponse(
            blocks = listOf(
                TextractBlock(blockType = "LINE", text = "Satır", confidence = 99.5f),
            ),
        )

        val result = TextractMapper.toOcrResult(response, ProviderIds.AwsTextract, 1000, 2000)

        assertEquals(0.995f, result.confidence!!, 0.0001f)
    }

    @Test
    fun `textract oransal kutu piksele cevrilir`() {
        val response = TextractResponse(
            blocks = listOf(
                TextractBlock(
                    blockType = "LINE",
                    text = "Satır",
                    confidence = 90f,
                    geometry = TextractGeometry(
                        TextractBoundingBox(left = 0.1f, top = 0.2f, width = 0.5f, height = 0.1f),
                    ),
                ),
            ),
        )

        val box = TextractMapper.toOcrResult(response, ProviderIds.AwsTextract, 1000, 2000)
            .blocks.first().boundingBox!!

        assertEquals(100, box.left)
        assertEquals(400, box.top)
        assertEquals(600, box.right)
        assertEquals(600, box.bottom)
    }

    @Test
    fun `textract sadece satir bloklarini kullanir`() {
        val response = TextractResponse(
            blocks = listOf(
                TextractBlock(blockType = "PAGE", text = "yok sayılmalı", confidence = 99f),
                TextractBlock(blockType = "LINE", text = "kullanılmalı", confidence = 99f),
                TextractBlock(blockType = "WORD", text = "kullanılmalı", confidence = 99f),
            ),
        )

        val result = TextractMapper.toOcrResult(response, ProviderIds.AwsTextract, 100, 100)

        assertEquals("kullanılmalı", result.text)
        assertEquals(1, result.blocks.size)
    }
}
