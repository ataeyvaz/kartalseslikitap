package com.kartal.seslikitap.di

import android.content.Context
import androidx.room.Room
import com.kartal.seslikitap.data.local.KartalDatabase
import com.kartal.seslikitap.data.local.Migrations
import com.kartal.seslikitap.data.local.dao.BookDao
import com.kartal.seslikitap.data.local.dao.PageDao
import com.kartal.seslikitap.data.local.dao.ReadingProgressDao
import com.kartal.seslikitap.data.local.dao.UserSettingsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KartalDatabase =
        Room.databaseBuilder(context, KartalDatabase::class.java, KartalDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .build()

    @Provides
    fun provideBookDao(db: KartalDatabase): BookDao = db.bookDao()

    @Provides
    fun providePageDao(db: KartalDatabase): PageDao = db.pageDao()

    @Provides
    fun provideReadingProgressDao(db: KartalDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideUserSettingsDao(db: KartalDatabase): UserSettingsDao = db.userSettingsDao()
}
