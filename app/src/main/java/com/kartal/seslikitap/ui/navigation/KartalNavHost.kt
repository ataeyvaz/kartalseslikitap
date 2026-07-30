package com.kartal.seslikitap.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kartal.seslikitap.ui.capture.CaptureScreen
import com.kartal.seslikitap.ui.library.LibraryScreen
import com.kartal.seslikitap.ui.newbook.NewBookScreen
import com.kartal.seslikitap.ui.pdfimport.PdfImportScreen
import com.kartal.seslikitap.ui.reader.ReaderScreen
import com.kartal.seslikitap.ui.settings.SettingsScreen

object Routes {
    const val LIBRARY = "library"
    const val NEW_BOOK = "newBook"
    const val CAPTURE = "capture/{bookId}"
    const val READER = "reader/{bookId}"
    const val SETTINGS = "settings"
    const val PDF_IMPORT = "pdfImport/{bookId}"

    const val ARG_BOOK_ID = "bookId"

    fun capture(bookId: String) = "capture/$bookId"
    fun reader(bookId: String) = "reader/$bookId"
    fun pdfImport(bookId: String) = "pdfImport/$bookId"
}

@Composable
fun KartalNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onAddBook = { navController.navigate(Routes.NEW_BOOK) },
                onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.NEW_BOOK) {
            NewBookScreen(
                // Kitap bilgisi adımı akışın başında bir kez sorulur (plan Bölüm 4.5);
                // sonrasında kullanıcı sayfa çekmeyi veya PDF içe aktarmayı seçer.
                onCreatedForCapture = { bookId ->
                    navController.navigate(Routes.capture(bookId)) {
                        popUpTo(Routes.NEW_BOOK) { inclusive = true }
                    }
                },
                onCreatedForPdfImport = { bookId ->
                    navController.navigate(Routes.pdfImport(bookId)) {
                        popUpTo(Routes.NEW_BOOK) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CAPTURE,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) {
            CaptureScreen(
                onFinished = { bookId ->
                    navController.navigate(Routes.reader(bookId)) {
                        popUpTo(Routes.LIBRARY)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.PDF_IMPORT,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) {
            PdfImportScreen(
                onFinished = { bookId ->
                    navController.navigate(Routes.reader(bookId)) {
                        popUpTo(Routes.LIBRARY)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) {
            ReaderScreen(
                onAddPage = { bookId -> navController.navigate(Routes.capture(bookId)) },
                onImportPdf = { bookId -> navController.navigate(Routes.pdfImport(bookId)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
