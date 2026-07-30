package com.kartal.seslikitap.data.repository

import com.kartal.seslikitap.data.local.dao.BookDao
import com.kartal.seslikitap.data.local.dao.PageDao
import com.kartal.seslikitap.data.local.dao.ReadingProgressDao
import com.kartal.seslikitap.data.local.mapper.toDomain
import com.kartal.seslikitap.data.local.mapper.toEntity
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.Page
import com.kartal.seslikitap.domain.model.ReadingProgress
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.repository.BookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val pageDao: PageDao,
    private val progressDao: ReadingProgressDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BookRepository {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { books -> books.map { it.toDomain() } }

    override fun observePages(bookId: String): Flow<List<Page>> =
        pageDao.observePages(bookId).map { pages -> pages.map { it.toDomain() } }

    override fun observeProgress(bookId: String): Flow<ReadingProgress?> =
        progressDao.observeProgress(bookId).map { it?.toDomain() }

    override suspend fun getBook(bookId: String): Book? = withContext(ioDispatcher) {
        bookDao.getBook(bookId)?.toDomain()
    }

    override suspend fun getPage(pageId: String): Page? = withContext(ioDispatcher) {
        pageDao.getPage(pageId)?.toDomain()
    }

    override suspend fun createBook(
        title: String,
        isChildrenBook: Boolean,
        narratorGender: NarratorGender,
        coverImagePath: String?,
    ): Book = withContext(ioDispatcher) {
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            coverImagePath = coverImagePath,
            createdAtEpochMillis = System.currentTimeMillis(),
            isChildrenBook = isChildrenBook,
            narratorGender = narratorGender,
        )
        bookDao.insert(book.toEntity())
        book
    }

    override suspend fun updateBook(book: Book) = withContext(ioDispatcher) {
        bookDao.update(book.toEntity())
    }

    override suspend fun deleteBook(bookId: String) = withContext(ioDispatcher) {
        // Sayfalar ve ilerleme kaydı foreign key CASCADE ile birlikte silinir.
        bookDao.delete(bookId)
    }

    override suspend fun addPage(
        bookId: String,
        imagePath: String?,
        rawOcrText: String,
        cleanedText: String,
        ocrProviderUsed: ProviderId?,
        confidenceScore: Float?,
    ): Page = withContext(ioDispatcher) {
        val page = Page(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            index = pageDao.lastPageIndex(bookId) + 1,
            imagePath = imagePath,
            rawOcrText = rawOcrText,
            cleanedText = cleanedText,
            ocrProviderUsed = ocrProviderUsed,
            confidenceScore = confidenceScore,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        pageDao.insert(page.toEntity())
        page
    }

    override suspend fun updatePage(page: Page) = withContext(ioDispatcher) {
        pageDao.update(page.toEntity())
    }

    override suspend fun deletePage(pageId: String) = withContext(ioDispatcher) {
        pageDao.delete(pageId)
    }

    override suspend fun saveProgress(progress: ReadingProgress) = withContext(ioDispatcher) {
        progressDao.upsert(progress.toEntity())
    }
}
