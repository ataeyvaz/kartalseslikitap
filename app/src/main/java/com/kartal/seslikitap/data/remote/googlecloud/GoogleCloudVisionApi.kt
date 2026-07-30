package com.kartal.seslikitap.data.remote.googlecloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Cloud Vision REST arayüzü (BYOK: istek, kullanıcının kendi API anahtarıyla
 * doğrudan Google'a gider — araya bizim sunucumuz girmez).
 */
interface GoogleCloudVisionApi {

    @POST("v1/images:annotate")
    suspend fun annotate(
        @Query("key") apiKey: String,
        @Body request: VisionAnnotateRequest,
    ): VisionAnnotateResponse
}

@Serializable
data class VisionAnnotateRequest(val requests: List<VisionRequest>)

@Serializable
data class VisionRequest(
    val image: VisionImage,
    val features: List<VisionFeature>,
    @SerialName("imageContext") val imageContext: VisionImageContext? = null,
)

@Serializable
data class VisionImage(val content: String)

@Serializable
data class VisionFeature(val type: String)

@Serializable
data class VisionImageContext(@SerialName("languageHints") val languageHints: List<String>)

@Serializable
data class VisionAnnotateResponse(val responses: List<VisionResponse> = emptyList())

@Serializable
data class VisionResponse(
    @SerialName("fullTextAnnotation") val fullTextAnnotation: VisionFullTextAnnotation? = null,
    val error: VisionError? = null,
)

@Serializable
data class VisionError(val code: Int = 0, val message: String = "")

@Serializable
data class VisionFullTextAnnotation(
    val text: String = "",
    val pages: List<VisionPage> = emptyList(),
)

@Serializable
data class VisionPage(
    val blocks: List<VisionBlock> = emptyList(),
    val property: VisionTextProperty? = null,
)

@Serializable
data class VisionBlock(
    val confidence: Float? = null,
    val paragraphs: List<VisionParagraph> = emptyList(),
    @SerialName("boundingBox") val boundingBox: VisionBoundingPoly? = null,
)

@Serializable
data class VisionParagraph(
    val confidence: Float? = null,
    val words: List<VisionWord> = emptyList(),
)

@Serializable
data class VisionWord(
    val confidence: Float? = null,
    val symbols: List<VisionSymbol> = emptyList(),
)

@Serializable
data class VisionSymbol(val text: String = "")

@Serializable
data class VisionBoundingPoly(val vertices: List<VisionVertex> = emptyList())

@Serializable
data class VisionVertex(val x: Int = 0, val y: Int = 0)

@Serializable
data class VisionTextProperty(
    @SerialName("detectedLanguages") val detectedLanguages: List<VisionDetectedLanguage> = emptyList(),
)

@Serializable
data class VisionDetectedLanguage(
    @SerialName("languageCode") val languageCode: String = "",
    val confidence: Float? = null,
)
