package com.kartal.seslikitap.di

import com.kartal.seslikitap.data.imaging.OpenCvPageCropper
import com.kartal.seslikitap.domain.imaging.PageCropper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {

    @Binds
    @Singleton
    abstract fun bindPageCropper(impl: OpenCvPageCropper): PageCropper
}
