package com.kartal.seslikitap.data.remote.googlecloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** Google Cloud Text-to-Speech REST arayüzü (Neural2/Studio/WaveNet sesleri dahil). */
interface GoogleCloudTtsApi {

    @GET("v1/voices")
    suspend fun listVoices(
        @Query("key") apiKey: String,
        @Query("languageCode") languageCode: String? = null,
    ): TtsVoicesResponse

    @POST("v1/text:synthesize")
    suspend fun synthesize(
        @Query("key") apiKey: String,
        @Body request: TtsSynthesizeRequest,
    ): TtsSynthesizeResponse
}

@Serializable
data class TtsVoicesResponse(val voices: List<TtsVoice> = emptyList())

@Serializable
data class TtsVoice(
    val name: String = "",
    @SerialName("ssmlGender") val ssmlGender: String = "SSML_VOICE_GENDER_UNSPECIFIED",
    @SerialName("languageCodes") val languageCodes: List<String> = emptyList(),
    @SerialName("naturalSampleRateHertz") val naturalSampleRateHertz: Int = 0,
)

@Serializable
data class TtsSynthesizeRequest(
    val input: TtsInput,
    val voice: TtsVoiceSelection,
    @SerialName("audioConfig") val audioConfig: TtsAudioConfig,
)

@Serializable
data class TtsInput(
    val text: String? = null,
    val ssml: String? = null,
)

@Serializable
data class TtsVoiceSelection(
    @SerialName("languageCode") val languageCode: String,
    val name: String? = null,
    @SerialName("ssmlGender") val ssmlGender: String? = null,
)

@Serializable
data class TtsAudioConfig(
    @SerialName("audioEncoding") val audioEncoding: String = "MP3",
    @SerialName("speakingRate") val speakingRate: Double,
    /** Google'da pitch yarım ton cinsindendir (-20.0 .. 20.0), çarpan değil. */
    val pitch: Double,
)

@Serializable
data class TtsSynthesizeResponse(
    @SerialName("audioContent") val audioContent: String = "",
)
