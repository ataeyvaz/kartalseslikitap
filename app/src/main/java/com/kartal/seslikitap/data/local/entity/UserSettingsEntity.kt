package com.kartal.seslikitap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kartal.seslikitap.domain.model.NarratorGender

/**
 * Tek satırlık ayar tablosu (id her zaman [SINGLETON_ID]).
 *
 * API anahtarları bilinçli olarak burada tutulmaz — bkz. [com.kartal.seslikitap.domain.model.UserSettings].
 */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "default_ocr_provider_id") val defaultOcrProviderId: String,
    @ColumnInfo(name = "default_tts_provider_id") val defaultTtsProviderId: String,
    @ColumnInfo(name = "text_correction_provider_id", defaultValue = "no_correction")
    val textCorrectionProviderId: String,
    @ColumnInfo(name = "default_narrator_gender") val defaultNarratorGender: NarratorGender,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float,
    @ColumnInfo(name = "pitch") val pitch: Float,
    @ColumnInfo(name = "auto_fallback_to_cloud") val autoFallbackToCloud: Boolean,
    @ColumnInfo(name = "cloud_fallback_confidence_threshold") val cloudFallbackConfidenceThreshold: Float,
    @ColumnInfo(name = "language_tag") val languageTag: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
