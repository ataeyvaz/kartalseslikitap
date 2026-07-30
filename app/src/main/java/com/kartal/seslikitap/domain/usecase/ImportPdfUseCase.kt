package com.kartal.seslikitap.domain.usecase

import android.net.Uri
import android.util.Log
import com.kartal.seslikitap.data.pdf.PdfPageExtractor
import com.kartal.seslikitap.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * PDF'i sayfa sayfa içe aktarır: her sayfa görüntüye dönüştürülür, aynı OCR + temizleme +
 * düzeltme boru hattından geçer ve kitaba eklenir.
 *
 * Fotoğrafla çekimden tek farkı görüntünün kaynağıdır; OCR sağlayıcısı, kenar düzeltme ve
 * metin düzeltme aynen çalışır. Bu yüzden [RecognizePageUseCase] olduğu gibi kullanılır.
 *
 * İlerleme akış olarak yayınlanır: 300 sayfalık bir kitapta kullanıcı ne olduğunu görmeli
 * ve istediğinde iptal edebilmelidir (akışın toplanması durdurulunca işlem de durur).
 */
class ImportPdfUseCase @Inject constructor(
    private val pdfPageExtractor: PdfPageExtractor,
    private val recognizePage: RecognizePageUseCase,
    private val bookRepository: BookRepository,
) {

    suspend fun pageCount(uri: Uri): Int = pdfPageExtractor.pageCount(uri)

    operator fun invoke(
        uri: Uri,
        bookId: String,
        pageRange: IntRange? = null,
    ): Flow<PdfImportProgress> = flow {
        var imported = 0
        var failed = 0

        pdfPageExtractor.extractPages(uri, bookId, pageRange).collect { page ->
            emit(
                PdfImportProgress(
                    currentPage = page.index + 1,
                    totalPages = page.totalPages,
                    importedPages = imported,
                    failedPages = failed,
                ),
            )

            try {
                val recognized = recognizePage(page.file)
                if (recognized.cleanedText.isBlank()) {
                    // Boş sayfa (kapak, ayraç): görüntüyü tutmanın anlamı yok.
                    failed++
                } else {
                    bookRepository.addPage(
                        bookId = bookId,
                        imagePath = page.file.absolutePath,
                        rawOcrText = recognized.result.text,
                        cleanedText = recognized.cleanedText,
                        ocrProviderUsed = recognized.result.providerId,
                        confidenceScore = recognized.result.confidence,
                    )
                    imported++
                }
            } catch (e: Exception) {
                // Tek bir bozuk sayfa yüzünden 300 sayfalık içe aktarma durmamalı.
                Log.w(TAG, "PDF sayfası işlenemedi: ${page.index}", e)
                failed++
            }
        }

        emit(
            PdfImportProgress(
                currentPage = imported + failed,
                totalPages = imported + failed,
                importedPages = imported,
                failedPages = failed,
                isFinished = true,
            ),
        )
    }

    private companion object {
        const val TAG = "ImportPdfUseCase"
    }
}

data class PdfImportProgress(
    val currentPage: Int,
    val totalPages: Int,
    val importedPages: Int,
    val failedPages: Int,
    val isFinished: Boolean = false,
) {
    val fraction: Float
        get() = if (totalPages <= 0) 0f else (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
}
