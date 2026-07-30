package com.kartal.seslikitap.di

import com.kartal.seslikitap.data.correction.DictionaryTextCorrectionProvider
import com.kartal.seslikitap.data.correction.NoOpTextCorrectionProvider
import com.kartal.seslikitap.domain.correction.TextCorrectionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Metin düzeltme sağlayıcılarının kayıt noktası.
 *
 * Bulut tabanlı bir dil modeli düzelticisi eklemek istendiğinde (plan Faz 3, "Claude API ile
 * taranmış metni düzelt") yapılacak tek şey, [TextCorrectionProvider]'ı uygulayan sınıfı
 * yazıp buraya bir `@Binds @IntoSet` satırı eklemektir. Ayarlar ekranı sağlayıcıyı
 * kendiliğinden listeler, kimlik bilgisi alanlarını sağlayıcının kendi tanımından üretir.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CorrectionModule {

    @Binds
    @IntoSet
    abstract fun bindNoOpCorrectionProvider(provider: NoOpTextCorrectionProvider): TextCorrectionProvider

    @Binds
    @IntoSet
    abstract fun bindDictionaryCorrectionProvider(
        provider: DictionaryTextCorrectionProvider,
    ): TextCorrectionProvider
}
