package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CircuitViewModel
import com.example.ui.MainWorkbenchScreen
import com.example.ui.theme.VoltLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: CircuitViewModel = viewModel()
            val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            VoltLabTheme(darkTheme = isDark) {
                MainWorkbenchScreen(viewModel = viewModel)
            }
        }
    }
}
