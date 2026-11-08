package app.caddis

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContentScope
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import app.caddis.ui.LocalDestinationScope
import app.caddis.ui.LocalOpenedItem
import app.caddis.ui.LocalSharedTransitionScope
import app.caddis.ui.Motion
import app.caddis.ui.OpenedItem
import app.caddis.ui.navMotion
import app.caddis.ui.pressScale
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.caddis.data.SettingsRepository
import app.caddis.domain.model.Item
import app.caddis.ui.Messenger
import app.caddis.ui.Toast
import app.caddis.ui.capture.CaptureNav
import app.caddis.ui.capture.CaptureSheetHost
import app.caddis.ui.capture.CaptureViewModel
import app.caddis.ui.components.CIcons
import app.caddis.ui.home.HomeScreen
import app.caddis.ui.item.ItemScreen
import app.caddis.ui.item.NoteScreen
import app.caddis.ui.library.LibraryScreen
import app.caddis.ui.library.ShelfScreen
import app.caddis.ui.manage.CollectionsScreen
import app.caddis.ui.manage.TidyScreen
import app.caddis.ui.manage.TrashScreen
import app.caddis.ui.profile.AccountScreen
import app.caddis.ui.profile.DataScreen
import app.caddis.ui.profile.IntentsScreen
import app.caddis.ui.profile.NotificationSettingsScreen
import app.caddis.ui.profile.OnboardingScreen
import app.caddis.ui.profile.PrivacyScreen
import app.caddis.ui.profile.ProfileScreen
import app.caddis.ui.profile.RulesScreen
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisTheme
import app.caddis.ui.theme.CaddisType
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, val messenger: Messenger) : ViewModel() {
    /** null until DataStore answers, so the first frame never flashes the wrong screen. */
    val onboarded: StateFlow<Boolean?> = settings.settings.map { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pendingRoute = MutableStateFlow<String?>(null)
}

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
        setContent {
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
                            true -> CaddisApp(main, capture)
                        }
                    }
                }
            }
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

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", CIcons.House),
    LIBRARY("library", "Library", CIcons.Library),
    PROFILE("profile", "Profile", CIcons.User),
}

@Composable
private fun CaddisApp(main: MainViewModel, capture: CaptureViewModel) {
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
        val fabSource = remember { MutableInteractionSource() }
        AnimatedVisibility(
            visible = showFab,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 84.dp),
            enter = fadeIn(tween(Motion.Short, easing = Motion.Decelerate)) + scaleIn(tween(Motion.Short, easing = Motion.Decelerate), initialScale = 0.8f),
            exit = fadeOut(tween(Motion.Quick)) + scaleOut(tween(Motion.Quick), targetScale = 0.8f),
        ) {
            Box(
                Modifier
                    .pressScale(fabSource, 0.92f)
                    .size(56.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(C.colors.accent)
                    .clickable(interactionSource = fabSource, indication = LocalIndication.current, role = Role.Button) { capture.openAdd(clipboard.getText()?.text) }
                    .semantics { contentDescription = "Add a link or note" },
                contentAlignment = Alignment.Center,
            ) { Icon(CIcons.Plus, null, tint = C.colors.onAccent, modifier = Modifier.size(24.dp)) }
        }
        val toastBottom by animateDpAsState(if (showBar) 84.dp else 20.dp, tween(Motion.Medium, easing = Motion.Standard), label = "toast")
        ToastHost(main.messenger, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = toastBottom))
        CaptureSheetHost(capture)
    }
}

/** The shared-element layer sits around the host, so a thumbnail can fly between two destinations. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CaddisNavHost(nav: NavHostController, opened: OpenedItem, openItem: (Item, String) -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            CaddisNavGraph(nav, opened, openItem)
        }
    }
}

@Composable
private fun CaddisNavGraph(nav: NavHostController, opened: OpenedItem, openItem: (Item, String) -> Unit) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        nav,
        startDestination = HOME_ROUTE,
        enterTransition = { Motion.enter(navMotion(initialState.destination.route, targetState.destination.route, pop = false)) },
        exitTransition = { Motion.exit(navMotion(initialState.destination.route, targetState.destination.route, pop = false)) },
        popEnterTransition = { Motion.enter(navMotion(initialState.destination.route, targetState.destination.route, pop = true)) },
        popExitTransition = { Motion.exit(navMotion(initialState.destination.route, targetState.destination.route, pop = true)) },
    ) {
        screen(HOME_ROUTE, arguments = listOf(navArgument("week") { type = NavType.BoolType; defaultValue = false })) { e ->
            HomeScreen(
                onOpenItem = { opened.set("home", it); nav.navigate("item/${it.id}") },
                onLibrary = { nav.navigateToTab("library") },
                onTidy = { nav.navigate("tidy") },
                scrollToWeek = e.arguments?.getBoolean("week") == true,
            )
        }
        screen("library") {
            LibraryScreen(
                onOpenItem = { openItem(it, "library") },
                onTrash = { nav.navigate("trash") },
                onIntents = { nav.navigate("intents") },
                onCollection = { nav.navigate("shelf/$it") },
                onManageCollections = { nav.navigate("collections") },
            )
        }
        composable("profile") { ProfileScreen(navigate = { nav.navigate(it) }) }
        screen("item/{id}") {
            ItemScreen(onBack = back, onNote = { nav.navigate("note/$it") }, onManageIntents = { nav.navigate("intents") })
        }
        composable("note/{id}") { NoteScreen(onBack = back, onManageIntents = { nav.navigate("intents") }) }
        screen("shelf/{collectionId}") { ShelfScreen(onBack = back, onOpenItem = { openItem(it, "shelf") }) }
        composable("collections") { CollectionsScreen(onBack = back, onOpen = { nav.navigate("shelf/$it") }) }
        composable("tidy") { TidyScreen(onBack = back, onLibrary = { nav.navigateToTab("library") }) }
        composable("trash") { TrashScreen(onBack = back) }
        composable("notifications") { NotificationSettingsScreen(onBack = back) }
        composable("rules") { RulesScreen(onBack = back) }
        composable("intents") { IntentsScreen(onBack = back) }
        composable("data") { DataScreen(onBack = back) }
        composable("account") { AccountScreen(onBack = back, onDeleted = { nav.navigate("home") { popUpTo(0) } }) }
        composable("privacy") { PrivacyScreen(onBack = back) }
    }
}

/** A destination that can take part in shared-element transitions: it tells [sharedThumb] which scope it is drawn in. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalDestinationScope provides this) { content(entry) }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
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

/** The prototype's dark toast with an optional Undo, shown for 5 seconds. */
@Composable
private fun ToastHost(messenger: Messenger, modifier: Modifier) {
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

internal const val HOME_ROUTE = "home?week={week}"

/**
 * Bottom-bar navigation. Leaving a tab saves its stack so coming back restores it. Home is the start
 * destination, and a non-inclusive pop saves the popped stack under Home's id too, so restoring
 * state on the way to Home would bring the tab you just left back. Home therefore never restores.
 */
internal fun NavHostController.navigateToTab(route: String) {
    val toHome = route.startsWith("home")
    navigate(route) {
        popUpTo(HOME_ROUTE) { saveState = true }
        launchSingleTop = true
        restoreState = !toHome
    }
}

private fun NavHostController.navigateToTab(t: Tab) = navigateToTab(t.route)
