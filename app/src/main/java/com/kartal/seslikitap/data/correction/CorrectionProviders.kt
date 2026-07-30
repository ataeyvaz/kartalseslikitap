package com.kartal.seslikitap.data.correction

import com.kartal.seslikitap.di.DefaultDispatcher
import com.kartal.seslikitap.domain.correction.CorrectionResult
import com.kartal.seslikitap.domain.correction.DictionaryCorrector
import com.kartal.seslikitap.domain.correction.TextCorrectionProvider
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.ProviderIds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Varsayılan: düzeltme yok.
 *
 * OCR çıktısını olduğu gibi bırakır. Kullanıcı bilinçli olarak seçmedikçe metne hiçbir
 * müdahale yapılmaması ilkesinin karşılığıdır.
 */
@Singleton
class NoOpTextCorrectionProvider @Inject constructor() : TextCorrectionProvider {

    override val id: ProviderId = ProviderIds.NoCorrection
    override val name: String = "Düzeltme yok"
    override val requiresApiKey: Boolean = false
    override val isOnDevice: Boolean = true

    override suspend fun correct(text: String, languageTag: String?): CorrectionResult =
        CorrectionResult.unchanged(text)
}

/**
 * Sözlük tabanlı, tamamen cihaz üzerinde çalışan düzeltici.
 *
 * Türkçe dışındaki dillerde sözlüğümüz olmadığı için metni değiştirmeden döner —
 * yanlış dilde "düzeltme" yapmak metni bozmaktan başka bir şey olmaz.
 */
@Singleton
class DictionaryTextCorrectionProvider @Inject constructor(
    private val lexiconLoader: AssetLexiconLoader,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : TextCorrectionProvider {

    override val id: ProviderId = ProviderIds.TurkishDictionary
    override val name: String = "Türkçe sözlük (cihaz üzerinde)"
    override val requiresApiKey: Boolean = false
    override val isOnDevice: Boolean = true

    override suspend fun isAvailable(): Boolean = lexiconLoader.load().size > 0

    override suspend fun correct(text: String, languageTag: String?): CorrectionResult {
        if (!languageTag.isTurkishOrUnset()) return CorrectionResult.unchanged(text)

        val lexicon = lexiconLoader.load()
        if (lexicon.size == 0) return CorrectionResult.unchanged(text)

        return withContext(defaultDispatcher) {
            DictionaryCorrector(lexicon).correct(text)
        }
    }

    /** Dil belirtilmemişse cihaz Türkçe varsayılır; uygulamanın hedef kitlesi budur. */
    private fun String?.isTurkishOrUnset(): Boolean =
        this == null || substringBefore('-').equals("tr", ignoreCase = true)
}
