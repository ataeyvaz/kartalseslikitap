package com.kartal.seslikitap.data.provider.azure

import com.kartal.seslikitap.data.remote.azure.AzureAnalyzeResponse
import com.kartal.seslikitap.domain.model.BoundingBox
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.model.OcrTextBlock
import com.kartal.seslikitap.domain.provider.ProviderId

/**
 * Azure yanıtını sağlayıcıdan bağımsız [OcrResult]'a çevirir.
 *
 * Ağ ve Android bağımlılığı olmadığı için JVM birim testleriyle doğrulanır — yanıt
 * biçimindeki bir yanlış anlama sessizce bozuk metne yol açacağı için test edilmeye değer.
 */
object AzureReadMapper {

    /**
     * Azure adresini tam istek URL'ine çevirir. Kullanıcı sonunda eğik çizgiyle veya
     * çizgisiz yapıştırabilir; ikisi de kabul edilir.
     */
    fun buildAnalyzeUrl(endpoint: String): String {
        val normalized = endpoint.trim().trimEnd('/')
        return "$normalized/computervision/imageanalysis:analyze" +
            "?api-version=$API_VERSION&features=read"
    }

    fun toOcrResult(response: AzureAnalyzeResponse, providerId: ProviderId): OcrResult {
        val lines = response.readResult?.blocks.orEmpty().flatMap { it.lines }
        if (lines.isEmpty()) return OcrResult.empty(providerId)

        val blocks = lines.map { line ->
            OcrTextBlock(
                text = line.text,
                // Azure güveni kelime düzeyinde verir; satır skoru kelimelerin ortalamasıdır.
                confidence = line.words.mapNotNull { it.confidence }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat(),
                boundingBox = line.boundingPolygon.takeIf { it.isNotEmpty() }?.let { polygon ->
                    BoundingBox(
                        left = polygon.minOf { it.x },
                        top = polygon.minOf { it.y },
                        right = polygon.maxOf { it.x },
                        bottom = polygon.maxOf { it.y },
                    )
                },
            )
        }

        return OcrResult(
            // Azure satırları ayrı verir; paragraf yapısını metin temizleme katmanı kurar.
            text = blocks.joinToString("\n") { it.text },
            blocks = blocks,
            confidence = blocks.mapNotNull { it.confidence }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat(),
            providerId = providerId,
        )
    }

    private const val API_VERSION = "2024-02-01"
}
