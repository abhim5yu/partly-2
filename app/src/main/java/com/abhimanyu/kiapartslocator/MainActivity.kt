package com.abhimanyu.kiapartslocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.abhimanyu.kiapartslocator.ui.theme.PartsPickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PartsPickerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("Parts Picker — Phase 1 skeleton")
                }
            }
        }
    }
}
