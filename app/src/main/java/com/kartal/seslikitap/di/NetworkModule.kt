package com.kartal.seslikitap.di

import com.kartal.seslikitap.data.remote.aws.TextractApi
import com.kartal.seslikitap.data.remote.azure.AzureVisionApi
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsApi
import com.kartal.seslikitap.data.remote.googlecloud.GoogleCloudTtsApi
import com.kartal.seslikitap.data.remote.googlecloud.GoogleCloudVisionApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // Sayfa görüntüsü yükleme ve uzun metin sentezi zaman alabiliyor.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideVisionApi(client: OkHttpClient, json: Json): GoogleCloudVisionApi =
        Retrofit.Builder()
            .baseUrl(VISION_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(GoogleCloudVisionApi::class.java)

    /**
     * Azure ve AWS adresleri kullanıcının kaynağına/bölgesine göre değiştiği için tam URL
     * çağrı sırasında `@Url` ile verilir; buradaki temel adres yalnızca Retrofit'in
     * zorunlu kıldığı bir yer tutucudur.
     */
    @Provides
    @Singleton
    fun provideAzureVisionApi(client: OkHttpClient, json: Json): AzureVisionApi =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(AzureVisionApi::class.java)

    @Provides
    @Singleton
    fun provideTextractApi(client: OkHttpClient, json: Json): TextractApi =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(TextractApi::class.java)

    @Provides
    @Singleton
    fun provideElevenLabsApi(client: OkHttpClient, json: Json): ElevenLabsApi =
        Retrofit.Builder()
            .baseUrl(ELEVENLABS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(ElevenLabsApi::class.java)

    @Provides
    @Singleton
    fun provideTtsApi(client: OkHttpClient, json: Json): GoogleCloudTtsApi =
        Retrofit.Builder()
            .baseUrl(TTS_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()
            .create(GoogleCloudTtsApi::class.java)

    private const val VISION_BASE_URL = "https://vision.googleapis.com/"
    private const val TTS_BASE_URL = "https://texttospeech.googleapis.com/"
    private const val ELEVENLABS_BASE_URL = "https://api.elevenlabs.io/"

    /** Yalnızca `@Url` ile çalışan servisler için yer tutucu; gerçek adres istekte verilir. */
    private const val PLACEHOLDER_BASE_URL = "https://kartal.invalid/"
    private const val JSON_MEDIA_TYPE = "application/json"
}
