package app.caddis.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.caddis.MainViewModel
import app.caddis.domain.model.Item
import app.caddis.ui.LocalOpenedItem
import app.caddis.ui.Motion
import app.caddis.ui.OpenedItem
import app.caddis.ui.capture.CaptureNav
import app.caddis.ui.capture.CaptureSheetHost
import app.caddis.ui.capture.CaptureViewModel
import app.caddis.ui.components.CIcons
import app.caddis.ui.nav.CaddisNavHost
import app.caddis.ui.pressScale
import app.caddis.ui.profile.OnboardingScreen
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisTheme

/** Everything inside the activity: theme, the onboarding-to-app hand-over, then the app shell. */
@Composable
fun CaddisRoot(main: MainViewModel, capture: CaptureViewModel) {
    CaddisTheme {
        val onboarded by main.onboarded.collectAsState()
        Box(Modifier.fillMaxSize().background(C.colors.bg)) {
            // Onboarding hands over to the app with a fade, not a cut.
            AnimatedContent(
                targetState = onboarded,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    fadeIn(tween(Motion.Medium, delayMillis = 60, easing = Motion.Decelerate)) togetherWith
                        fadeOut(tween(Motion.Short, easing = LinearEasing))
                },
                label = "root",
            ) { ready ->
                when (ready) {
                    null -> Unit
                    false -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) { OnboardingScreen(onDone = {}) }
                    true -> CaddisShell(main, capture)
                }
            }
        }
    }
}

/** The signed-in app: nav host, bottom bar, the add button, toasts and the capture sheets. */
@Composable
private fun CaddisShell(main: MainViewModel, capture: CaptureViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tab = Tab.entries.firstOrNull { route.startsWith(it.route) }
    val clipboard = LocalClipboardManager.current
    val pending by main.pendingRoute.collectAsState()

    LaunchedEffect(pending) {
        pending?.let { r -> nav.navigate(r) { launchSingleTop = true }; main.pendingRoute.value = null }
    }
    LaunchedEffect(Unit) {
        capture.nav.collect { n ->
            when (n) {
                is CaptureNav.OpenNote -> nav.navigate("note/${n.id}")
                is CaptureNav.OpenItem -> nav.navigate("item/${n.id}")
            }
        }
    }

    // Before the first destination exists, treat the chrome as visible so it does not animate in on launch.
    val showBar = entry == null || tab != null
    val showFab = entry == null || tab == Tab.HOME || tab == Tab.LIBRARY
    val opened = remember { OpenedItem() }
    // The bar keeps showing the tab you just left while it slides away.
    val lastTab = remember { arrayOfNulls<Tab>(1) }
    SideEffect { if (tab != null) lastTab[0] = tab }

    fun openItem(i: Item, origin: String) {
        opened.set(origin, i)
        nav.navigate(if (i.isNote) "note/${i.id}" else "item/${i.id}")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalOpenedItem provides opened) {
                    CaddisNavHost(nav, opened, ::openItem)
                }
            }
            AnimatedVisibility(
                visible = showBar,
                enter = expandVertically(tween(Motion.Medium, easing = Motion.Decelerate), expandFrom = Alignment.Top) + fadeIn(tween(Motion.Short)),
                exit = shrinkVertically(tween(Motion.Short, easing = Motion.Standard), shrinkTowards = Alignment.Top) + fadeOut(tween(Motion.Quick)),
            ) {
                BottomBar(tab ?: lastTab[0] ?: Tab.HOME) { t -> nav.navigateToTab(t) }
            }
        }
        // Capture is a plus button on Home and Library (Experience overview).
        AddButton(
            visible = showFab,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 84.dp),
            onClick = { capture.openAdd(clipboard.getText()?.text) },
        )
        val toastBottom by animateDpAsState(if (showBar) 84.dp else 20.dp, tween(Motion.Medium, easing = Motion.Standard), label = "toast")
        ToastHost(main.messenger, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = toastBottom))
        CaptureSheetHost(capture)
    }
}

@Composable
private fun AddButton(visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Motion.Short, easing = Motion.Decelerate)) + scaleIn(tween(Motion.Short, easing = Motion.Decelerate), initialScale = 0.8f),
        exit = fadeOut(tween(Motion.Quick)) + scaleOut(tween(Motion.Quick), targetScale = 0.8f),
    ) {
        Box(
            Modifier
                .pressScale(source, 0.92f)
                .size(56.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(C.colors.accent)
                .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "Add a link or note" },
            contentAlignment = Alignment.Center,
        ) { Icon(CIcons.Plus, null, tint = C.colors.onAccent, modifier = Modifier.size(24.dp)) }
    }
}
