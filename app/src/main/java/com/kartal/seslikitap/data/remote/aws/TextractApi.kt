package com.kartal.seslikitap.data.remote.aws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * AWS Textract `DetectDocumentText` uç noktası.
 *
 * Adres bölgeye özeldir (`https://textract.<bolge>.amazonaws.com/`) ve başlıklar
 * [AwsSigV4Signer] tarafından üretilir; bu yüzden hem URL hem başlıklar dinamiktir.
 */
interface TextractApi {

    @POST
    suspend fun detectDocumentText(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: TextractRequest,
    ): TextractResponse
}

@Serializable
data class TextractRequest(
    @SerialName("Document") val document: TextractDocument,
)

@Serializable
data class TextractDocument(
    @SerialName("Bytes") val bytes: String,
)

@Serializable
data class TextractResponse(
    @SerialName("Blocks") val blocks: List<TextractBlock> = emptyList(),
)

@Serializable
data class TextractBlock(
    @SerialName("BlockType") val blockType: String = "",
    @SerialName("Text") val text: String = "",
    /** Textract güveni yüzde olarak verir (0-100). */
    @SerialName("Confidence") val confidence: Float? = null,
    @SerialName("Geometry") val geometry: TextractGeometry? = null,
)

@Serializable
data class TextractGeometry(
    @SerialName("BoundingBox") val boundingBox: TextractBoundingBox? = null,
)

/** Textran kutu değerleri 0..1 aralığında oranlardır, piksel değil. */
@Serializable
data class TextractBoundingBox(
    @SerialName("Left") val left: Float = 0f,
    @SerialName("Top") val top: Float = 0f,
    @SerialName("Width") val width: Float = 0f,
    @SerialName("Height") val height: Float = 0f,
)
