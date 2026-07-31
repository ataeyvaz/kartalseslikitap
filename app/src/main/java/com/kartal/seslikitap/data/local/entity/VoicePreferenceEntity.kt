package com.kartal.seslikitap.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kullanıcının bir sağlayıcı için sabitlediği ses.
 *
 * Otomatik ses seçimi cinsiyet ve doğallık skoruna bakar; ama kullanıcı kendi klonladığı
 * sesi ya da özellikle beğendiği bir sesi seçtiyse **o seçim her şeyin önüne geçer**.
 * Sağlayıcı başına tutulur, çünkü ses kimlikleri sağlayıcıya özeldir.
 */
@Entity(tableName = "voice_preferences")
data class VoicePreferenceEntity(
    @PrimaryKey @ColumnInfo(name = "provider_id") val providerId: String,
    @ColumnInfo(name = "voice_id") val voiceId: String,
    /** Ayarlar ekranında gösterilecek ad; ses listesi yüklenemese de kullanıcı ne seçtiğini görsün. */
    @ColumnInfo(name = "display_name") val displayName: String,
)
