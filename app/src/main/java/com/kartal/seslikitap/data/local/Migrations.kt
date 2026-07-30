package com.kartal.seslikitap.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Şema geçişleri.
 *
 * Kullanıcının kitapları ve okuma ilerlemesi yerelde tutulduğu için veri kaybına yol açan
 * `fallbackToDestructiveMigration` bilinçli olarak kullanılmıyor.
 */
object Migrations {

    /** v2: OCR sonrası metin düzeltme sağlayıcısı ayarı eklendi. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE user_settings ADD COLUMN text_correction_provider_id " +
                    "TEXT NOT NULL DEFAULT 'no_correction'",
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2)
}
