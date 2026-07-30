package com.kartal.seslikitap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.kartal.seslikitap.data.local.entity.BookEntity
import com.kartal.seslikitap.data.local.entity.PageEntity
import com.kartal.seslikitap.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY created_at DESC")
    fun observeBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun delete(bookId: String)
}

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE book_id = :bookId ORDER BY page_index ASC")
    fun observePages(bookId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPage(pageId: String): PageEntity?

    @Query("SELECT COALESCE(MAX(page_index), -1) FROM pages WHERE book_id = :bookId")
    suspend fun lastPageIndex(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(page: PageEntity)

    @Update
    suspend fun update(page: PageEntity)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun delete(pageId: String)
}

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    fun observeProgress(bookId: String): Flow<ReadingProgressEntity?>

    @Upsert
    suspend fun upsert(progress: ReadingProgressEntity)
}
