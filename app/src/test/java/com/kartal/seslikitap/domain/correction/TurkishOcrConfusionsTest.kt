package com.kartal.seslikitap.domain.correction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishOcrConfusionsTest {

    @Test
    fun `isaret adaylari uretilir`() {
        val candidates = TurkishOcrConfusions.candidatesFor("sarki").map { it.text }
        assertTrue(candidates.contains("şarkı"))
        assertTrue(candidates.contains("sarkı"))
        assertTrue(candidates.contains("şarki"))
    }

    @Test
    fun `kelimenin kendisi aday degildir`() {
        assertFalse(TurkishOcrConfusions.candidatesFor("masa").any { it.text == "masa" })
    }

    @Test
    fun `sekil karismasi adaylari uretilir`() {
        val candidates = TurkishOcrConfusions.candidatesFor("rnasa").map { it.text }
        assertTrue(candidates.contains("masa"))
    }

    @Test
    fun `cok fazla belirsiz konum varsa isaret adaylari uretilmez`() {
        // 9 belirsiz konum: 2^9 aday üretmek hem yavaş hem riskli.
        val many = TurkishOcrConfusions.candidatesFor("iiiiiiiii")
        assertTrue(many.none { it.reason == CorrectionReason.DIACRITIC_RESTORED })
    }

    @Test
    fun `uzun turkce kelimede aday sayisi sinirli kalir`() {
        // 3 seçenekli i ve u grupları yüzünden bu kelime sınırsız bırakılsa binlerce
        // aday üretirdi; gerçek metinde bu tür kelimeler sıradan.
        val candidates = TurkishOcrConfusions.candidatesFor("uygulamalarindan")
        assertTrue("Aday sayısı patladı: ${candidates.size}", candidates.size <= 300)
    }

    @Test
    fun `cok kisa kelime icin aday uretilmez`() {
        assertTrue(TurkishOcrConfusions.candidatesFor("a").isEmpty())
    }

    @Test
    fun `buyuk harf duzeni korunur`() {
        assertEquals("Güneş", TurkishOcrConfusions.applyCasing("Gunes", "güneş"))
        assertEquals("GÜNEŞ", TurkishOcrConfusions.applyCasing("GUNES", "güneş"))
        assertEquals("güneş", TurkishOcrConfusions.applyCasing("gunes", "güneş"))
    }

    @Test
    fun `turkce kucuk harf kurallari uygulanir`() {
        // Türkçede I -> ı, İ -> i olur; varsayılan yerel ayarla bu yanlış çalışır.
        assertEquals("ışık", TurkishOcrConfusions.lowercase("IŞIK"))
        assertEquals("iyi", TurkishOcrConfusions.lowercase("İYİ"))
    }
}
