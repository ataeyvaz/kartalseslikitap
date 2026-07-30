package com.kartal.seslikitap.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onAddPage: (String) -> Unit,
    onImportPdf: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bookTitle.ifBlank { "Okuma" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
                actions = {
                    TextButton(onClick = { onImportPdf(viewModel.bookId) }) { Text("PDF") }
                    TextButton(onClick = { onAddPage(viewModel.bookId) }) { Text("Sayfa ekle") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.pages.isEmpty()) {
                EmptyPages(onAddPage = { onAddPage(viewModel.bookId) })
                return@Column
            }

            Text(
                text = "Sayfa ${state.currentIndex + 1} / ${state.pages.size}",
                style = MaterialTheme.typography.labelLarge,
            )

            state.currentPage?.let { page ->
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        page.confidenceScore?.let {
                            Text(
                                "OCR güveni: %${(it * 100).toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(page.cleanedText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = viewModel::previous,
                    enabled = state.currentIndex > 0,
                ) { Text("Önceki") }

                Button(
                    onClick = { if (state.isPlaying) viewModel.stop() else viewModel.playFromCurrentPage() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isPlaying) "Durdur" else "Oku")
                }

                OutlinedButton(
                    onClick = viewModel::next,
                    enabled = state.currentIndex < state.pages.lastIndex,
                ) { Text("Sonraki") }
            }
        }
    }
}

@Composable
private fun EmptyPages(onAddPage: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Bu kitapta henüz sayfa yok.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAddPage) { Text("Sayfa çek") }
    }
}
