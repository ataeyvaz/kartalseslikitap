package com.kartal.seslikitap.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Şema geçişinin gerçek SQLite üzerinde çalıştığını ve **kullanıcı verisini koruduğunu**
 * doğrular. Kitaplar ve okuma ilerlemesi yalnızca cihazda durduğu için bu testin
 * kaybedecek bir şeyi var.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KartalDatabase::class.java,
    )

    @Test
    fun migrate1To2_kullanici_verisini_korur() {
        val bookId = "kitap-1"

        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO books (id, title, cover_image_path, created_at, is_children_book, narrator_gender)
                VALUES ('$bookId', 'Küçük Prens', NULL, 1700000000000, 1, 'FEMALE')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO pages (id, book_id, page_index, image_path, raw_ocr_text, cleaned_text,
                                   ocr_provider_used, confidence_score, created_at)
                VALUES ('sayfa-1', '$bookId', 0, '/tmp/a.jpg', 'ham metin', 'temiz metin',
                        'ml_kit', 0.87, 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO user_settings (id, default_ocr_provider_id, default_tts_provider_id,
                    default_narrator_gender, playback_speed, pitch, auto_fallback_to_cloud,
                    cloud_fallback_confidence_threshold, language_tag)
                VALUES (0, 'ml_kit', 'android_tts', 'NEUTRAL', 1.0, 1.0, 0, 0.6, 'tr-TR')
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        migrated.query("SELECT title, is_children_book, narrator_gender FROM books WHERE id = '$bookId'")
            .use { cursor ->
                assertTrue("Kitap kaydı geçişte kaybolmuş", cursor.moveToFirst())
                assertEquals("Küçük Prens", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("FEMALE", cursor.getString(2))
            }

        migrated.query("SELECT cleaned_text, confidence_score FROM pages WHERE book_id = '$bookId'")
            .use { cursor ->
                assertTrue("Sayfa kaydı geçişte kaybolmuş", cursor.moveToFirst())
                assertEquals("temiz metin", cursor.getString(0))
                assertEquals(0.87f, cursor.getFloat(1), 0.001f)
            }

        // Yeni sütun, mevcut satıra varsayılan değeriyle gelmeli.
        migrated.query("SELECT text_correction_provider_id, language_tag FROM user_settings WHERE id = 0")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("no_correction", cursor.getString(0))
                assertEquals("tr-TR", cursor.getString(1))
            }

        migrated.close()
    }

    /**
     * v2'den v3'e geçiş: elle yazılan `CREATE TABLE`, Room'un beklediği şemayla birebir
     * uyuşmazsa `runMigrationsAndValidate` burada patlar — kullanıcının cihazında
     * açılışta çökmesindense burada.
     */
    @Test
    fun migrate2To3_ses_tercihi_tablosunu_ekler() {
        helper.createDatabase(TEST_DB_V2, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO user_settings (id, default_ocr_provider_id, default_tts_provider_id,
                    text_correction_provider_id, default_narrator_gender, playback_speed, pitch,
                    auto_fallback_to_cloud, cloud_fallback_confidence_threshold, language_tag)
                VALUES (0, 'ml_kit', 'elevenlabs', 'turkish_dictionary', 'NEUTRAL', 1.0, 1.0,
                        0, 0.6, 'tr-TR')
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_V2, 3, true, Migrations.MIGRATION_2_3)

        // Mevcut ayar korunmalı.
        migrated.query("SELECT default_tts_provider_id, text_correction_provider_id FROM user_settings")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("elevenlabs", cursor.getString(0))
                assertEquals("turkish_dictionary", cursor.getString(1))
            }

        // Yeni tablo kullanılabilir olmalı.
        migrated.execSQL(
            "INSERT INTO voice_preferences (provider_id, voice_id, display_name) " +
                "VALUES ('elevenlabs', 'klon-123', 'Kendi Sesim')",
        )
        migrated.query("SELECT voice_id, display_name FROM voice_preferences WHERE provider_id = 'elevenlabs'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("klon-123", cursor.getString(0))
                assertEquals("Kendi Sesim", cursor.getString(1))
            }

        migrated.close()
    }

    /** Eski bir kurulumdan (v1) doğrudan güncelleyen kullanıcı için zincirin tamamı. */
    @Test
    fun migrate1To3_zinciri_calisir() {
        helper.createDatabase(TEST_DB_CHAIN, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO books (id, title, cover_image_path, created_at, is_children_book, narrator_gender)
                VALUES ('eski-kitap', 'Eski Kitap', NULL, 1700000000000, 0, 'MALE')
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_CHAIN, 3, true, *Migrations.ALL)

        migrated.query("SELECT title FROM books WHERE id = 'eski-kitap'").use { cursor ->
            assertTrue("Kitap zincirli geçişte kaybolmuş", cursor.moveToFirst())
            assertEquals("Eski Kitap", cursor.getString(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val TEST_DB_V2 = "migration-test-v2.db"
        const val TEST_DB_CHAIN = "migration-test-chain.db"
    }
}
