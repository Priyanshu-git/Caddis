package app.caddis.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.caddis.ui.Motion
import app.caddis.ui.components.CIcons
import app.caddis.ui.nav.navigateToTab
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType

internal enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", CIcons.House),
    LIBRARY("library", "Library", CIcons.Library),
    PROFILE("profile", "Profile", CIcons.User),
}

internal fun NavHostController.navigateToTab(t: Tab) = navigateToTab(t.route)

@Composable
internal fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = C.colors
    Column(Modifier.background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                val iconTint by animateColorAsState(if (on) c.accent else c.fg2, tween(Motion.Quick), label = "tabIcon")
                val labelTint by animateColorAsState(if (on) c.fg else c.fg2, tween(Motion.Quick), label = "tabLabel")
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .clickable(role = Role.Tab) { onSelect(t) }
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
