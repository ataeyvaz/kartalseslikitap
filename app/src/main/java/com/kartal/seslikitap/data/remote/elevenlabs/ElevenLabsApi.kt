package com.kartal.seslikitap.data.remote.elevenlabs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * ElevenLabs REST arayüzü (BYOK).
 *
 * Google'ın aksine anahtar sorgu parametresinde değil `xi-api-key` başlığında taşınır;
 * ses çıktısı da JSON değil doğrudan MP3 gövdesidir.
 */
interface ElevenLabsApi {

    @GET("v1/voices")
    suspend fun listVoices(@Header("xi-api-key") apiKey: String): ElevenLabsVoicesResponse

    @Streaming
    @POST("v1/text-to-speech/{voiceId}")
    suspend fun synthesize(
        @Header("xi-api-key") apiKey: String,
        @Path("voiceId") voiceId: String,
        @Query("output_format") outputFormat: String,
        @Body request: ElevenLabsSynthesizeRequest,
    ): ResponseBody
}

@Serializable
data class ElevenLabsVoicesResponse(val voices: List<ElevenLabsVoice> = emptyList())

@Serializable
data class ElevenLabsVoice(
    @SerialName("voice_id") val voiceId: String = "",
    val name: String = "",
    /** Serbest biçimli etiketler: gender, age, accent, use_case, description... */
    val labels: Map<String, String> = emptyMap(),
    val category: String = "",
)

@Serializable
data class ElevenLabsSynthesizeRequest(
    val text: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("voice_settings") val voiceSettings: ElevenLabsVoiceSettings,
)

@Serializable
data class ElevenLabsVoiceSettings(
    val stability: Double,
    @SerialName("similarity_boost") val similarityBoost: Double,
    val style: Double,
    @SerialName("use_speaker_boost") val useSpeakerBoost: Boolean,
    /** ElevenLabs dar bir hız aralığı kabul eder (0.7..1.2). */
    val speed: Double,
)
