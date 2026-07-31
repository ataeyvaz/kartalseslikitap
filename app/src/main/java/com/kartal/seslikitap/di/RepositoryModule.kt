package com.kartal.seslikitap.di

import com.kartal.seslikitap.data.repository.BookRepositoryImpl
import com.kartal.seslikitap.data.repository.SettingsRepositoryImpl
import com.kartal.seslikitap.data.repository.VoicePreferenceRepositoryImpl
import com.kartal.seslikitap.domain.repository.BookRepository
import com.kartal.seslikitap.domain.repository.SettingsRepository
import com.kartal.seslikitap.domain.repository.VoicePreferenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindVoicePreferenceRepository(
        impl: VoicePreferenceRepositoryImpl,
    ): VoicePreferenceRepository
}
