package com.kartal.seslikitap.ui.pdfimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onFinished: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PdfImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::onPdfSelected) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bookTitle.ifBlank { "PDF içe aktar" }) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Geri") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "PDF'in her sayfası görüntüye çevrilip aynı metin tanıma akışından geçirilir. " +
                    "Taranmış PDF'ler de çalışır.",
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedButton(
                onClick = { pickPdf.launch(arrayOf(PDF_MIME_TYPE)) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.selectedUri == null) "PDF seç" else "Başka PDF seç")
            }

            if (state.pdfPageCount > 0) {
                Card {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Seçilen PDF: ${state.pdfPageCount} sayfa")
                        Text(
                            "Uzun kitaplarda bu işlem sürebilir; ekranda kalman gerekir.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.isImporting || state.isFinished) {
                ImportProgress(state)
            }

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::startImport,
                    enabled = state.selectedUri != null && !state.isImporting && !state.isFinished,
                ) {
                    Text("İçe aktarmayı başlat")
                }
                if (state.isImporting) {
                    OutlinedButton(onClick = viewModel::cancelImport) { Text("Durdur") }
                }
                if (state.isFinished) {
                    Button(onClick = { onFinished(viewModel.bookId) }) { Text("Okumaya geç") }
                }
            }
        }
    }
}

@Composable
private fun ImportProgress(state: PdfImportUiState) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.isFinished) "İçe aktarma tamamlandı" else "Sayfa ${state.currentPage} / ${state.totalPages}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (!state.isFinished) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Eklenen sayfa: ${state.importedPages}")
            if (state.failedPages > 0) {
                Text(
                    "Atlanan sayfa: ${state.failedPages} (boş sayfa veya okunamayan içerik)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val PDF_MIME_TYPE = "application/pdf"
