package com.kartal.seslikitap.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanOcrTextUseCaseTest {

    private val cleanOcrText = CleanOcrTextUseCase()

    @Test
    fun `satir sonu tiresi kelimeyi birlestirir`() {
        val raw = "Küçük prens gezege-\nninden ayrıldı."
        assertEquals("Küçük prens gezegeninden ayrıldı.", cleanOcrText(raw))
    }

    @Test
    fun `cumle ortasinda bolunmus satirlar tek satira birlesir`() {
        val raw = "Bir zamanlar\nuzak bir ülkede\nyaşarmış."
        assertEquals("Bir zamanlar uzak bir ülkede yaşarmış.", cleanOcrText(raw))
    }

    @Test
    fun `bos satir paragraf siniri olarak korunur`() {
        val raw = "Birinci paragraf.\n\nİkinci paragraf."
        assertEquals("Birinci paragraf.\n\nİkinci paragraf.", cleanOcrText(raw))
    }

    @Test
    fun `sayfa numarasi satirlari atilir`() {
        val raw = "Metin burada bitiyor.\n\n- 42 -"
        assertEquals("Metin burada bitiyor.", cleanOcrText(raw))
    }

    @Test
    fun `fazla bosluklar sikistirilir`() {
        assertEquals("İki kelime", cleanOcrText("İki     kelime   "))
    }

    @Test
    fun `bos girdi bos cikti verir`() {
        assertEquals("", cleanOcrText("   \n  \n "))
    }
}
