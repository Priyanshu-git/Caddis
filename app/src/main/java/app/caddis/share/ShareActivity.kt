package app.caddis.share

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.caddis.MainActivity
import app.caddis.ui.capture.CaptureNav
import app.caddis.ui.capture.CaptureSheet
import app.caddis.ui.capture.CaptureSheetHost
import app.caddis.ui.capture.CaptureViewModel
import app.caddis.ui.theme.CaddisTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * CAP-01: the share-sheet target. The save is written to Room in onCreate, before the sheet draws,
 * so it survives the person swiping away immediately (CAP-02) or the process dying.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val ok = intent?.action == Intent.ACTION_SEND && vm.fromShare(
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE),
                sourceApp = sourceAppLabel(),
            )
            if (!ok) {
                Toast.makeText(this, "Nothing to save in what was shared.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
        setContent {
            CaddisTheme {
                val sheet by vm.sheet.collectAsState()
                var opened by remember { mutableStateOf(false) }
                LaunchedEffect(sheet) {
                    if (sheet != CaptureSheet.None) opened = true else if (opened) finish()
                }
                LaunchedEffect(Unit) {
                    vm.nav.collect { n ->
                        val route = when (n) {
                            is CaptureNav.OpenItem -> MainActivity.ROUTE_ITEM_PREFIX + n.id
                            is CaptureNav.OpenNote -> "note/" + n.id
                        }
                        startActivity(Intent(this@ShareActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route))
                        finish()
                    }
                }
                CaptureSheetHost(vm)
            }
        }
    }

    /** CAP-07: the source app when Android tells us (the referrer), e.g. "WhatsApp". */
    private fun sourceAppLabel(): String {
        val pkg = referrer?.takeIf { it.scheme == "android-app" }?.host
            ?: if (Build.VERSION.SDK_INT >= 34) launchedFromPackage else null
        if (pkg == null || pkg == packageName) return ""
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }
}
