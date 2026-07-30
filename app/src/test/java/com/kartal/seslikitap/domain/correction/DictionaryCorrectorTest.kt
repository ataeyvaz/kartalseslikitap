package com.kartal.seslikitap.domain.correction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCorrectorTest {

    /** Küçük ama gerçekçi bir sözlük: kelime -> sıklık. */
    private val lexicon = WordLexicon(
        mapOf(
            "şarkı" to 30_751L,
            "kişi" to 97_455L,
            "çocuk" to 122_690L,
            "güneş" to 23_394L,
            "değil" to 1_249_188L,
            "gözlük" to 1_968L,
            "masa" to 5_000L,
            "kar" to 12_031L,
            "kâr" to 9_000L,
            "bir" to 6_039_421L,
            "gün" to 400_000L,
            "adam" to 200_000L,
            // Sıklığı düşük ama geçerli kelime: düzeltme hedefi olmamalı.
            "sac" to 50L,
        ),
    )

    private val corrector = DictionaryCorrector(lexicon)

    @Test
    fun `kaybolmus turkce isaretler geri konur`() {
        val result = corrector.correct("sarki")
        assertEquals("şarkı", result.text)
        assertEquals(CorrectionReason.DIACRITIC_RESTORED, result.corrections.single().reason)
    }

    @Test
    fun `cumle icinde birden cok kelime duzeltilir`() {
        val result = corrector.correct("gunes cok guzel degil")
        assertTrue(result.text.contains("güneş"))
        assertTrue(result.text.contains("değil"))
    }

    @Test
    fun `sozlukteki kelimeye asla dokunulmaz`() {
        val result = corrector.correct("kar masa bir gün")
        assertEquals("kar masa bir gün", result.text)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `bilinmeyen kelime aday bulunamazsa oldugu gibi kalir`() {
        val result = corrector.correct("zxcvbnm")
        assertEquals("zxcvbnm", result.text)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `noktalama ve bosluk korunur`() {
        val result = corrector.correct("Gunes, cok parlak! Degil mi?")
        assertTrue(result.text.contains(","))
        assertTrue(result.text.contains("!"))
        assertTrue(result.text.endsWith("?"))
    }

    @Test
    fun `cumle basindaki buyuk harf korunur`() {
        val result = corrector.correct("Gunes parlıyor.")
        assertTrue("Çıktı: ${result.text}", result.text.startsWith("Güneş"))
    }

    @Test
    fun `cumle ortasindaki buyuk harfli kelimeye dokunulmaz`() {
        // Özel isim olabilir; sözlükte olmaması normaldir.
        val result = corrector.correct("Bugün Sarki geldi.")
        assertTrue(result.text.contains("Sarki"))
    }

    @Test
    fun `rakam iceren belirtec degistirilmez`() {
        val result = corrector.correct("sarki1 ve 2sarki")
        assertEquals("sarki1 ve 2sarki", result.text)
    }

    @Test
    fun `kesme isaretinden sonraki ek korunur`() {
        val result = corrector.correct("sarki'yi")
        assertEquals("şarkı'yi", result.text)
    }

    @Test
    fun `nadir aday duzeltme hedefi olamaz`() {
        // "sac" sözlükte var (sıklık 50) ama eşiğin altında; "sacc" düzeltilmemeli.
        val result = corrector.correct("sacc")
        assertEquals("sacc", result.text)
    }

    @Test
    fun `iki yakin aday varsa karar verilmez`() {
        // "kar" (12031) ve "kâr" (9000) yarışıyor; hangisinin doğru olduğunu bilemeyiz.
        val ambiguous = DictionaryCorrector(
            WordLexicon(mapOf("kar" to 12_031L, "kâr" to 9_000L)),
        )
        val result = ambiguous.correct("kkar")
        assertEquals("kkar", result.text)
        assertTrue(result.corrections.isEmpty())
    }

    @Test
    fun `bos sozlukle metin degismez`() {
        val result = DictionaryCorrector(WordLexicon.Empty).correct("sarki gunes")
        assertEquals("sarki gunes", result.text)
    }

    @Test
    fun `her degisiklik raporlanir`() {
        val result = corrector.correct("gunes ve gozluk")
        assertEquals(2, result.corrections.size)
        assertTrue(result.corrections.any { it.original == "gunes" && it.corrected == "güneş" })
        assertTrue(result.hasChanges)
    }

    @Test
    fun `satir sonlari korunur`() {
        val result = corrector.correct("gunes\n\nmasa")
        assertEquals("güneş\n\nmasa", result.text)
    }
}
