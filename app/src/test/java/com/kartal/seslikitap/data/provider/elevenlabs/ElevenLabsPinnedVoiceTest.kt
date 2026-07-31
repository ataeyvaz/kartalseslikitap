package com.kartal.seslikitap.data.provider.elevenlabs

import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsApi
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsSynthesizeRequest
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsVoice
import com.kartal.seslikitap.data.remote.elevenlabs.ElevenLabsVoicesResponse
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import com.kartal.seslikitap.domain.repository.PinnedVoice
import com.kartal.seslikitap.domain.repository.VoicePreferenceRepository
import com.kartal.seslikitap.domain.security.ApiKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sabitlenmiş sesin otomatik seçimi ezdiğini doğrular.
 *
 * Kullanıcı kendi klonladığı sesi seçtiğinde, o ses cinsiyet etiketi taşımadığı için
 * skorlamada kaybedebilir; sabitleme tam olarak bu durumu çözmek için var.
 */
class ElevenLabsPinnedVoiceTest {

    private val clonedVoice = ElevenLabsVoice(
        voiceId = "klon-123",
        name = "Kendi Sesim",
        labels = emptyMap(), // klonlanmış seslerde cinsiyet etiketi genelde yok
        category = "cloned",
    )
    private val premadeFemale = ElevenLabsVoice(
        voiceId = "hazir-kadin",
        name = "Hazır Kadın",
        labels = mapOf("gender" to "female", "use_case" to "narration"),
        category = "professional",
    )

    @Test
    fun `sabitlenen ses otomatik secimi ezer`() = runTest {
        val resolver = resolver(pinned = PinnedVoice("klon-123", "Kendi Sesim"))

        val selected = resolver.resolveVoice(VoiceConfig(gender = NarratorGender.FEMALE))

        assertEquals("klon-123", selected?.id)
    }

    @Test
    fun `sabitleme yoksa skorlama calisir`() = runTest {
        val resolver = resolver(pinned = null)

        val selected = resolver.resolveVoice(VoiceConfig(gender = NarratorGender.FEMALE))

        assertEquals("hazir-kadin", selected?.id)
    }

    @Test
    fun `sabitlenen ses listede yoksa skorlamaya dusulur`() = runTest {
        val resolver = resolver(pinned = PinnedVoice("silinmis-ses", "Silinmiş"))

        val selected = resolver.resolveVoice(VoiceConfig(gender = NarratorGender.FEMALE))

        assertEquals("hazir-kadin", selected?.id)
    }

    @Test
    fun `ses listesi alinamazsa sabitlenen kimlikle devam edilir`() = runTest {
        val resolver = resolver(
            pinned = PinnedVoice("klon-123", "Kendi Sesim"),
            voices = emptyList(),
        )

        val selected = resolver.resolveVoice(VoiceConfig())

        assertEquals("klon-123", selected?.id)
        assertEquals("Kendi Sesim", selected?.displayName)
    }

    private fun resolver(
        pinned: PinnedVoice?,
        voices: List<ElevenLabsVoice> = listOf(clonedVoice, premadeFemale),
    ) = ElevenLabsVoiceMappingResolver(
        api = FakeApi(voices),
        apiKeyStore = FakeApiKeyStore(),
        voicePreferenceRepository = FakePreferences(pinned),
    )

    private class FakeApi(private val voices: List<ElevenLabsVoice>) : ElevenLabsApi {
        override suspend fun listVoices(apiKey: String) = ElevenLabsVoicesResponse(voices)

        override suspend fun synthesize(
            apiKey: String,
            voiceId: String,
            outputFormat: String,
            request: ElevenLabsSynthesizeRequest,
        ): ResponseBody = throw UnsupportedOperationException("test kapsamı dışında")
    }

    private class FakeApiKeyStore : ApiKeyStore {
        override suspend fun getCredential(providerId: ProviderId, fieldId: String) = "test-anahtar"
        override suspend fun setCredential(providerId: ProviderId, fieldId: String, value: String) = Unit
        override suspend fun clearCredentials(providerId: ProviderId) = Unit
        override fun observeStoredFields(): Flow<Map<ProviderId, Set<String>>> = flowOf(emptyMap())
    }

    private class FakePreferences(private val pinned: PinnedVoice?) : VoicePreferenceRepository {
        override fun observeAll(): Flow<Map<ProviderId, PinnedVoice>> =
            flowOf(pinned?.let { mapOf(ProviderIds.ElevenLabs to it) } ?: emptyMap())

        override suspend fun getPinnedVoice(providerId: ProviderId) = pinned
        override suspend fun pinVoice(providerId: ProviderId, voiceId: String, displayName: String) = Unit
        override suspend fun clearPinnedVoice(providerId: ProviderId) = Unit
    }
}
