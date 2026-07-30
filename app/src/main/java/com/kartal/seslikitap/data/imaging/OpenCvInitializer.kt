package com.kartal.seslikitap.data.imaging

import android.util.Log
import org.opencv.android.OpenCVLoader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenCV native kitaplığını bir kez yükler.
 *
 * Yükleme başarısız olursa (beklenmedik ABI, bozuk kurulum) uygulama çökmez; kenar tespiti
 * devre dışı kalır ve ham kare OCR'a gider. Kırpma bir iyileştirmedir, akışın ön koşulu değil.
 */
@Singleton
class OpenCvInitializer @Inject constructor() {

    val isReady: Boolean by lazy {
        val loaded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        if (!loaded) {
            Log.w(TAG, "OpenCV yüklenemedi; kenar tespiti ve perspektif düzeltme devre dışı")
        }
        loaded
    }

    private companion object {
        const val TAG = "OpenCvInitializer"
    }
}
