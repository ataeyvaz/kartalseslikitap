package com.kartal.seslikitap.data.provider.googlecloud

import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import com.kartal.seslikitap.domain.provider.ProviderVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleVoiceMappingTest {

    @Test
    fun `dogal ses aileleri standart sesin onune gecer`() {
        assertTrue(
            GoogleVoiceMapping.qualityOf("tr-TR-Studio-A") >
                GoogleVoiceMapping.qualityOf("tr-TR-Neural2-A"),
        )
        assertTrue(
            GoogleVoiceMapping.qualityOf("tr-TR-Wavenet-A") >
                GoogleVoiceMapping.qualityOf("tr-TR-Standard-A"),
        )
    }

    @Test
    fun `pitch carpani yarim tona cevrilir`() {
        assertEquals(0.0, GoogleVoiceMapping.pitchToSemitones(1.0f), 0.001)
        // Çocuk ön ayarı (1.08) yaklaşık +1 yarım ton olmalı.
        assertEquals(0.96, GoogleVoiceMapping.pitchToSemitones(1.08f), 0.01)
    }

    @Test
    fun `pitch google sinirlarinin disina tasmaz`() {
        assertEquals(20.0, GoogleVoiceMapping.pitchToSemitones(5.0f), 0.001)
        assertEquals(-20.0, GoogleVoiceMapping.pitchToSemitones(-5.0f), 0.001)
    }

    @Test
    fun `konusma hizi google araligina kirpilir`() {
        assertEquals(0.25, GoogleVoiceMapping.clampSpeakingRate(0.05f), 0.001)
        assertEquals(4.0, GoogleVoiceMapping.clampSpeakingRate(9.0f), 0.001)
        assertEquals(1.15, GoogleVoiceMapping.clampSpeakingRate(1.15f), 0.001)
    }

    @Test
    fun `istenen cinsiyet dogallikin onunde gelir`() {
        val candidates = listOf(
            voice("tr-TR-Studio-B", NarratorGender.MALE),
            voice("tr-TR-Standard-A", NarratorGender.FEMALE),
        )

        val selected = GoogleVoiceMapping.selectVoice(
            candidates,
            VoiceConfig(gender = NarratorGender.FEMALE),
        )

        assertEquals("tr-TR-Standard-A", selected?.id)
    }

    @Test
    fun `notr istekte en dogal ses secilir`() {
        val candidates = listOf(
            voice("tr-TR-Standard-A", NarratorGender.FEMALE),
            voice("tr-TR-Neural2-C", NarratorGender.MALE),
        )

        val selected = GoogleVoiceMapping.selectVoice(
            candidates,
            VoiceConfig(gender = NarratorGender.NEUTRAL),
        )

        assertEquals("tr-TR-Neural2-C", selected?.id)
    }

    @Test
    fun `cocuk kitabi modunda uygun etiketli ses one cikar`() {
        val childFriendly = voice("tr-TR-Neural2-A", NarratorGender.FEMALE)
        val plain = voice("tr-TR-Wavenet-A", NarratorGender.FEMALE)

        val selected = GoogleVoiceMapping.selectVoice(
            listOf(plain, childFriendly),
            VoiceConfig(gender = NarratorGender.FEMALE, isChildrenPreset = true),
        )

        assertEquals("tr-TR-Neural2-A", selected?.id)
    }

    @Test
    fun `ssml cinsiyet etiketleri google formatina cevrilir`() {
        assertEquals("FEMALE", GoogleVoiceMapping.toSsmlGender(NarratorGender.FEMALE))
        assertEquals("MALE", GoogleVoiceMapping.toSsmlGender(NarratorGender.MALE))
        assertEquals("NEUTRAL", GoogleVoiceMapping.toSsmlGender(NarratorGender.NEUTRAL))
        assertEquals(NarratorGender.FEMALE, GoogleVoiceMapping.genderOf("FEMALE"))
        assertEquals(NarratorGender.NEUTRAL, GoogleVoiceMapping.genderOf("SSML_VOICE_GENDER_UNSPECIFIED"))
    }

    private fun voice(name: String, gender: NarratorGender) = ProviderVoice(
        id = name,
        displayName = name,
        gender = gender,
        languageTag = "tr-TR",
        isChildFriendly = GoogleVoiceMapping.isChildFriendly(name),
        requiresNetwork = true,
        quality = GoogleVoiceMapping.qualityOf(name),
    )
}
