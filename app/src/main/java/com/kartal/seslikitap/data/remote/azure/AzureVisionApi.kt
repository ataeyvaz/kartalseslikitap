package com.kartal.seslikitap.data.remote.azure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Azure AI Vision — Image Analysis 4.0 "read" özelliği.
 *
 * Adres kullanıcının kaynağına özeldir (`https://<kaynak>.cognitiveservices.azure.com/`),
 * bu yüzden tam URL çağrı sırasında [Url] ile verilir.
 */
interface AzureVisionApi {

    @POST
    suspend fun analyzeRead(
        @Url url: String,
        @Header("Ocp-Apim-Subscription-Key") apiKey: String,
        @Body image: RequestBody,
    ): AzureAnalyzeResponse
}

@Serializable
data class AzureAnalyzeResponse(
    @SerialName("readResult") val readResult: AzureReadResult? = null,
)

@Serializable
data class AzureReadResult(
    val blocks: List<AzureBlock> = emptyList(),
)

@Serializable
data class AzureBlock(
    val lines: List<AzureLine> = emptyList(),
)

@Serializable
data class AzureLine(
    val text: String = "",
    @SerialName("boundingPolygon") val boundingPolygon: List<AzurePoint> = emptyList(),
    val words: List<AzureWord> = emptyList(),
)

@Serializable
data class AzureWord(
    val text: String = "",
    val confidence: Float? = null,
)

@Serializable
data class AzurePoint(val x: Int = 0, val y: Int = 0)
