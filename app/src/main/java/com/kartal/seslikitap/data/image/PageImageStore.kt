package com.kartal.seslikitap.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.kartal.seslikitap.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Çekilen sayfa fotoğraflarının kalıcı deposu ve OCR'a verilecek bitmap'i hazırlayan katman.
 *
 * Fotoğraflar uygulamanın özel dizininde tutulur (telif notu gereği dışarı paylaşılmaz).
 */
@Singleton
class PageImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Yeni bir sayfa fotoğrafının yazılacağı hedef dosya. */
    fun newPageImageFile(bookId: String): File =
        File(bookDir(bookId), "${UUID.randomUUID()}.jpg")

    fun bookDir(bookId: String): File =
        File(File(context.filesDir, "books"), bookId).apply { mkdirs() }

    /**
     * Dosyayı OCR'a uygun bitmap olarak yükler: EXIF dönüşü uygulanır ve çok büyük
     * görüntüler küçültülür (ML Kit için 1080p civarı yeterli, bellek baskısını düşürür).
     */
    suspend fun loadForOcr(file: File): Bitmap = withContext(ioDispatcher) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: error("Görüntü okunamadı: ${file.name}")

        decoded.applyExifRotation(file)
    }

    /** Düzeltilmiş görüntüyü aynı dosyaya yazar; kalıcı olan kırpılmış hâldir. */
    suspend fun save(bitmap: Bitmap, file: File) = withContext(ioDispatcher) {
        file.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        Unit
    }

    suspend fun delete(file: File) = withContext(ioDispatcher) {
        file.delete()
        Unit
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= MAX_DIMENSION || height / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun Bitmap.applyExifRotation(file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
            .also { if (it != this) recycle() }
    }

    private companion object {
        const val MAX_DIMENSION = 1920
        const val JPEG_QUALITY = 92
    }
}
