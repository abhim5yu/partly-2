package com.partspicker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private lateinit var vm: PartsViewModel

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { vm.exportBackup(it) }
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importBackup(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: PartsViewModel = viewModel()
            vm = viewModel
            PartsPickerApp(
                vm = viewModel,
                onExportBackup = { exportLauncher.launch("parts-picker-backup.json") },
                onImportBackup = { importLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}

@Composable
fun PartsPickerApp(
    vm: PartsViewModel = viewModel(),
    onExportBackup: () -> Unit = {},
    onImportBackup: () -> Unit = {}
) {
    AppTheme(vm.themeMode) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showSheets by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()

        BackHandler(enabled = showSettings || showSheets) {
            showSettings = false
            showSheets = false
        }

        when {
            showSettings -> SettingsScreen(vm, onBack = { showSettings = false }, onExportBackup, onImportBackup)
            showSheets -> SheetsScreen(vm, onBack = { showSheets = false })
            else -> MainScreen(
                vm,
                listState,
                onOpenSheets = { showSheets = true },
                onOpenSettings = { showSettings = true }
            )
        }

        AppSheets(vm)
    }
}
