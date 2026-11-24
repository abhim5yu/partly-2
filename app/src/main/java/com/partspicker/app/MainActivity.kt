package com.partspicker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { PartsPickerApp() }
    }
}

@Composable
fun PartsPickerApp(vm: PartsViewModel = viewModel()) {
    AppTheme(vm.themeMode) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()

        BackHandler(enabled = showSettings) { showSettings = false }

        if (showSettings) {
            SettingsScreen(vm, onBack = { showSettings = false })
        } else {
            MainScreen(vm, listState, onOpenSettings = { showSettings = true })
        }

        AppSheets(vm)
    }
}
