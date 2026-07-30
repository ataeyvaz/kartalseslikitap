package com.kartal.seslikitap.ui.capture

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX bağlama ve fotoğraf çekme işlerinin ince sarmalayıcısı.
 *
 * Kamera yaşam döngüsüne bağlı olduğu için ViewModel'de değil, Compose ağacında tutulur.
 */
class CameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null

    val imageCapture: ImageCapture = ImageCapture.Builder()
        // Kitap sayfasında öncelik netlik/okunabilirlik; hız ikinci planda.
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    suspend fun bind(lifecycleOwner: LifecycleOwner, preview: Preview) {
        val provider = cameraProvider ?: ProcessCameraProvider.awaitInstance(context)
            .also { cameraProvider = it }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
        )
    }

    fun unbind() {
        cameraProvider?.unbindAll()
    }

    /** Fotoğrafı [target] dosyasına yazar; başarısızlıkta hata fırlatır. */
    suspend fun capture(target: File): File = suspendCancellableCoroutine { continuation ->
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) continuation.resume(target)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }
}
