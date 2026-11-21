package com.nexxlabs.caddis.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexxlabs.caddis.ui.theme.C
import kotlinx.coroutines.delay

/** How long a load may take before placeholders appear. A load that beats this never shows them. */
const val PLACEHOLDER_GRACE_MS = 150L

/**
 * Greyed rows shaped like [ItemRow] (thumbnail, two lines, tag), shown while a list is still loading so
 * nothing moves when the real rows arrive. They wait [PLACEHOLDER_GRACE_MS] first, so a quick load shows
 * nothing at all, and they only pulse softly (Compose follows the system animation scale, so "remove animations" stills the pulse).
 */
@Composable
fun PlaceholderRows(count: Int = 6, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(PLACEHOLDER_GRACE_MS); show = true }
    if (!show) return
    val pulse by rememberInfiniteTransition(label = "placeholderPulse").animateFloat(
        initialValue = 1f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "placeholderAlpha",
    )
    // Decoration only: nothing here is for a screen reader to read.
    Column(modifier.clearAndSetSemantics {}.alpha(pulse)) {
        Bar(8.dp, Modifier.padding(top = 10.dp, bottom = 6.dp).width(120.dp))
        repeat(count) { i ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(C.colors.tint))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Titles differ in length a little, like real ones.
                    Bar(11.dp, Modifier.fillMaxWidth(0.9f - (i % 3) * 0.12f))
                    Bar(8.dp, Modifier.fillMaxWidth(0.5f))
                }
                Bar(18.dp, Modifier.width(44.dp), radius = 9.dp)
            }
            HorizontalDivider(color = C.colors.line)
        }
    }
}

@Composable
private fun Bar(height: Dp, modifier: Modifier, radius: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(radius)).background(C.colors.tint))
}
