package com.kartal.seslikitap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kartal.seslikitap.domain.model.NarratorGender

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "cover_image_path") val coverImagePath: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Kitap ekleme ekranındaki tikbox (plan Bölüm 4.5). */
    @ColumnInfo(name = "is_children_book", defaultValue = "0") val isChildrenBook: Boolean,
    @ColumnInfo(name = "narrator_gender") val narratorGender: NarratorGender,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["book_id", "page_index"], unique = true)],
)
data class PageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    /** `order` SQL'de ayrılmış kelime olduğu için sütun adı `page_index`. */
    @ColumnInfo(name = "page_index") val index: Int,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "raw_ocr_text") val rawOcrText: String,
    @ColumnInfo(name = "cleaned_text") val cleanedText: String,
    @ColumnInfo(name = "ocr_provider_used") val ocrProviderUsed: String?,
    @ColumnInfo(name = "confidence_score") val confidenceScore: Float?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "current_page_id") val currentPageId: String?,
    @ColumnInfo(name = "current_audio_position_ms") val currentAudioPositionMillis: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
