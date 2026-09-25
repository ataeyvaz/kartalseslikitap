package com.kartal.seslikitap.di

// import com.kartal.seslikitap.data.provider.androidtts.AndroidTtsProvider
// import com.kartal.seslikitap.data.provider.aws.AwsTextractOcrProvider
// import com.kartal.seslikitap.data.provider.azure.AzureVisionOcrProvider
// import com.kartal.seslikitap.data.provider.androidtts.AndroidTtsVoiceMappingResolver
// import com.kartal.seslikitap.data.provider.elevenlabs.ElevenLabsTtsProvider
// import com.kartal.seslikitap.data.provider.elevenlabs.ElevenLabsVoiceMappingResolver
// import com.kartal.seslikitap.data.provider.googlecloud.GoogleCloudTtsProvider
// import com.kartal.seslikitap.data.provider.googlecloud.GoogleCloudTtsVoiceMappingResolver
// import com.kartal.seslikitap.data.provider.googlecloud.GoogleCloudVisionOcrProvider
import com.kartal.seslikitap.data.provider.mlkit.MlKitOcrProvider
import com.kartal.seslikitap.data.provider.piper.PiperTtsProvider
import com.kartal.seslikitap.data.provider.piper.PiperVoiceMappingResolver
import com.kartal.seslikitap.data.security.KeystoreApiKeyStore
import com.kartal.seslikitap.domain.provider.OcrProvider
import com.kartal.seslikitap.domain.provider.TtsProvider
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import com.kartal.seslikitap.domain.security.ApiKeyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Sağlayıcı kayıt noktası.
 *
 * Yeni bir OCR/TTS sağlayıcısı eklemek = burada bir `@Binds @IntoSet` satırı.
 * Üst katmanlardaki hiçbir kod değişmez (plan Bölüm 1).
 *
 * **Şimdilik kapalı (2026-09-25, kullanıcı isteği):** kullanıcıya yalnız ML Kit (OCR) ve Ata'nın sesi (TTS)
 * gösterilir. Bulut sağlayıcıları ve Android TTS'in kayıt satırları yorum satırına alındı; kodları yerinde.
 * Yeniden açmak için ilgili `@Binds @IntoSet` satırını ve importunu geri açmak yeter. Ayarlar ekranı listeyi
 * kayıtlardan ürettiği için başka bir yerin değişmesi gerekmez.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    // --- OCR ---

    @Binds
    @IntoSet
    abstract fun bindMlKitOcrProvider(provider: MlKitOcrProvider): OcrProvider

    // @Binds
    // @IntoSet
    // abstract fun bindGoogleCloudVisionProvider(provider: GoogleCloudVisionOcrProvider): OcrProvider

    // @Binds
    // @IntoSet
    // abstract fun bindAzureVisionProvider(provider: AzureVisionOcrProvider): OcrProvider

    // @Binds
    // @IntoSet
    // abstract fun bindAwsTextractProvider(provider: AwsTextractOcrProvider): OcrProvider

    // --- TTS ---

    // @Binds
    // @IntoSet
    // abstract fun bindAndroidTtsProvider(provider: AndroidTtsProvider): TtsProvider

    // @Binds
    // @IntoSet
    // abstract fun bindGoogleCloudTtsProvider(provider: GoogleCloudTtsProvider): TtsProvider

    // @Binds
    // @IntoSet
    // abstract fun bindElevenLabsTtsProvider(provider: ElevenLabsTtsProvider): TtsProvider

    @Binds
    @IntoSet
    abstract fun bindPiperTtsProvider(provider: PiperTtsProvider): TtsProvider

    // --- Ses eşleme ---

    // @Binds
    // @IntoSet
    // abstract fun bindAndroidTtsVoiceResolver(
    //     resolver: AndroidTtsVoiceMappingResolver,
    // ): VoiceMappingResolver

    // @Binds
    // @IntoSet
    // abstract fun bindGoogleCloudTtsVoiceResolver(
    //     resolver: GoogleCloudTtsVoiceMappingResolver,
    // ): VoiceMappingResolver

    // @Binds
    // @IntoSet
    // abstract fun bindElevenLabsVoiceResolver(
    //     resolver: ElevenLabsVoiceMappingResolver,
    // ): VoiceMappingResolver

    @Binds
    @IntoSet
    abstract fun bindPiperVoiceResolver(resolver: PiperVoiceMappingResolver): VoiceMappingResolver

    // --- Anahtar deposu ---

    @Binds
    @Singleton
    abstract fun bindApiKeyStore(store: KeystoreApiKeyStore): ApiKeyStore
}
