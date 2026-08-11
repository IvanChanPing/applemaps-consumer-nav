package com.example.applemaps

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.example.applemaps.diag.DiagLog
import com.example.applemaps.map.ConsumerMapController
import com.example.applemaps.map.PlaceRepository
import com.example.applemaps.ui.AppleMapsScreen
import com.example.applemaps.ui.theme.AppleMapsTheme

/**
 * Host activity for the Apple-Maps-web reconstruction. Single-Activity Compose app; the whole UI
 * lives in [AppleMapsScreen].
 */
class MainActivity : ComponentActivity() {
    private lateinit var mapController: ConsumerMapController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DiagLog.init(applicationContext)
        PlaceRepository.initialize(applicationContext)
        mapController = ConsumerMapController(this)
        val root = FrameLayout(this)
        mapController.attachTo(root)
        root.addView(
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { AppleMapsTheme { AppleMapsScreen(mapController) } }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)
    }

    override fun onResume() { super.onResume(); mapController.onResume() }
    override fun onPause() { mapController.onPause(); super.onPause() }
    override fun onDestroy() { mapController.destroy(); super.onDestroy() }
}
