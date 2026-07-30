package com.kartal.seslikitap.data.provider.elevenlabs

import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevenLabsVoiceMappingTest {

    @Test
    fun `cinsiyet etiketlerden okunur`() {
        assertEquals(NarratorGender.FEMALE, ElevenLabsVoiceMapping.genderOf(mapOf("gender" to "female")))
        assertEquals(NarratorGender.MALE, ElevenLabsVoiceMapping.genderOf(mapOf("gender" to "Male")))
        assertEquals(NarratorGender.NEUTRAL, ElevenLabsVoiceMapping.genderOf(emptyMap()))
    }

    @Test
    fun `anlatim icin etiketlenmis ses one cikar`() {
        val narration = ElevenLabsVoiceMapping.qualityOf(mapOf("use_case" to "narration"), "premade")
        val conversational = ElevenLabsVoiceMapping.qualityOf(mapOf("use_case" to "conversational"), "premade")
        assertTrue(narration > conversational)
    }

    @Test
    fun `profesyonel ses hazir sesin onune gecer`() {
        val professional = ElevenLabsVoiceMapping.qualityOf(emptyMap(), "professional")
        val premade = ElevenLabsVoiceMapping.qualityOf(emptyMap(), "premade")
        assertTrue(professional > premade)
    }

    @Test
    fun `kalite bir uzerine cikmaz`() {
        val maxed = ElevenLabsVoiceMapping.qualityOf(
            mapOf("use_case" to "narration audiobook"),
            "professional",
        )
        assertEquals(1.0f, maxed, 0.0001f)
    }

    @Test
    fun `karakter ve genc sesler cocuk kitabina uygun sayilir`() {
        assertTrue(ElevenLabsVoiceMapping.isChildFriendly(mapOf("use_case" to "characters_animation")))
        assertTrue(ElevenLabsVoiceMapping.isChildFriendly(mapOf("age" to "young")))
        assertFalse(ElevenLabsVoiceMapping.isChildFriendly(mapOf("use_case" to "narration", "age" to "old")))
    }

    @Test
    fun `hiz elevenlabs dar araligina kirpilir`() {
        assertEquals(0.7, ElevenLabsVoiceMapping.clampSpeed(0.3f), 0.001)
        assertEquals(1.2, ElevenLabsVoiceMapping.clampSpeed(2.0f), 0.001)
        assertEquals(1.05, ElevenLabsVoiceMapping.clampSpeed(1.05f), 0.001)
    }

    @Test
    fun `cocuk kitabi modunda tonlama canlanir`() {
        val child = ElevenLabsVoiceMapping.settingsFor(VoiceConfig(isChildrenPreset = true))
        val narration = ElevenLabsVoiceMapping.settingsFor(VoiceConfig(isChildrenPreset = false))

        // Düşük stability + yüksek style = daha canlı, değişken tonlama.
        assertTrue(child.stability < narration.stability)
        assertTrue(child.style > narration.style)
    }

    @Test
    fun `ses ayarlarina kullanicinin hizi tasinir`() {
        val settings = ElevenLabsVoiceMapping.settingsFor(VoiceConfig(speakingRate = 1.1f))
        assertEquals(1.1, settings.speed, 0.001)
        assertTrue(settings.useSpeakerBoost)
    }
}
