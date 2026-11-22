package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.Mp3tagApp
import com.example.ui.theme.Mp3tagTheme
import com.example.viewmodel.Mp3tagViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: Mp3tagViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            Mp3tagTheme(themeMode = settings.themeMode) {
                Mp3tagApp(viewModel = viewModel)
            }
        }
    }
}
