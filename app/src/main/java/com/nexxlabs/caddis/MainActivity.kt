package com.nexxlabs.caddis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.capture.CaptureViewModel
import com.nexxlabs.caddis.ui.shell.CaddisRoot
import dagger.hilt.android.AndroidEntryPoint

/** Hosts the app: splash, edge-to-edge, and the route handed in by an intent. The UI lives in `ui.shell`, navigation in `ui.nav`. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val main: MainViewModel by viewModels()
    private val capture: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hold the splash until DataStore answers, so the first frame is already the right screen.
        splash.setKeepOnScreenCondition { main.onboarded.value == null }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(Motion.Short.toLong())
                .withEndAction { provider.remove() }
                .start()
        }
        if (savedInstanceState == null) main.pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent { CaddisRoot(main, capture) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { main.pendingRoute.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_ITEM_PREFIX = "item/"
        const val ROUTE_WEEK = "home?week=true"
    }
}
