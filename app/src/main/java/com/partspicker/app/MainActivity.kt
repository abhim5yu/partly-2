package com.partspicker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

class MainActivity : ComponentActivity() {
    private lateinit var vm: PartsViewModel

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { vm.exportBackup(it) }
        }
    private val exportExcelLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
            uri?.let { vm.exportExcel(it) }
        }
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { vm.importBackup(it) }
        }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        UpdateScheduler.schedule(this)
        if (Storage(this).loadNotifyUpdates()) askNotificationPermission()

        setContent {
            val viewModel: PartsViewModel = viewModel()
            vm = viewModel
            PartsPickerApp(
                vm = viewModel,
                onExportBackup = { exportLauncher.launch("parts-picker-backup.json") },
                onExportExcel = {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportExcelLauncher.launch("parts-picker-$day.xlsx")
                },
                onImportBackup = { importLauncher.launch(arrayOf("application/json")) },
                onEnableNotifications = { askNotificationPermission() }
            )
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun PartsPickerApp(
    vm: PartsViewModel = viewModel(),
    onExportBackup: () -> Unit = {},
    onExportExcel: () -> Unit = {},
    onImportBackup: () -> Unit = {},
    onEnableNotifications: () -> Unit = {}
) {
    AppTheme(vm.themeMode) {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        val listState = rememberLazyListState()

        BackHandler(enabled = showSettings) { showSettings = false }

        Box(Modifier.fillMaxSize().background(LocalAppColors.current.bg)) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (slideInHorizontally(tween(340)) { dir * it / 4 } + fadeIn(tween(340))) togetherWith
                        (slideOutHorizontally(tween(340)) { -dir * it / 4 } + fadeOut(tween(220)))
                },
                label = "screen"
            ) { settings ->
                if (settings) {
                    SettingsScreen(
                        vm,
                        onBack = { showSettings = false },
                        onExportBackup = onExportBackup,
                        onExportExcel = onExportExcel,
                        onImportBackup = onImportBackup,
                        onEnableNotifications = onEnableNotifications
                    )
                } else {
                    MainScreen(vm, listState, onOpenSettings = { showSettings = true })
                }
            }
        }

        AppSheets(vm)
    }
}
