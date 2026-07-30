package com.kartal.seslikitap.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted },
    )

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bookTitle.ifBlank { "Sayfa çek" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
                actions = {
                    TextButton(
                        onClick = { onFinished(viewModel.bookId) },
                        enabled = state.phase != CapturePhase.SAVING,
                    ) { Text("Bitir") }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasCameraPermission -> PermissionRequest(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )

                state.phase == CapturePhase.REVIEW || state.phase == CapturePhase.SAVING ->
                    ReviewPane(
                        state = state,
                        onTextChange = viewModel::updateRecognizedText,
                        onRetake = viewModel::retake,
                        onPreview = viewModel::previewAloud,
                        onStop = viewModel::stopSpeaking,
                        onSave = { viewModel.savePage {} },
                    )

                else -> CameraPane(
                    state = state,
                    onCapture = viewModel::onImageCaptured,
                    onCaptureFailed = viewModel::onCaptureFailed,
                    newImageFile = viewModel::newImageFile,
                )
            }
        }
    }
}

@Composable
private fun CameraPane(
    state: CaptureUiState,
    onCapture: (java.io.File) -> Unit,
    onCaptureFailed: (String) -> Unit,
    newImageFile: () -> java.io.File,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember { CameraController(context) }
    var isCapturing by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        onDispose { controller.unbind() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            update = { previewView ->
                scope.launch {
                    val preview = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }
                    runCatching { controller.bind(lifecycleOwner, preview) }
                        .onFailure { onCaptureFailed(it.message ?: "Kamera başlatılamadı") }
                }
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.savedPageCount > 0) {
                Text("${state.savedPageCount} sayfa kaydedildi")
            }
            if (state.phase == CapturePhase.RECOGNIZING) {
                CircularProgressIndicator()
                Text("Metin tanınıyor…")
            } else {
                Button(
                    enabled = !isCapturing,
                    onClick = {
                        isCapturing = true
                        scope.launch {
                            try {
                                val file = controller.capture(newImageFile())
                                onCapture(file)
                            } catch (e: Exception) {
                                onCaptureFailed(e.message ?: "Fotoğraf çekilemedi")
                            } finally {
                                isCapturing = false
                            }
                        }
                    },
                ) {
                    Text(if (isCapturing) "Çekiliyor…" else "Sayfayı çek")
                }
            }
        }
    }
}

@Composable
private fun ReviewPane(
    state: CaptureUiState,
    onTextChange: (String) -> Unit,
    onRetake: () -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.pendingImagePath?.let { path ->
            CapturedPagePreview(imagePath = path)
        }

        Text("Tanınan metin", style = MaterialTheme.typography.titleMedium)
        Text(
            text = buildString {
                append("Sağlayıcı: ${state.ocrProviderName ?: "-"}")
                state.confidence?.let { append(" · Güven: %${(it * 100).toInt()}") }
                append(
                    if (state.wasPerspectiveCorrected) {
                        " · Kenarlar düzeltildi"
                    } else {
                        " · Kenar bulunamadı, ham kare kullanıldı"
                    },
                )
                if (state.correctionCount > 0) {
                    append(" · ${state.correctionCount} kelime düzeltildi")
                }
            },
            style = MaterialTheme.typography.labelMedium,
        )

        OutlinedTextField(
            value = state.recognizedText,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            label = { Text("Gerekirse düzelt") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = if (state.isSpeaking) onStop else onPreview) {
                Text(if (state.isSpeaking) "Durdur" else "Dinle")
            }
            OutlinedButton(onClick = onRetake) { Text("Tekrar çek") }
        }

        Button(
            onClick = onSave,
            enabled = state.phase != CapturePhase.SAVING && state.recognizedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.phase == CapturePhase.SAVING) "Kaydediliyor…" else "Kaydet ve sonraki sayfa")
        }

        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Kırpma sonucunu kullanıcıya gösterir: yanlış kırpıldıysa hemen "Tekrar çek" diyebilsin. */
@Composable
private fun CapturedPagePreview(imagePath: String) {
    val bitmap = remember(imagePath) {
        runCatching { BitmapFactory.decodeFile(imagePath)?.asImageBitmap() }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Düzeltilmiş sayfa görüntüsü",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
        )
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sayfa çekmek için kamera izni gerekiyor.")
        Button(onClick = onRequest) { Text("İzin ver") }
    }
}
