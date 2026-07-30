package com.kartal.seslikitap.data.correction

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kartal.seslikitap.domain.correction.DictionaryCorrector
import com.kartal.seslikitap.domain.correction.WordLexicon
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Düzelticiyi **gerçek 110 bin kelimelik sözlükle** çalıştırır.
 *
 * Birim testleri sahte bir sözlükle mantığı doğruluyor; burada asıl merak edilen şey
 * gerçek verinin davranışı: işaretler geri geliyor mu, doğru kelimeler bozuluyor mu.
 */
@RunWith(AndroidJUnit4::class)
class RealLexiconCorrectionTest {

    private lateinit var lexicon: WordLexicon
    private lateinit var corrector: DictionaryCorrector

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lexicon = context.assets.open("dictionaries/tr_lexicon.tsv")
            .bufferedReader()
            .useLines(WordLexicon::fromLines)
        corrector = DictionaryCorrector(lexicon)
    }

    @Test
    fun sozluk_beklenen_buyuklukte_yuklenir() {
        assertTrue("Sözlük çok küçük: ${lexicon.size}", lexicon.size > 100_000)
        assertTrue(lexicon.contains("şarkı"))
        assertTrue(lexicon.contains("çocuk"))
        assertTrue(lexicon.contains("değil"))
    }

    @Test
    fun ascii_bicimler_sozlukten_temizlenmis() {
        // Bunlar sözlükte kalsaydı işaret geri yükleme hiç çalışmazdı.
        assertTrue("'cocuk' hâlâ sözlükte", !lexicon.contains("cocuk"))
        assertTrue("'degil' hâlâ sözlükte", !lexicon.contains("degil"))
        assertTrue("'sarki' hâlâ sözlükte", !lexicon.contains("sarki"))
    }

    @Test
    fun gercek_cumlede_isaretler_geri_gelir() {
        val result = corrector.correct("Cocuk sarki soyluyordu ama gunes cok parlakti.")

        assertTrue("Çıktı: ${result.text}", result.text.contains("şarkı"))
        assertTrue("Çıktı: ${result.text}", result.text.contains("güneş"))
        assertTrue(result.corrections.isNotEmpty())
    }

    @Test
    fun dogru_yazilmis_metin_bozulmaz() {
        val original = "Küçük Prens gezegeninden ayrıldı ve yıldızlar arasında yolculuğa çıktı."
        val result = corrector.correct(original)

        assertEquals("Doğru metin değiştirildi: ${result.corrections}", original, result.text)
    }

    @Test
    fun ozel_isimlere_dokunulmaz() {
        val original = "Ankara'da Mehmet ile Ayse bulustu."
        val result = corrector.correct(original)

        assertTrue("Özel isim değişti: ${result.text}", result.text.contains("Ankara"))
        assertTrue("Özel isim değişti: ${result.text}", result.text.contains("Mehmet"))
    }

    @Test
    fun uzun_metinde_makul_surede_calisir() {
        val page = buildString {
            repeat(60) { append("Cocuk sarki soyluyordu ama gunes cok parlakti. ") }
        }

        val start = System.currentTimeMillis()
        val result = corrector.correct(page)
        val elapsed = System.currentTimeMillis() - start

        assertTrue("Bir sayfa için çok yavaş: ${elapsed}ms", elapsed < 2_000)
        assertTrue(result.corrections.isNotEmpty())
    }
}
