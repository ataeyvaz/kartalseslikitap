package com.kartal.seslikitap.data.local.mapper

import com.kartal.seslikitap.data.local.entity.BookEntity
import com.kartal.seslikitap.data.local.entity.PageEntity
import com.kartal.seslikitap.data.local.entity.ReadingProgressEntity
import com.kartal.seslikitap.data.local.entity.UserSettingsEntity
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.Page
import com.kartal.seslikitap.domain.model.ReadingProgress
import com.kartal.seslikitap.domain.model.UserSettings
import com.kartal.seslikitap.domain.provider.ProviderId

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    coverImagePath = coverImagePath,
    createdAtEpochMillis = createdAt,
    isChildrenBook = isChildrenBook,
    narratorGender = narratorGender,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    coverImagePath = coverImagePath,
    createdAt = createdAtEpochMillis,
    isChildrenBook = isChildrenBook,
    narratorGender = narratorGender,
)

fun PageEntity.toDomain(): Page = Page(
    id = id,
    bookId = bookId,
    index = index,
    imagePath = imagePath,
    rawOcrText = rawOcrText,
    cleanedText = cleanedText,
    ocrProviderUsed = ocrProviderUsed?.let(::ProviderId),
    confidenceScore = confidenceScore,
    createdAtEpochMillis = createdAt,
)

fun Page.toEntity(): PageEntity = PageEntity(
    id = id,
    bookId = bookId,
    index = index,
    imagePath = imagePath,
    rawOcrText = rawOcrText,
    cleanedText = cleanedText,
    ocrProviderUsed = ocrProviderUsed?.value,
    confidenceScore = confidenceScore,
    createdAt = createdAtEpochMillis,
)

fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    currentPageId = currentPageId,
    currentAudioPositionMillis = currentAudioPositionMillis,
    updatedAtEpochMillis = updatedAt,
)

fun ReadingProgress.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    bookId = bookId,
    currentPageId = currentPageId,
    currentAudioPositionMillis = currentAudioPositionMillis,
    updatedAt = updatedAtEpochMillis,
)

fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
    defaultOcrProviderId = ProviderId(defaultOcrProviderId),
    defaultTtsProviderId = ProviderId(defaultTtsProviderId),
    textCorrectionProviderId = ProviderId(textCorrectionProviderId),
    defaultNarratorGender = defaultNarratorGender,
    playbackSpeed = playbackSpeed,
    pitch = pitch,
    autoFallbackToCloud = autoFallbackToCloud,
    cloudFallbackConfidenceThreshold = cloudFallbackConfidenceThreshold,
    languageTag = languageTag,
)

fun UserSettings.toEntity(): UserSettingsEntity = UserSettingsEntity(
    id = UserSettingsEntity.SINGLETON_ID,
    defaultOcrProviderId = defaultOcrProviderId.value,
    defaultTtsProviderId = defaultTtsProviderId.value,
    textCorrectionProviderId = textCorrectionProviderId.value,
    defaultNarratorGender = defaultNarratorGender,
    playbackSpeed = playbackSpeed,
    pitch = pitch,
    autoFallbackToCloud = autoFallbackToCloud,
    cloudFallbackConfidenceThreshold = cloudFallbackConfidenceThreshold,
    languageTag = languageTag,
)
