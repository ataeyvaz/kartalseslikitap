package com.kartal.seslikitap.data.imaging

import android.graphics.Bitmap
import android.util.Log
import com.kartal.seslikitap.di.DefaultDispatcher
import com.kartal.seslikitap.domain.imaging.CropResult
import com.kartal.seslikitap.domain.imaging.PageCropper
import com.kartal.seslikitap.domain.imaging.PagePoint
import com.kartal.seslikitap.domain.imaging.PageQuad
import com.kartal.seslikitap.domain.imaging.QuadGeometry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kitap sayfasının kenarlarını bulup perspektifi düzelten OpenCV uygulaması.
 *
 * Boru hattı: küçült -> gri + bulanıklaştır -> Canny + morfolojik kapatma -> konturlar ->
 * en büyük dörtgen -> makullük kontrolü -> tam çözünürlükte warpPerspective.
 *
 * Küçültülmüş kopya üzerinde arama yapılır (hız), bulunan köşeler orijinal ölçeğe geri
 * çarpılır; böylece düzeltme kaybı olmadan tam çözünürlükte uygulanır.
 */
@Singleton
class OpenCvPageCropper @Inject constructor(
    private val openCv: OpenCvInitializer,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : PageCropper {

    override suspend fun crop(bitmap: Bitmap): CropResult = withContext(defaultDispatcher) {
        if (!openCv.isReady) {
            return@withContext CropResult(bitmap, quad = null, wasCorrected = false)
        }

        val source = Mat()
        try {
            Utils.bitmapToMat(bitmap, source)
            val quad = detectQuad(source)
                ?: return@withContext CropResult(bitmap, quad = null, wasCorrected = false)

            if (!QuadGeometry.isPlausiblePage(quad, bitmap.width, bitmap.height)) {
                // Yanlış kırpmaktansa ham kareyi OCR'a vermek daha güvenli.
                return@withContext CropResult(bitmap, quad = quad, wasCorrected = false)
            }

            CropResult(bitmap = warp(source, quad), quad = quad, wasCorrected = true)
        } catch (e: Exception) {
            Log.w(TAG, "Kenar tespiti başarısız, ham görüntü kullanılıyor", e)
            CropResult(bitmap, quad = null, wasCorrected = false)
        } finally {
            source.release()
        }
    }

    private fun detectQuad(source: Mat): PageQuad? {
        val scale = DETECTION_WIDTH / source.width().toDouble()
        val working = Mat()
        val contours = mutableListOf<MatOfPoint>()
        try {
            if (scale < 1.0) {
                Imgproc.resize(source, working, Size(DETECTION_WIDTH, source.height() * scale))
            } else {
                source.copyTo(working)
            }

            Imgproc.cvtColor(working, working, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(working, working, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(working, working, CANNY_LOW, CANNY_HIGH)
            // Kesik kenarları birleştir: sayfa kenarı gölge/parlama yüzünden kopabiliyor.
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(working, working, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            Imgproc.findContours(
                working,
                contours,
                Mat(),
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val effectiveScale = if (scale < 1.0) scale else 1.0
            return contours
                .sortedByDescending { Imgproc.contourArea(it) }
                .take(MAX_CANDIDATE_CONTOURS)
                .firstNotNullOfOrNull { contour -> contour.toQuadOrNull(effectiveScale) }
        } finally {
            working.release()
            contours.forEach { it.release() }
        }
    }

    /** Konturu dörtgene indirger; 4 köşeye inmiyorsa aday değildir. */
    private fun MatOfPoint.toQuadOrNull(scale: Double): PageQuad? {
        val curve = MatOfPoint2f(*toArray())
        val approx = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(curve, true)
            Imgproc.approxPolyDP(curve, approx, APPROX_EPSILON_RATIO * perimeter, true)
            val points = approx.toArray()
            if (points.size != 4) return null

            return QuadGeometry.order(points.map { PagePoint(it.x / scale, it.y / scale) })
        } finally {
            curve.release()
            approx.release()
        }
    }

    private fun warp(source: Mat, quad: PageQuad): Bitmap {
        val (width, height) = QuadGeometry.targetSize(quad)
        val sourcePoints = MatOfPoint2f(
            quad.topLeft.toCvPoint(),
            quad.topRight.toCvPoint(),
            quad.bottomRight.toCvPoint(),
            quad.bottomLeft.toCvPoint(),
        )
        val destinationPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0),
        )
        val transform = Imgproc.getPerspectiveTransform(sourcePoints, destinationPoints)
        val output = Mat(height, width, CvType.CV_8UC4)
        try {
            Imgproc.warpPerspective(source, output, transform, Size(width.toDouble(), height.toDouble()))
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, result)
            return result
        } finally {
            sourcePoints.release()
            destinationPoints.release()
            transform.release()
            output.release()
        }
    }

    private fun PagePoint.toCvPoint() = Point(x, y)

    private companion object {
        const val TAG = "OpenCvPageCropper"
        const val DETECTION_WIDTH = 800.0
        const val CANNY_LOW = 60.0
        const val CANNY_HIGH = 180.0
        const val APPROX_EPSILON_RATIO = 0.02
        const val MAX_CANDIDATE_CONTOURS = 6
    }
}
