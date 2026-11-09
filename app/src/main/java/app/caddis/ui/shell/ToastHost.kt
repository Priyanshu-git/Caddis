package app.caddis.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.caddis.ui.Messenger
import app.caddis.ui.Motion
import app.caddis.ui.Toast
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The prototype's dark toast with an optional Undo, shown for 5 seconds. */
@Composable
internal fun ToastHost(messenger: Messenger, modifier: Modifier) {
    // The last toast is kept after it hides, so it has something to animate out with.
    var toast by remember { mutableStateOf<Toast?>(null) }
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { messenger.toasts.collect { toast = it; shown = true } }
    LaunchedEffect(toast?.id) {
        if (toast != null) { delay(5_000); shown = false }
    }
    val t = toast ?: return
    val c = C.colors
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(Motion.Short, easing = Motion.Decelerate)) + slideInVertically(tween(Motion.Short, easing = Motion.Decelerate)) { it / 2 },
        exit = fadeOut(tween(Motion.Quick)) + slideOutVertically(tween(Motion.Quick)) { it / 2 },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(c.fg)
                .border(0.dp, c.fg, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = t.message },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(t.message, style = CaddisType.sub, color = c.bg, modifier = Modifier.weight(1f))
            val undo = t.undo
            if (undo != null) {
                Text(
                    "Undo",
                    style = CaddisType.strong.copy(fontWeight = FontWeight.Bold),
                    color = c.toastLink,
                    modifier = Modifier.heightIn(min = 32.dp).clickable(role = Role.Button) {
                        shown = false
                        scope.launch { undo() }
                    }.padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
    }
}
