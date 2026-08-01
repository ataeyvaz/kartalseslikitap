package com.kartal.seslikitap.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.domain.provider.ProviderId
import com.kartal.seslikitap.ui.library.label
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection("Metin tanıma (OCR)") {
                state.ocrProviders.forEach { provider ->
                    ProviderOption(
                        provider = provider,
                        selected = state.settings.defaultOcrProviderId == provider.id,
                        isConfigured = state.isConfigured(provider),
                        onSelect = { viewModel.selectOcrProvider(provider.id) },
                    )
                }
            }

            SettingsSection("Seslendirme (TTS)") {
                state.ttsProviders.forEach { provider ->
                    ProviderOption(
                        provider = provider,
                        selected = state.settings.defaultTtsProviderId == provider.id,
                        isConfigured = state.isConfigured(provider),
                        onSelect = { viewModel.selectTtsProvider(provider.id) },
                    )
                }
                Text(
                    "Bulut sesleri uzun metinlerde tonlama ve duraklamalarda belirgin biçimde " +
                        "daha doğaldır; cihaz sesi ücretsiz ve internetsiz çalışır.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // Anahtar girildikten sonra "gerçekten çalışıyor mu" sorusunun tek cevabı.
                OutlinedButton(
                    onClick = viewModel::testVoice,
                    enabled = !state.isTestingVoice,
                ) {
                    Text(if (state.isTestingVoice) "Deneniyor…" else "Sesi dene")
                }
                state.testResult?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("Başarısız")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            state.activeTtsProvider?.takeIf { !it.isOnDevice }?.let { provider ->
                SettingsSection("Ses seçimi — ${provider.name}") {
                    val pinned = state.pinnedVoiceForActiveTts
                    Text(
                        text = if (pinned == null) {
                            "Ses otomatik seçiliyor: anlatıcı cinsiyeti ve doğallık skoruna göre."
                        } else {
                            "Sabit ses: ${pinned.displayName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::loadVoices,
                            enabled = state.isConfigured(provider) && !state.isLoadingVoices,
                        ) {
                            Text(if (state.isLoadingVoices) "Yükleniyor…" else "Sesleri yükle")
                        }
                        if (pinned != null) {
                            OutlinedButton(onClick = viewModel::clearPinnedVoice) {
                                Text("Otomatiğe dön")
                            }
                        }
                    }

                    state.availableVoices.forEach { voice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = pinned?.voiceId == voice.id,
                                    onClick = { viewModel.pinVoice(voice.id, voice.displayName) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = pinned?.voiceId == voice.id, onClick = null)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(voice.displayName)
                                Text(voice.id, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Liste yüklenemese de kullanıcı kimliği elle girebilmeli.
                    OutlinedTextField(
                        value = state.voiceIdDraft,
                        onValueChange = viewModel::onVoiceIdDraftChange,
                        label = { Text("Ses kimliği (elle)") },
                        supportingText = { Text("Sağlayıcının ses sayfasındaki voiceId değeri.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = viewModel::pinVoiceFromDraft,
                        enabled = state.voiceIdDraft.isNotBlank(),
                    ) {
                        Text("Bu kimliği sabitle")
                    }
                }
            }

            SettingsSection("Metin düzeltme") {
                state.correctionProviders.forEach { provider ->
                    ProviderOption(
                        provider = provider,
                        selected = state.settings.textCorrectionProviderId == provider.id,
                        isConfigured = state.isConfigured(provider),
                        onSelect = { viewModel.selectCorrectionProvider(provider.id) },
                    )
                }
                Text(
                    "Sözlük düzelticisi yalnızca sözlükte olmayan bir kelimeyi, sözlükte " +
                        "bulunan tek bir karşılığa çevirir; metin uydurmaz. Şüpheli durumda " +
                        "kelimeye dokunmaz.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            SettingsSection("Sağlayıcı hesapları") {
                Text(
                    "Girdiğin bilgiler cihazında Android Keystore ile şifrelenir, hiçbir zaman " +
                        "bizim sunucumuza gönderilmez. İstekler doğrudan sağlayıcıya gider ve " +
                        "faturalandırma senin hesabında olur.",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.credentialProviders.forEach { provider ->
                    CredentialGroup(
                        provider = provider,
                        isConfigured = state.isConfigured(provider),
                        hasAnyStoredField = state.hasAnyStoredField(provider),
                        draftFor = { fieldId -> state.drafts[DraftKey(provider.id, fieldId)].orEmpty() },
                        isFieldStored = { fieldId -> fieldId in state.storedFields[provider.id].orEmpty() },
                        onDraftChange = { fieldId, value ->
                            viewModel.onDraftChange(provider.id, fieldId, value)
                        },
                        onSave = { viewModel.saveCredentials(provider) },
                        onClear = { viewModel.clearCredentials(provider.id) },
                    )
                }
            }

            SettingsSection("Otomatik bulut geçişi") {
                LabeledSwitch(
                    title = "Güven düşükse buluta geç",
                    description = "Cihaz üstü tanıma zayıf kaldığında sayfa bulut sağlayıcıya " +
                        "gönderilir. Bu ücretli bir çağrıdır.",
                    checked = state.settings.autoFallbackToCloud,
                    onCheckedChange = viewModel::setAutoFallback,
                )
                if (state.settings.autoFallbackToCloud) {
                    SliderRow(
                        label = "Eşik: %${(state.settings.cloudFallbackConfidenceThreshold * 100).roundToInt()}",
                        value = state.settings.cloudFallbackConfidenceThreshold,
                        range = 0.3f..0.95f,
                        onValueChange = viewModel::setFallbackThreshold,
                    )
                }
            }

            SettingsSection("Okuma varsayılanları") {
                Text("Anlatıcı sesi", style = MaterialTheme.typography.titleSmall)
                NarratorGender.entries.forEach { gender ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.settings.defaultNarratorGender == gender,
                                onClick = { viewModel.selectNarratorGender(gender) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.settings.defaultNarratorGender == gender,
                            onClick = null,
                        )
                        Text(gender.label(), modifier = Modifier.padding(start = 12.dp))
                    }
                }

                SliderRow(
                    label = "Okuma hızı: ${"%.2f".format(state.settings.playbackSpeed)}x",
                    value = state.settings.playbackSpeed,
                    range = 0.5f..2.0f,
                    onValueChange = viewModel::setPlaybackSpeed,
                )
                SliderRow(
                    label = "Ton: ${"%.2f".format(state.settings.pitch)}",
                    value = state.settings.pitch,
                    range = 0.5f..1.5f,
                    onValueChange = viewModel::setPitch,
                )

                OutlinedTextField(
                    value = state.settings.languageTag.orEmpty(),
                    onValueChange = viewModel::setLanguageTag,
                    label = { Text("Dil etiketi (örn. tr-TR)") },
                    supportingText = { Text("Boş bırakırsan cihaz dili kullanılır.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProviderOption(
    provider: ProviderRow,
    selected: Boolean,
    isConfigured: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = isConfigured,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = isConfigured)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(provider.name)
            Text(
                text = when {
                    provider.isOnDevice -> "Cihaz üzerinde · ücretsiz · internetsiz"
                    !isConfigured -> "Bulut · kullanmak için hesap bilgilerini gir"
                    else -> "Bulut · hazır · ücretli"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Bir sağlayıcının tüm kimlik bilgisi alanlarını üretir. Alan listesi sağlayıcıdan
 * geldiği için yeni sağlayıcı eklendiğinde bu ekranda değişiklik gerekmez.
 */
@Composable
private fun CredentialGroup(
    provider: ProviderRow,
    isConfigured: Boolean,
    hasAnyStoredField: Boolean,
    draftFor: (String) -> String,
    isFieldStored: (String) -> Boolean,
    onDraftChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(provider.name, style = MaterialTheme.typography.titleSmall)

        if (hasAnyStoredField && !isConfigured) {
            Text(
                "Eksik alan var; sağlayıcı kullanılamaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        provider.credentialFields.forEach { field ->
            val draft = draftFor(field.id)
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChange(field.id, it) },
                label = { Text(field.label) },
                placeholder = {
                    Text(
                        when {
                            isFieldStored(field.id) -> "Kayıtlı"
                            field.hint != null -> field.hint
                            else -> ""
                        },
                    )
                },
                singleLine = true,
                visualTransformation = if (field.isSecret) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (field.isSecret) KeyboardType.Password else KeyboardType.Uri,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSave,
                enabled = provider.credentialFields.any { draftFor(it.id).isNotBlank() },
            ) { Text("Kaydet") }
            if (hasAnyStoredField) {
                OutlinedButton(onClick = onClear) { Text("Sil") }
            }
        }
    }
}

@Composable
private fun LabeledSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
