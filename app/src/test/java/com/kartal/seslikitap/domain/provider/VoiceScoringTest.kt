package com.kartal.seslikitap.domain.provider

import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.VoiceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceScoringTest {

    @Test
    fun `istenen cinsiyet ses kalitesinin onunde gelir`() {
        val selected = VoiceScoring.select(
            listOf(
                voice("erkek-mukemmel", NarratorGender.MALE, quality = 1.0f),
                voice("kadin-vasat", NarratorGender.FEMALE, quality = 0.4f),
            ),
            VoiceConfig(gender = NarratorGender.FEMALE),
        )
        assertEquals("kadin-vasat", selected?.id)
    }

    @Test
    fun `notr istekte en kaliteli ses secilir`() {
        val selected = VoiceScoring.select(
            listOf(
                voice("kadin-vasat", NarratorGender.FEMALE, quality = 0.4f),
                voice("erkek-mukemmel", NarratorGender.MALE, quality = 1.0f),
            ),
            VoiceConfig(gender = NarratorGender.NEUTRAL),
        )
        assertEquals("erkek-mukemmel", selected?.id)
    }

    @Test
    fun `cocuk kitabinda esit kalitede uygun etiketli ses kazanir`() {
        val selected = VoiceScoring.select(
            listOf(
                voice("duz", NarratorGender.FEMALE, quality = 0.8f, childFriendly = false),
                voice("canli", NarratorGender.FEMALE, quality = 0.8f, childFriendly = true),
            ),
            VoiceConfig(gender = NarratorGender.FEMALE, isChildrenPreset = true),
        )
        assertEquals("canli", selected?.id)
    }

    @Test
    fun `cocuk etiketi buyuk kalite farkini kapatmaz`() {
        val selected = VoiceScoring.select(
            listOf(
                voice("cok-iyi", NarratorGender.FEMALE, quality = 1.0f, childFriendly = false),
                voice("zayif-ama-canli", NarratorGender.FEMALE, quality = 0.3f, childFriendly = true),
            ),
            VoiceConfig(gender = NarratorGender.FEMALE, isChildrenPreset = true),
        )
        assertEquals("cok-iyi", selected?.id)
    }

    @Test
    fun `aday yoksa null doner`() {
        assertNull(VoiceScoring.select(emptyList(), VoiceConfig()))
    }

    private fun voice(
        id: String,
        gender: NarratorGender,
        quality: Float,
        childFriendly: Boolean = false,
    ) = ProviderVoice(
        id = id,
        displayName = id,
        gender = gender,
        languageTag = "tr-TR",
        isChildFriendly = childFriendly,
        requiresNetwork = true,
        quality = quality,
    )
}
