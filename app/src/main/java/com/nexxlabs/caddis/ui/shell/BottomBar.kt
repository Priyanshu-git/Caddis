package com.nexxlabs.caddis.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.components.CIcons
import com.nexxlabs.caddis.ui.nav.navigateToTab
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType

internal enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", CIcons.House),
    LIBRARY("library", "Library", CIcons.Library),
    SETTINGS("settings", "Settings", CIcons.Gear),
}

internal fun NavHostController.navigateToTab(t: Tab) = navigateToTab(t.route)

@Composable
internal fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = C.colors
    // One pill slides behind the tabs, so the active mark travels instead of switching off and on.
    val slot by animateFloatAsState(current.ordinal.toFloat(), tween(PILL_MS, easing = PillEasing), label = "tabPill")
    Column(Modifier.background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(color = c.line)
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .fillMaxWidth(1f / Tab.entries.size)
                        .fillMaxHeight()
                        .graphicsLayer { translationX = slot * size.width }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .background(c.accentTint, RoundedCornerShape(12.dp)),
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    val on = t == current
                    val iconTint by animateColorAsState(if (on) c.accent else c.fg2, tween(Motion.Short), label = "tabIcon")
                    val labelTint by animateColorAsState(if (on) c.fg else c.fg2, tween(Motion.Short), label = "tabLabel")
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            // No ripple: the active/inactive change is the feedback.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onSelect(t) }
                            .semantics { selected = on },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Icon(t.icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                        Text(t.label, style = CaddisType.tag.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = labelTint)
                    }
                }
            }
        }
    }
}

private const val PILL_MS = 380
private val PillEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
