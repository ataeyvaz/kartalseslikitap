package com.kartal.seslikitap.ui.newbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kartal.seslikitap.domain.model.NarratorGender
import com.kartal.seslikitap.ui.library.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBookScreen(
    onCreatedForCapture: (String) -> Unit,
    onCreatedForPdfImport: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewBookViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitap bilgisi") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Kitap adı") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.isChildrenBook,
                        onValueChange = viewModel::onChildrenBookChange,
                        role = Role.Checkbox,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.isChildrenBook, onCheckedChange = null)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text("Bu bir çocuk kitabı", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Okuma hızı biraz düşer, tonlama daha canlı olur.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Anlatıcı sesi", style = MaterialTheme.typography.titleMedium)
                NarratorGender.entries.forEach { gender ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.narratorGender == gender,
                                onClick = { viewModel.onNarratorGenderChange(gender) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.narratorGender == gender, onClick = null)
                        Text(gender.label(), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.createBook(onCreatedForCapture) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "Oluşturuluyor…" else "Sayfa fotoğrafı çek")
            }

            OutlinedButton(
                onClick = { viewModel.createBook(onCreatedForPdfImport) },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("PDF'ten içe aktar")
            }
        }
    }
}
