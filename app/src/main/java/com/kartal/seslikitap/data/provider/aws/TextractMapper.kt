package com.kartal.seslikitap.data.provider.aws

import com.kartal.seslikitap.data.remote.aws.TextractResponse
import com.kartal.seslikitap.domain.model.BoundingBox
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.model.OcrTextBlock
import com.kartal.seslikitap.domain.provider.ProviderId
import kotlin.math.roundToInt

/**
 * Textract yanıtını sağlayıcıdan bağımsız [OcrResult]'a çevirir.
 *
 * İki dönüşüm dikkat ister ve bu yüzden test edilir: güven **yüzde** gelir (0-100),
 * kutu koordinatları ise görüntü boyutuna **oran** olarak gelir (0..1).
 */
object TextractMapper {

    fun buildEndpointUrl(region: String): String = "https://${host(region)}/"

    fun host(region: String): String = "textract.${region.trim().lowercase()}.amazonaws.com"

    fun toOcrResult(
        response: TextractResponse,
        providerId: ProviderId,
        imageWidth: Int,
        imageHeight: Int,
    ): OcrResult {
        val lines = response.blocks.filter { it.blockType == BLOCK_TYPE_LINE }
        if (lines.isEmpty()) return OcrResult.empty(providerId)

        val blocks = lines.map { line ->
            OcrTextBlock(
                text = line.text,
                confidence = line.confidence?.let { it / PERCENT_SCALE },
                boundingBox = line.geometry?.boundingBox?.let { box ->
                    BoundingBox(
                        left = (box.left * imageWidth).roundToInt(),
                        top = (box.top * imageHeight).roundToInt(),
                        right = ((box.left + box.width) * imageWidth).roundToInt(),
                        bottom = ((box.top + box.height) * imageHeight).roundToInt(),
                    )
                },
            )
        }

        return OcrResult(
            text = blocks.joinToString("\n") { it.text },
            blocks = blocks,
            confidence = blocks.mapNotNull { it.confidence }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.toFloat(),
            providerId = providerId,
        )
    }

    private const val BLOCK_TYPE_LINE = "LINE"
    private const val PERCENT_SCALE = 100f
}
