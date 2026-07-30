package com.kartal.seslikitap.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kartal.seslikitap.data.image.PageImageStore
import com.kartal.seslikitap.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF sayfalarını OCR'a verilebilir görüntülere dönüştürür.
 *
 * Android'in yerleşik [PdfRenderer]'ı kullanılır: ek bağımlılık yok, tamamen cihaz üzerinde.
 * Sayfalar tek tek üretilir ([Flow]) — 300 sayfalık bir kitabı belleğe topluca almak
 * uygulamayı düşürür.
 *
 * Not: [PdfRenderer] metin katmanını okumaz, sayfayı **çizer**. Yani metin katmanı olan
 * dijital PDF'lerde bile OCR'dan geçeriz. Bunun avantajı taranmış PDF'lerin de çalışması,
 * dezavantajı dijital PDF'de gereksiz doğruluk kaybı — metin katmanı çıkarımı ayrı bir
 * iyileştirme olarak sonra eklenebilir.
 */
@Singleton
class PdfPageExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val imageStore: PageImageStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** PDF'in sayfa sayısı; içe aktarmadan önce kullanıcıya göstermek için. */
    suspend fun pageCount(uri: Uri): Int = withContext(ioDispatcher) {
        val (descriptor, renderer) = openRendererBlocking(uri)
        try {
            renderer.pageCount
        } finally {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }

    /**
     * Her sayfayı JPEG olarak kitabın dizinine yazar ve dosyayı yayınlar.
     *
     * @param bookId hedef kitap
     * @param pageRange içe aktarılacak sayfalar (0 tabanlı); null ise tamamı
     */
    fun extractPages(
        uri: Uri,
        bookId: String,
        pageRange: IntRange? = null,
    ): Flow<ExtractedPage> = flow {
        val (descriptor, renderer) = openRendererBlocking(uri)
        try {
            val total = renderer.pageCount
            val range = (pageRange ?: 0 until total).let { requested ->
                maxOf(requested.first, 0)..minOf(requested.last, total - 1)
            }

            for (index in range) {
                currentCoroutineContext().ensureActive()
                val file = imageStore.newPageImageFile(bookId)
                renderPage(renderer, index, file)
                emit(ExtractedPage(index = index, totalPages = total, file = file))
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }.flowOn(ioDispatcher)

    private fun renderPage(renderer: PdfRenderer, index: Int, target: File) {
        renderer.openPage(index).use { page ->
            val scale = targetScale(page.width, page.height)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            // PdfRenderer saydam alanları boyamaz; beyaz zemin olmazsa OCR siyah sayfa görür.
            bitmap.eraseColor(Color.WHITE)
            // FOR_PRINT, metin kenarlarını daha keskin çizer; OCR doğruluğu için önemli.
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            bitmap.recycle()
        }
    }

    /** Sayfayı OCR için yeterli çözünürlüğe ölçekler; gereksiz büyütme bellek yakar. */
    private fun targetScale(width: Int, height: Int): Float {
        val longestSide = maxOf(width, height).coerceAtLeast(1)
        return (TARGET_LONGEST_SIDE.toFloat() / longestSide).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    private fun openRendererBlocking(uri: Uri): Pair<ParcelFileDescriptor, PdfRenderer> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw PdfImportException("PDF dosyası açılamadı")
        return try {
            descriptor to PdfRenderer(descriptor)
        } catch (e: Exception) {
            runCatching { descriptor.close() }
            throw PdfImportException("PDF okunamadı; şifreli veya bozuk olabilir", e)
        }
    }

    private companion object {
        const val TARGET_LONGEST_SIDE = 2200
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 4.0f
        const val JPEG_QUALITY = 92
    }
}

data class ExtractedPage(
    val index: Int,
    val totalPages: Int,
    val file: File,
)

class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
