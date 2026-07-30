package com.kartal.seslikitap.data.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OpenCV'nin gerçekten yüklendiğini ve kenar tespitinin çalıştığını doğrular.
 *
 * Native kitaplık ABI başına paketlendiği için "derleniyor" ile "cihazda çalışıyor"
 * arasında gerçek bir fark var; burada ikincisi ölçülüyor.
 */
@RunWith(AndroidJUnit4::class)
class OpenCvPageCropperTest {

    private lateinit var cropper: OpenCvPageCropper
    private lateinit var initializer: OpenCvInitializer

    @Before
    fun setUp() {
        initializer = OpenCvInitializer()
        cropper = OpenCvPageCropper(initializer, Dispatchers.Default)
    }

    @Test
    fun opencv_native_kitapligi_yuklenir() {
        assertTrue("OpenCV bu ABI'de yüklenemedi", initializer.isReady)
    }

    @Test
    fun egik_sayfa_tespit_edilip_duzeltilir() = runTest {
        val source = sceneWithTiltedPage()

        val result = cropper.crop(source)

        assertTrue("Sayfa kenarları bulunamadı", result.wasCorrected)
        assertTrue(result.quad != null)
        // Düzeltilen görüntü kadrajın tamamı olmamalı: gerçekten kırpılmış olmalı.
        assertTrue(
            "Kırpılan görüntü kaynakla aynı boyutta: ${result.bitmap.width}x${result.bitmap.height}",
            result.bitmap.width < source.width,
        )
    }

    @Test
    fun sayfa_yoksa_ham_kare_kullanilir() = runTest {
        val noisy = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(90, 90, 90))
        }

        val result = cropper.crop(noisy)

        assertTrue("Sayfa yokken kırpma yapılmamalı", !result.wasCorrected)
        assertEquals(noisy, result.bitmap)
    }

    @Test
    fun kucuk_nesne_sayfa_sayilmaz() = runTest {
        // Koyu zeminde küçük beyaz kart: kadrajın %25'inden küçük, reddedilmeli.
        val scene = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 40, 40))
        }
        Canvas(scene).drawRect(100f, 100f, 350f, 300f, Paint().apply { color = Color.WHITE })

        val result = cropper.crop(scene)

        assertTrue("Küçük nesne sayfa sanıldı", !result.wasCorrected)
    }

    /** Koyu masa üzerinde hafif eğik duran beyaz bir sayfa. */
    private fun sceneWithTiltedPage(): Bitmap {
        val bitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(35, 35, 35))

        val page = Path().apply {
            moveTo(120f, 180f)
            lineTo(880f, 120f)
            lineTo(920f, 1260f)
            lineTo(150f, 1310f)
            close()
        }
        canvas.drawPath(page, Paint().apply { color = Color.WHITE; isAntiAlias = true })

        // Sayfa üzerinde metin izlenimi veren satırlar (kenar tespitini zorlaştırır).
        val textPaint = Paint().apply { color = Color.rgb(60, 60, 60); strokeWidth = 6f }
        var y = 320f
        repeat(12) {
            canvas.drawLine(220f, y, 780f, y - 20f, textPaint)
            y += 70f
        }
        return bitmap
    }
}
