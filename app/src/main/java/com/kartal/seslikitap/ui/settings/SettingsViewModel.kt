package com.kartal.seslikitap.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kartal.seslikitap.domain.correction.TextCorrectionRegistry
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.model.UserSettings
import com.kartal.seslikitap.domain.provider.OcrProviderRegistry
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.domain.provider.TtsProviderRegistry
import com.kartal.seslikitap.domain.provider.VoiceMappingResolver
import com.kartal.seslikitap.domain.repository.SettingsRepository
import com.kartal.seslikitap.domain.security.ApiKeyStore
import com.kartal.seslikitap.domain.security.CredentialField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val voiceMappingResolvers: Set<@JvmSuppressWildcards VoiceMappingResolver>,
    ocrRegistry: OcrProviderRegistry,
    ttsRegistry: TtsProviderRegistry,
    correctionRegistry: TextCorrectionRegistry,
) : ViewModel() {

    private val transientState = MutableStateFlow(TransientState())

    private val ocrProviders = ocrRegistry.all().map {
        ProviderRow(it.id, it.name, it.isOnDevice, it.requiresApiKey, it.credentialFields)
    }
    private val ttsProviders = ttsRegistry.all().map {
        ProviderRow(it.id, it.name, it.isOnDevice, it.requiresApiKey, it.credentialFields)
    }
    private val correctionProviders = correctionRegistry.all().map {
        ProviderRow(it.id, it.name, it.isOnDevice, it.requiresApiKey, it.credentialFields)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        apiKeyStore.observeStoredFields(),
        transientState,
    ) { settings, storedFields, transient ->
        SettingsUiState(
            settings = settings,
            ocrProviders = ocrProviders,
            ttsProviders = ttsProviders,
            correctionProviders = correctionProviders,
            storedFields = storedFields,
            drafts = transient.drafts,
            message = transient.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun selectOcrProvider(id: ProviderId) = update { it.copy(defaultOcrProviderId = id) }

    fun selectTtsProvider(id: ProviderId) = update { it.copy(defaultTtsProviderId = id) }

    fun selectCorrectionProvider(id: ProviderId) = update { it.copy(textCorrectionProviderId = id) }

    fun selectNarratorGender(gender: NarratorGender) =
        update { it.copy(defaultNarratorGender = gender) }

    fun setPlaybackSpeed(speed: Float) = update { it.copy(playbackSpeed = speed) }

    fun setPitch(pitch: Float) = update { it.copy(pitch = pitch) }

    fun setAutoFallback(enabled: Boolean) = update { it.copy(autoFallbackToCloud = enabled) }

    fun setFallbackThreshold(threshold: Float) =
        update { it.copy(cloudFallbackConfidenceThreshold = threshold) }

    fun setLanguageTag(tag: String) =
        update { it.copy(languageTag = tag.trim().ifBlank { null }) }

    fun onDraftChange(providerId: ProviderId, fieldId: String, value: String) =
        transientState.update {
            it.copy(drafts = it.drafts + (DraftKey(providerId, fieldId) to value))
        }

    /** Sağlayıcının doldurulmuş tüm taslak alanlarını kaydeder. */
    fun saveCredentials(provider: ProviderRow) {
        val drafts = transientState.value.drafts
        val pending = provider.credentialFields
            .mapNotNull { field ->
                drafts[DraftKey(provider.id, field.id)]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { field.id to it }
            }
        if (pending.isEmpty()) return

        viewModelScope.launch {
            pending.forEach { (fieldId, value) -> apiKeyStore.setCredential(provider.id, fieldId, value) }
            invalidateVoiceCaches(provider.id)
            transientState.update { state ->
                state.copy(
                    drafts = state.drafts - pending.map { DraftKey(provider.id, it.first) }.toSet(),
                    message = "Bilgiler cihazda şifreli olarak kaydedildi",
                )
            }
        }
    }

    fun clearCredentials(providerId: ProviderId) {
        viewModelScope.launch {
            apiKeyStore.clearCredentials(providerId)
            invalidateVoiceCaches(providerId)
            transientState.update { it.copy(message = "Bilgiler silindi") }
        }
    }

    fun consumeMessage() = transientState.update { it.copy(message = null) }

    /** Anahtarı değişen sağlayıcının önbelleğe alınmış ses listesi geçersizdir. */
    private suspend fun invalidateVoiceCaches(providerId: ProviderId) {
        voiceMappingResolvers
            .filter { it.providerId == providerId }
            .forEach { it.invalidateCache() }
    }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(transform) }
    }

    private data class TransientState(
        val drafts: Map<DraftKey, String> = emptyMap(),
        val message: String? = null,
    )
}

data class DraftKey(val providerId: ProviderId, val fieldId: String)

data class SettingsUiState(
    val settings: UserSettings = UserSettings.Default,
    val ocrProviders: List<ProviderRow> = emptyList(),
    val ttsProviders: List<ProviderRow> = emptyList(),
    val correctionProviders: List<ProviderRow> = emptyList(),
    val storedFields: Map<ProviderId, Set<String>> = emptyMap(),
    val drafts: Map<DraftKey, String> = emptyMap(),
    val message: String? = null,
) {
    /** Kimlik bilgisi isteyen sağlayıcılar; ayarlar ekranı alanları bu listeden üretir. */
    val credentialProviders: List<ProviderRow>
        get() = (ocrProviders + ttsProviders + correctionProviders)
            .filter { it.credentialFields.isNotEmpty() }
            .distinctBy { it.id }

    /** Sağlayıcı, gerekli **tüm** alanları dolu olduğunda kullanılabilir sayılır. */
    fun isConfigured(provider: ProviderRow): Boolean {
        if (provider.credentialFields.isEmpty()) return true
        val stored = storedFields[provider.id].orEmpty()
        return provider.credentialFields.all { it.id in stored }
    }

    fun hasAnyStoredField(provider: ProviderRow): Boolean =
        storedFields[provider.id].orEmpty().isNotEmpty()
}

data class ProviderRow(
    val id: ProviderId,
    val name: String,
    val isOnDevice: Boolean,
    val requiresApiKey: Boolean,
    val credentialFields: List<CredentialField>,
)
