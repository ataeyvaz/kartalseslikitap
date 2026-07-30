package com.kartal.seslikitap.data.provider.azure

import android.graphics.Bitmap
import com.kartal.seslikitap.data.remote.azure.AzureVisionApi
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.OcrResult
import com.kartal.seslikitap.domain.provider.OcrProvider
import com.kartal.seslikitap.domain.provider.OcrProviderException
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.security.ApiKeyStore
import com.kartal.seslikitap.domain.security.CredentialField
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Azure AI Vision (Read) tabanlı OCR sağlayıcısı (BYOK).
 *
 * Çoklu dil desteği güçlüdür; Google'a alternatif olarak konumlanır. Azure kaynağı
 * bölgeye özel bir adres verdiği için anahtarın yanında adres de kullanıcıdan alınır.
 */
@Singleton
class AzureVisionOcrProvider @Inject constructor(
    private val api: AzureVisionApi,
    private val apiKeyStore: ApiKeyStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OcrProvider {

    override val id: ProviderId = ProviderIds.AzureVision
    override val name: String = "Azure AI Vision (bulut)"
    override val requiresApiKey: Boolean = true
    override val isOnDevice: Boolean = false

    override val credentialFields: List<CredentialField> = listOf(
        CredentialField.ApiKey,
        CredentialField(
            id = FIELD_ENDPOINT,
            label = "Azure kaynak adresi",
            isSecret = false,
            hint = "https://<kaynagin-adi>.cognitiveservices.azure.com",
        ),
    )

    override suspend fun isAvailable(): Boolean =
        !apiKeyStore.getKey(id).isNullOrBlank() &&
            !apiKeyStore.getCredential(id, FIELD_ENDPOINT).isNullOrBlank()

    override suspend fun recognize(image: Bitmap): OcrResult = withContext(ioDispatcher) {
        val apiKey = apiKeyStore.getKey(id)
            ?: throw OcrProviderException(id, "Azure API anahtarı girilmemiş")
        val endpoint = apiKeyStore.getCredential(id, FIELD_ENDPOINT)
            ?: throw OcrProviderException(id, "Azure kaynak adresi girilmemiş")

        val body = image.toJpegBytes().toRequestBody(OCTET_STREAM.toMediaType())

        val response = try {
            api.analyzeRead(AzureReadMapper.buildAnalyzeUrl(endpoint), apiKey, body)
        } catch (e: Exception) {
            throw OcrProviderException(id, "Azure Vision isteği başarısız: ${e.message}", e)
        }

        AzureReadMapper.toOcrResult(response, id)
    }

    private fun Bitmap.toJpegBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return stream.toByteArray()
    }

    private companion object {
        const val FIELD_ENDPOINT = "endpoint"
        const val OCTET_STREAM = "application/octet-stream"
        const val JPEG_QUALITY = 90
    }
}
