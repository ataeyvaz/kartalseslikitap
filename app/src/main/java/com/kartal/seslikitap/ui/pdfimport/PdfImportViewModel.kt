package com.kartal.seslikitap.ui.pdfimport

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.domain.repository.BookRepository
import com.kartal.seslikitap.domain.usecase.ImportPdfUseCase
import com.kartal.seslikitap.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importPdf: ImportPdfUseCase,
    bookRepository: BookRepository,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _uiState = MutableStateFlow(PdfImportUiState())
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            val title = bookRepository.getBook(bookId)?.title.orEmpty()
            _uiState.update { it.copy(bookTitle = title) }
        }
    }

    fun onPdfSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedUri = uri, errorMessage = null) }
            val count = runCatching { importPdf.pageCount(uri) }.getOrElse { error ->
                _uiState.update { it.copy(errorMessage = error.message ?: "PDF okunamadı") }
                return@launch
            }
            _uiState.update { it.copy(pdfPageCount = count) }
        }
    }

    fun startImport() {
        val uri = _uiState.value.selectedUri ?: return
        if (importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, errorMessage = null) }
            try {
                importPdf(uri, bookId).collect { progress ->
                    _uiState.update {
                        it.copy(
                            currentPage = progress.currentPage,
                            totalPages = progress.totalPages,
                            importedPages = progress.importedPages,
                            failedPages = progress.failedPages,
                            isFinished = progress.isFinished,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "İçe aktarma başarısız") }
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    /** İptal: akışın toplanması durunca sayfa üretimi de durur, o ana dek eklenenler kalır. */
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _uiState.update { it.copy(isImporting = false) }
    }

    override fun onCleared() {
        importJob?.cancel()
        super.onCleared()
    }
}

data class PdfImportUiState(
    val bookTitle: String = "",
    val selectedUri: Uri? = null,
    val pdfPageCount: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val importedPages: Int = 0,
    val failedPages: Int = 0,
    val isImporting: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (totalPages <= 0) 0f else (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
}
