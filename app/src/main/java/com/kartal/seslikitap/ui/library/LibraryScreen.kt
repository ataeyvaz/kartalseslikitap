package com.kartal.seslikitap.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import com.kartal.seslikitap.domain.model.Book
import com.kartal.seslikitap.domain.model.NarratorGender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAddBook: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kitaplığım") },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text("Ayarlar") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Yeni kitap") },
                icon = {},
                onClick = onAddBook,
            )
        },
    ) { innerPadding ->
        if (books.isEmpty()) {
            EmptyLibrary(
                providerInfo = viewModel.activeProviders,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = viewModel.activeProviders,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(books, key = { it.id }) { book ->
                    BookCard(book = book, onClick = { onOpenBook(book.id) })
                }
            }
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(book.narratorGender.label())
                    if (book.isChildrenBook) append(" · Çocuk kitabı")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(providerInfo: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Henüz kitap yok", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "\"Yeni kitap\" ile başla: kitap bilgisini gir, sayfaların fotoğrafını çek, " +
                "uygulama metni tanıyıp sana okusun.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(providerInfo, style = MaterialTheme.typography.labelSmall)
    }
}

fun NarratorGender.label(): String = when (this) {
    NarratorGender.FEMALE -> "Kadın ses"
    NarratorGender.MALE -> "Erkek ses"
    NarratorGender.NEUTRAL -> "Nötr ses"
}
