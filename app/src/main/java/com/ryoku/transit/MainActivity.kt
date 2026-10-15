package com.ryoku.transit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.ryoku.transit.ui.RyokuApp
import com.ryoku.transit.ui.ThemeMode

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onResume() { super.onResume(); vm.refreshPending(); vm.reloadLearning() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Iconos de las barras del sistema acordes al tema (claros sobre Grafito, oscuros sobre Washi).
            LaunchedEffect(vm.theme) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (vm.theme == ThemeMode.WASHI) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            RyokuApp(vm)
        }
    }
}
