package com.kartal.seslikitap.domain.model

import com.kartal.seslikitap.domain.provider.ProviderId

data class Book(
    val id: String,
    val title: String,
    val coverImagePath: String? = null,
    val createdAtEpochMillis: Long,
    /** Kitap oluşturma ekranındaki tikbox (plan Bölüm 4.5) — sistem bunu tahmin etmez. */
    val isChildrenBook: Boolean = false,
    val narratorGender: NarratorGender = NarratorGender.NEUTRAL,
)

data class Page(
    val id: String,
    val bookId: String,
    /** Kitap içindeki 0 tabanlı sıra. */
    val index: Int,
    val imagePath: String?,
    val rawOcrText: String,
    val cleanedText: String,
    val ocrProviderUsed: ProviderId?,
    val confidenceScore: Float?,
    val createdAtEpochMillis: Long,
)

data class ReadingProgress(
    val bookId: String,
    val currentPageId: String?,
    val currentAudioPositionMillis: Long = 0L,
    val updatedAtEpochMillis: Long,
)
