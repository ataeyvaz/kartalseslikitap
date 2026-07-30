package com.kartal.seslikitap.domain.repository

import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.Page
import com.kartal.seslikitap.domain.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {

    fun observeBooks(): Flow<List<Book>>

    fun observePages(bookId: String): Flow<List<Page>>

    fun observeProgress(bookId: String): Flow<ReadingProgress?>

    suspend fun getBook(bookId: String): Book?

    suspend fun getPage(pageId: String): Page?

    /** Kitap oluşturma ekranından gelen iki kullanıcı girdisiyle birlikte kitabı yaratır. */
    suspend fun createBook(
        title: String,
        isChildrenBook: Boolean,
        narratorGender: NarratorGender,
        coverImagePath: String? = null,
    ): Book

    suspend fun updateBook(book: Book)

    suspend fun deleteBook(bookId: String)

    /** Sayfayı kitabın sonuna ekler ve oluşturulan sayfayı döner. */
    suspend fun addPage(
        bookId: String,
        imagePath: String?,
        rawOcrText: String,
        cleanedText: String,
        ocrProviderUsed: com.kartal.seslikitap.domain.provider.ProviderId?,
        confidenceScore: Float?,
    ): Page

    suspend fun updatePage(page: Page)

    suspend fun deletePage(pageId: String)

    suspend fun saveProgress(progress: ReadingProgress)
}
