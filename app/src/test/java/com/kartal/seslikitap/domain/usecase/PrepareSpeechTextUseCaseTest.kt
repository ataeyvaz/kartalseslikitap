package com.kartal.seslikitap.domain.usecase

import com.kartal.seslikitap.domain.model.AudioStream
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderVoice
import com.kartal.seslikitap.domain.provider.TtsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepareSpeechTextUseCaseTest {

    private val prepare = PrepareSpeechTextUseCase()

    @Test
    fun `ssml desteklemeyen saglayiciya etiket gonderilmez`() {
        val output = prepare("Merhaba. Nasılsın?", FakeTtsProvider(supportsSsml = false))
        assertFalse(output.contains("<"))
        assertEquals("Merhaba. Nasılsın?", output)
    }

    @Test
    fun `ssml destekleyen saglayiciya cumle duraklamalari eklenir`() {
        val output = prepare("Merhaba. Nasılsın?", FakeTtsProvider(supportsSsml = true))
        assertTrue(output.startsWith("<speak>"))
        assertTrue(output.endsWith("</speak>"))
        assertEquals(2, Regex("<break").findAll(output).count())
    }

    @Test
    fun `ssml ciktisinda metindeki isaretler kacirilir`() {
        val output = prepare("Ali & Veli <kitap>", FakeTtsProvider(supportsSsml = true))
        assertTrue(output.contains("Ali &amp; Veli &lt;kitap&gt;"))
    }

    @Test
    fun `paragraflar ssml de ayri p etiketi olur`() {
        val output = prepare("Birinci.\n\nİkinci.", FakeTtsProvider(supportsSsml = true))
        assertEquals(2, Regex("<p>").findAll(output).count())
    }

    private class FakeTtsProvider(override val supportsSsml: Boolean) : TtsProvider {
        override val id = ProviderId("fake")
        override val name = "Fake"
        override val requiresApiKey = false
        override val isOnDevice = true
        override suspend fun availableVoices(): List<ProviderVoice> = emptyList()
        override suspend fun synthesize(text: String, voice: VoiceConfig): AudioStream =
            AudioStream.InMemory(ByteArray(0))
    }
}
