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

    /** v3: sağlayıcı başına sabitlenmiş ses tercihi eklendi. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS voice_preferences (
                    provider_id TEXT NOT NULL PRIMARY KEY,
                    voice_id TEXT NOT NULL,
                    display_name TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
