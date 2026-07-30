package com.kartal.seslikitap.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kartal.seslikitap.data.local.dao.BookDao
import com.kartal.seslikitap.data.local.dao.PageDao
import com.kartal.seslikitap.data.local.dao.ReadingProgressDao
import com.kartal.seslikitap.data.local.dao.UserSettingsDao
import com.kartal.seslikitap.data.local.entity.BookEntity
import com.kartal.seslikitap.data.local.entity.PageEntity
import com.kartal.seslikitap.data.local.entity.ReadingProgressEntity
import com.kartal.seslikitap.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        BookEntity::class,
        PageEntity::class,
        ReadingProgressEntity::class,
        UserSettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KartalDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun pageDao(): PageDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        const val NAME = "kartal_sesli_kitap.db"
    }
}
