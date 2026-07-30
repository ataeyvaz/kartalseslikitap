package com.kartal.seslikitap.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.Page
import com.kartal.seslikitap.domain.model.ReadingProgress
import com.kartal.seslikitap.domain.repository.BookRepository
import com.kartal.seslikitap.domain.usecase.ReadPageAloudUseCase
import com.kartal.seslikitap.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Oku" adımı: kaydedilmiş sayfaları sırayla seslendirir ve ilerlemeyi Room'a yazar.
 *
 * Bir sayfa bitince otomatik olarak sonrakine geçer; böylece kitap kesintisiz dinlenir.
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookRepository: BookRepository,
    private val readPageAloud: ReadPageAloudUseCase,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val currentPageIndex = MutableStateFlow(0)
    private val playbackState = MutableStateFlow(PlaybackState())
    private val bookState = MutableStateFlow<Book?>(null)

    private val book: Book? get() = bookState.value
    private var playbackJob: Job? = null

    val uiState: StateFlow<ReaderUiState> = combine(
        bookRepository.observePages(bookId),
        currentPageIndex,
        playbackState,
        bookState,
    ) { pages, index, playback, book ->
        val safeIndex = index.coerceIn(0, maxOf(0, pages.lastIndex))
        ReaderUiState(
            bookTitle = book?.title.orEmpty(),
            pages = pages,
            currentIndex = safeIndex,
            currentPage = pages.getOrNull(safeIndex),
            isPlaying = playback.isPlaying,
            errorMessage = playback.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    init {
        viewModelScope.launch {
            bookState.value = bookRepository.getBook(bookId)
            // Kaldığı yerden devam: kayıtlı ilerleme varsa o sayfaya konumlan.
            val progress = bookRepository.observeProgress(bookId).first()
            val pages = bookRepository.observePages(bookId).first()
            val savedIndex = pages.indexOfFirst { it.id == progress?.currentPageId }
            if (savedIndex >= 0) currentPageIndex.value = savedIndex
        }
    }

    fun playFromCurrentPage() {
        val currentBook = book ?: return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            playbackState.update { it.copy(isPlaying = true, errorMessage = null) }
            try {
                val pages = bookRepository.observePages(bookId).first()
                var index = currentPageIndex.value
                while (index <= pages.lastIndex) {
                    val page = pages[index]
                    currentPageIndex.value = index
                    saveProgress(page)
                    readPageAloud(currentBook, page.cleanedText)
                    index++
                }
            } catch (e: Exception) {
                playbackState.update { it.copy(errorMessage = e.message ?: "Okuma başarısız") }
            } finally {
                playbackState.update { it.copy(isPlaying = false) }
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        readPageAloud.stop()
        playbackState.update { it.copy(isPlaying = false) }
    }

    fun goToPage(index: Int) {
        val wasPlaying = playbackState.value.isPlaying
        stop()
        viewModelScope.launch {
            val lastIndex = bookRepository.observePages(bookId).first().lastIndex
            currentPageIndex.value = index.coerceIn(0, maxOf(0, lastIndex))
            if (wasPlaying) playFromCurrentPage()
        }
    }

    fun next() = goToPage(currentPageIndex.value + 1)

    fun previous() = goToPage((currentPageIndex.value - 1).coerceAtLeast(0))

    private suspend fun saveProgress(page: Page) {
        bookRepository.saveProgress(
            ReadingProgress(
                bookId = bookId,
                currentPageId = page.id,
                currentAudioPositionMillis = 0L,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override fun onCleared() {
        readPageAloud.stop()
        super.onCleared()
    }

    private data class PlaybackState(
        val isPlaying: Boolean = false,
        val errorMessage: String? = null,
    )
}

data class ReaderUiState(
    val bookTitle: String = "",
    val pages: List<Page> = emptyList(),
    val currentIndex: Int = 0,
    val currentPage: Page? = null,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null,
)
