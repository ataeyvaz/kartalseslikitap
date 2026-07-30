package com.kartal.seslikitap.data.correction

import android.content.Context
import android.util.Log
import com.kartal.seslikitap.di.IoDispatcher
import com.kartal.seslikitap.domain.correction.WordLexicon
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sözcük dağarcığını uygulama varlıklarından (assets) yükler.
 *
 * ~110 bin kayıt olduğu için yükleme bir kez yapılır ve bellekte tutulur. Sözlük yoksa
 * veya bozuksa boş dağarcık döner: düzeltme devre dışı kalır ama akış çalışmaya devam eder.
 */
@Singleton
class AssetLexiconLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private var cached: WordLexicon? = null

    suspend fun load(): WordLexicon = mutex.withLock {
        cached?.let { return@withLock it }

        val lexicon = withContext(ioDispatcher) {
            runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().useLines(WordLexicon::fromLines)
            }.getOrElse { error ->
                Log.w(TAG, "Sözlük yüklenemedi; metin düzeltme devre dışı", error)
                WordLexicon.Empty
            }
        }
        cached = lexicon
        lexicon
    }

    private companion object {
        const val TAG = "AssetLexiconLoader"
        const val ASSET_PATH = "dictionaries/tr_lexicon.tsv"
    }
}
