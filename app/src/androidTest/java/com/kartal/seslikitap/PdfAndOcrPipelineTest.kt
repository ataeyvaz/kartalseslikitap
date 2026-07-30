package com.kartal.seslikitap

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kartal.seslikitap.data.image.PageImageStore
import com.kartal.seslikitap.data.pdf.PdfPageExtractor
import com.kartal.seslikitap.data.provider.mlkit.MlKitOcrProvider
import com.kartal.seslikitap.domain.usecase.CleanOcrTextUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * PDF -> görüntü -> OCR zincirini gerçek cihaz bileşenleriyle uçtan uca çalıştırır.
 *
 * Test PDF'i çalışma anında üretilir: repoda ikili dosya taşımadan, içeriğini bildiğimiz
 * bir sayfayla OCR çıktısını karşılaştırabiliriz.
 */
@RunWith(AndroidJUnit4::class)
class PdfAndOcrPipelineTest {

    private lateinit var context: Context
    private lateinit var imageStore: PageImageStore
    private lateinit var extractor: PdfPageExtractor
    private lateinit var ocrProvider: MlKitOcrProvider

    private val expectedLines = listOf(
        "Kucuk Prens gezegeninden ayrildi",
        "Yildizlar arasinda yolculuga cikti",
        "Sonra bir tilki ile karsilasti",
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        imageStore = PageImageStore(context, Dispatchers.IO)
        extractor = PdfPageExtractor(context, imageStore, Dispatchers.IO)
        ocrProvider = MlKitOcrProvider(Dispatchers.IO)
    }

    @Test
    fun pdf_sayfa_sayisi_okunur() = runTest {
        val uri = createTestPdf(pageCount = 3)
        assertEquals(3, extractor.pageCount(uri))
    }

    @Test
    fun pdf_sayfalari_goruntuye_donusturulur() = runTest {
        val uri = createTestPdf(pageCount = 3)

        val pages = extractor.extractPages(uri, bookId = "test-kitap").toList()

        assertEquals(3, pages.size)
        pages.forEachIndexed { index, page ->
            assertEquals(index, page.index)
            assertEquals(3, page.totalPages)
            assertTrue("Sayfa dosyası oluşmadı", page.file.exists())
            assertTrue("Sayfa dosyası boş", page.file.length() > 0)
        }
    }

    @Test
    fun secili_sayfa_araligi_ice_aktarilir() = runTest {
        val uri = createTestPdf(pageCount = 5)

        val pages = extractor.extractPages(uri, bookId = "test-kitap", pageRange = 1..2).toList()

        assertEquals(2, pages.size)
        assertEquals(listOf(1, 2), pages.map { it.index })
    }

    @Test
    fun pdf_sayfasi_ocr_ile_okunur() = runTest {
        val uri = createTestPdf(pageCount = 1)
        val page = extractor.extractPages(uri, bookId = "test-kitap").toList().single()

        val bitmap = imageStore.loadForOcr(page.file)
        val result = ocrProvider.recognize(bitmap)

        assertTrue("OCR hiç metin bulamadı", result.text.isNotBlank())
        val normalized = result.text.replace("\n", " ")
        expectedLines.forEach { line ->
            val firstWord = line.split(" ").first()
            assertTrue(
                "OCR '$firstWord' kelimesini bulamadı. Çıktı: ${result.text}",
                normalized.contains(firstWord, ignoreCase = true),
            )
        }
    }

    @Test
    fun ocr_ciktisi_temizlemeden_gecer() = runTest {
        val uri = createTestPdf(pageCount = 1)
        val page = extractor.extractPages(uri, bookId = "test-kitap").toList().single()

        val bitmap = imageStore.loadForOcr(page.file)
        val cleaned = CleanOcrTextUseCase()(ocrProvider.recognize(bitmap).text)

        assertTrue(cleaned.isNotBlank())
        assertTrue("Temizlenmiş metinde çift boşluk kalmış", !cleaned.contains("  "))
    }

    @Test
    fun bozuk_dosya_anlasilir_hata_verir() = runTest {
        val broken = File(context.cacheDir, "bozuk.pdf").apply {
            writeText("bu bir PDF değil")
        }

        val error = runCatching { extractor.pageCount(Uri.fromFile(broken)) }.exceptionOrNull()

        assertTrue("Beklenen hata alınmadı: $error", error != null)
    }

    /** İçeriğini bildiğimiz, metin çizilmiş bir PDF üretir. */
    private fun createTestPdf(pageCount: Int): Uri {
        val document = PdfDocument()
        val paint = Paint().apply {
            textSize = 28f
            isAntiAlias = true
        }

        repeat(pageCount) { pageIndex ->
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = document.startPage(pageInfo)
            var y = 120f
            expectedLines.forEach { line ->
                page.canvas.drawText(line, 60f, y, paint)
                y += 60f
            }
            document.finishPage(page)
        }

        val file = File(context.cacheDir, "test-${System.nanoTime()}.pdf")
        file.outputStream().use(document::writeTo)
        document.close()
        return Uri.fromFile(file)
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
    }
}
