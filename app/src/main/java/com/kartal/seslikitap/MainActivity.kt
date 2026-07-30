package com.kartal.seslikitap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kartal.seslikitap.ui.navigation.KartalNavHost
import com.kartal.seslikitap.ui.theme.KartalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KartalTheme {
                KartalNavHost()
            }
        }
    }
}
