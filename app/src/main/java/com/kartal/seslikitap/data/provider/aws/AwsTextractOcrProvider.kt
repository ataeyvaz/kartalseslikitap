package com.kartal.seslikitap.data.provider.aws

import android.graphics.Bitmap
import android.util.Base64
import com.kartal.seslikitap.data.remote.aws.AwsSigV4Signer
import com.kartal.seslikitap.data.remote.aws.TextractApi
import com.kartal.seslikitap.data.remote.aws.TextractDocument
import com.kartal.seslikitap.data.remote.aws.TextractRequest
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
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AWS Textract tabanlı OCR sağlayıcısı (BYOK) — karmaşık sayfa düzenlerinde güçlüdür.
 *
 * Diğer sağlayıcılardan farkı, her isteğin SigV4 ile imzalanmasıdır; bu yüzden
 * kullanıcıdan erişim anahtarı, gizli anahtar ve bölge alınır.
 */
@Singleton
class AwsTextractOcrProvider @Inject constructor(
    private val api: TextractApi,
    private val apiKeyStore: ApiKeyStore,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OcrProvider {

    override val id: ProviderId = ProviderIds.AwsTextract
    override val name: String = "AWS Textract (bulut)"
    override val requiresApiKey: Boolean = true
    override val isOnDevice: Boolean = false

    override val credentialFields: List<CredentialField> = listOf(
        CredentialField(id = FIELD_ACCESS_KEY, label = "AWS erişim anahtarı (Access Key ID)"),
        CredentialField(id = FIELD_SECRET_KEY, label = "AWS gizli anahtarı (Secret Access Key)"),
        CredentialField(
            id = FIELD_REGION,
            label = "Bölge",
            isSecret = false,
            hint = "eu-central-1",
        ),
    )

    override suspend fun isAvailable(): Boolean =
        credentialFields.all { !apiKeyStore.getCredential(id, it.id).isNullOrBlank() }

    override suspend fun recognize(image: Bitmap): OcrResult = withContext(ioDispatcher) {
        val credentials = AwsSigV4Signer.Credentials(
            accessKeyId = requireCredential(FIELD_ACCESS_KEY, "AWS erişim anahtarı"),
            secretAccessKey = requireCredential(FIELD_SECRET_KEY, "AWS gizli anahtarı"),
            region = requireCredential(FIELD_REGION, "AWS bölgesi"),
        )

        val request = TextractRequest(
            document = TextractDocument(bytes = image.toBase64Jpeg()),
        )
        // İmza gövdenin hash'ini içerir; bu yüzden imzalanan bayt dizisi Retrofit'in
        // göndereceğiyle birebir aynı olmalı. Aynı [Json] örneği kullanıldığı için öyle.
        val payload = json.encodeToString(TextractRequest.serializer(), request)
            .toByteArray(Charsets.UTF_8)

        val signed = AwsSigV4Signer.sign(
            credentials = credentials,
            service = SERVICE,
            host = TextractMapper.host(credentials.region),
            httpMethod = "POST",
            canonicalUri = "/",
            canonicalQueryString = "",
            headers = mapOf(
                "content-type" to CONTENT_TYPE,
                "x-amz-target" to TARGET_DETECT_DOCUMENT_TEXT,
            ),
            payload = payload,
        )

        val response = try {
            api.detectDocumentText(
                url = TextractMapper.buildEndpointUrl(credentials.region),
                headers = signed.headers,
                request = request,
            )
        } catch (e: Exception) {
            throw OcrProviderException(id, "AWS Textract isteği başarısız: ${e.message}", e)
        }

        TextractMapper.toOcrResult(response, id, image.width, image.height)
    }

    private suspend fun requireCredential(fieldId: String, label: String): String =
        apiKeyStore.getCredential(id, fieldId)?.takeIf { it.isNotBlank() }
            ?: throw OcrProviderException(id, "$label girilmemiş")

    private fun Bitmap.toBase64Jpeg(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        const val FIELD_ACCESS_KEY = "aws_access_key_id"
        const val FIELD_SECRET_KEY = "aws_secret_access_key"
        const val FIELD_REGION = "aws_region"
        const val SERVICE = "textract"
        const val CONTENT_TYPE = "application/x-amz-json-1.1"
        const val TARGET_DETECT_DOCUMENT_TEXT = "Textract.DetectDocumentText"
        const val JPEG_QUALITY = 90
    }
}
