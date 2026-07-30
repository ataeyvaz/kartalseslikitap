package com.kartal.seslikitap.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfImportProgressTest {

    @Test
    fun `ilerleme orani hesaplanir`() {
        val progress = PdfImportProgress(
            currentPage = 25,
            totalPages = 100,
            importedPages = 24,
            failedPages = 1,
        )
        assertEquals(0.25f, progress.fraction, 0.0001f)
    }

    @Test
    fun `sayfa sayisi bilinmiyorsa sifir doner`() {
        val progress = PdfImportProgress(currentPage = 5, totalPages = 0, importedPages = 0, failedPages = 0)
        assertEquals(0f, progress.fraction, 0.0001f)
    }

    @Test
    fun `oran bir uzerine cikmaz`() {
        val progress = PdfImportProgress(
            currentPage = 120,
            totalPages = 100,
            importedPages = 100,
            failedPages = 20,
        )
        assertEquals(1f, progress.fraction, 0.0001f)
    }
}
