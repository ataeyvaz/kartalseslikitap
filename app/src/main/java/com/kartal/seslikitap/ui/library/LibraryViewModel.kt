package com.kartal.seslikitap.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.provider.OcrProviderRegistry
import com.kartal.seslikitap.domain.provider.TtsProviderRegistry
import com.kartal.seslikitap.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    ocrRegistry: OcrProviderRegistry,
    ttsRegistry: TtsProviderRegistry,
) : ViewModel() {

    val activeProviders: String =
        "OCR: ${ocrRegistry.defaultOnDevice().name} · Ses: ${ttsRegistry.defaultOnDevice().name}"

    val books: StateFlow<List<Book>> = bookRepository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteBook(bookId: String) {
        viewModelScope.launch { bookRepository.deleteBook(bookId) }
    }
}
