package com.kartal.seslikitap.ui.newbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.repository.BookRepository
import com.kartal.seslikitap.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Kitap Bilgisi" adımı (plan Bölüm 4.5): çocuk kitabı tikbox'ı + anlatıcı cinsiyeti.
 * Kullanıcı bir şey seçmezse ayarlardaki varsayılanlar kullanılır.
 */
@HiltViewModel
class NewBookViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewBookUiState())
    val uiState: StateFlow<NewBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            _uiState.update { it.copy(narratorGender = settings.defaultNarratorGender) }
        }
    }

    fun onTitleChange(title: String) = _uiState.update { it.copy(title = title) }

    fun onChildrenBookChange(isChildrenBook: Boolean) =
        _uiState.update { it.copy(isChildrenBook = isChildrenBook) }

    fun onNarratorGenderChange(gender: NarratorGender) =
        _uiState.update { it.copy(narratorGender = gender) }

    fun createBook(onCreated: (String) -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val book = bookRepository.createBook(
                    title = state.title.ifBlank { DEFAULT_TITLE },
                    isChildrenBook = state.isChildrenBook,
                    narratorGender = state.narratorGender,
                )
                onCreated(book.id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "Kitap oluşturulamadı", isSaving = false)
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_TITLE = "İsimsiz kitap"
    }
}

data class NewBookUiState(
    val title: String = "",
    val isChildrenBook: Boolean = false,
    val narratorGender: NarratorGender = NarratorGender.NEUTRAL,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)
