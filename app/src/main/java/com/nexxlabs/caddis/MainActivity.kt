package com.nexxlabs.caddis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.capture.CaptureViewModel
import com.nexxlabs.caddis.ui.shell.CaddisRoot
import com.nexxlabs.caddis.update.AppUpdater
import com.nexxlabs.caddis.update.UpdatePrompter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hosts the app: splash, edge-to-edge, and the route handed in by an intent. The UI lives in `ui.shell`, navigation in `ui.nav`. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val main: MainViewModel by viewModels()
    private val capture: CaptureViewModel by viewModels()

    @Inject lateinit var updater: AppUpdater
    @Inject lateinit var updatePrompter: UpdatePrompter

    /** Google Play's update dialog. The flexible flow reports through [AppUpdater.state], so the result itself is not needed. */
    private val updateDialog = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { updatePrompter.run { updater.start(updateDialog) } }
        }
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
