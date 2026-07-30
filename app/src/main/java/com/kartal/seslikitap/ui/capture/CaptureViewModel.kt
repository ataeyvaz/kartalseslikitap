package com.kartal.seslikitap.ui.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.data.image.PageImageStore
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.repository.BookRepository
import com.kartal.seslikitap.domain.usecase.ReadPageAloudUseCase
import com.kartal.seslikitap.domain.usecase.RecognizePageUseCase
import com.kartal.seslikitap.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * "Çek -> OCR -> oku" akışının çekim/tanıma yarısı.
 *
 * Fotoğraf çekildikten sonra OCR sonucu kullanıcıya gösterilir; kullanıcı onaylarsa
 * sayfa Room'a yazılır, beğenmezse fotoğraf silinip tekrar çekilir.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val imageStore: PageImageStore,
    private val recognizePage: RecognizePageUseCase,
    private val readPageAloud: ReadPageAloudUseCase,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var book: Book? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            book = bookRepository.getBook(bookId)
            _uiState.update { it.copy(bookTitle = book?.title.orEmpty()) }
        }
    }

    /** Kamera dosyaya yazdıktan sonra çağrılır. */
    fun onImageCaptured(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = CapturePhase.RECOGNIZING, errorMessage = null) }
            try {
                val recognized = recognizePage(file)
                _uiState.update {
                    it.copy(
                        phase = CapturePhase.REVIEW,
                        pendingImagePath = file.absolutePath,
                        recognizedText = recognized.cleanedText,
                        rawText = recognized.result.text,
                        confidence = recognized.result.confidence,
                        ocrProviderName = recognized.result.providerId.value,
                        wasPerspectiveCorrected = recognized.wasPerspectiveCorrected,
                        correctionCount = recognized.corrections.size,
                    )
                }
            } catch (e: Exception) {
                imageStore.delete(file)
                _uiState.update {
                    it.copy(
                        phase = CapturePhase.CAMERA,
                        errorMessage = e.message ?: "Metin tanınamadı",
                    )
                }
            }
        }
    }

    fun onCaptureFailed(message: String) {
        _uiState.update { it.copy(phase = CapturePhase.CAMERA, errorMessage = message) }
    }

    /** Tanınan metni kaydetmeden önce dinleyip kontrol etmek için. */
    fun previewAloud() {
        val text = _uiState.value.recognizedText
        val currentBook = book ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isSpeaking = true) }
            try {
                readPageAloud(currentBook, text)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Okuma başarısız") }
            } finally {
                _uiState.update { it.copy(isSpeaking = false) }
            }
        }
    }

    fun stopSpeaking() {
        previewJob?.cancel()
        readPageAloud.stop()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    fun savePage(onSaved: () -> Unit) {
        val state = _uiState.value
        val imagePath = state.pendingImagePath ?: return

        viewModelScope.launch {
            stopSpeaking()
            _uiState.update { it.copy(phase = CapturePhase.SAVING) }
            try {
                bookRepository.addPage(
                    bookId = bookId,
                    imagePath = imagePath,
                    rawOcrText = state.rawText,
                    cleanedText = state.recognizedText,
                    ocrProviderUsed = state.ocrProviderName?.let(::ProviderId),
                    confidenceScore = state.confidence,
                )
                _uiState.update {
                    it.copy(
                        phase = CapturePhase.CAMERA,
                        pendingImagePath = null,
                        recognizedText = "",
                        rawText = "",
                        confidence = null,
                        savedPageCount = it.savedPageCount + 1,
                    )
                }
                onSaved()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(phase = CapturePhase.REVIEW, errorMessage = e.message ?: "Sayfa kaydedilemedi")
                }
            }
        }
    }

    /** Kullanıcı tanınan metni beğenmedi: fotoğrafı sil, kameraya dön. */
    fun retake() {
        val path = _uiState.value.pendingImagePath
        viewModelScope.launch {
            stopSpeaking()
            path?.let { imageStore.delete(File(it)) }
            _uiState.update {
                it.copy(
                    phase = CapturePhase.CAMERA,
                    pendingImagePath = null,
                    recognizedText = "",
                    rawText = "",
                    confidence = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun updateRecognizedText(text: String) = _uiState.update { it.copy(recognizedText = text) }

    fun newImageFile(): File = imageStore.newPageImageFile(bookId)

    override fun onCleared() {
        readPageAloud.stop()
        super.onCleared()
    }
}

enum class CapturePhase { CAMERA, RECOGNIZING, REVIEW, SAVING }

data class CaptureUiState(
    val bookTitle: String = "",
    val phase: CapturePhase = CapturePhase.CAMERA,
    val pendingImagePath: String? = null,
    val recognizedText: String = "",
    val rawText: String = "",
    val confidence: Float? = null,
    val ocrProviderName: String? = null,
    val wasPerspectiveCorrected: Boolean = false,
    val correctionCount: Int = 0,
    val savedPageCount: Int = 0,
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null,
)
