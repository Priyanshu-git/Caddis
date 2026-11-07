package app.caddis

import android.content.Intent
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) main.pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            CaddisTheme {
                val onboarded by main.onboarded.collectAsState()
                Box(Modifier.fillMaxSize().background(C.colors.bg)) {
                    when (onboarded) {
                        null -> Unit
                        false -> Box(Modifier.statusBarsPadding().navigationBarsPadding()) { OnboardingScreen(onDone = {}) }
                        true -> CaddisApp(main, capture)
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

    fun openItem(i: Item) = nav.navigate(if (i.isNote) "note/${i.id}" else "item/${i.id}")

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                CaddisNavHost(nav, ::openItem)
            }
            if (tab != null) BottomBar(tab) { t ->
                nav.navigate(t.route) {
                    popUpTo(HOME_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        // Capture is a plus button on Home and Library (Experience overview).
        if (tab == Tab.HOME || tab == Tab.LIBRARY) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 84.dp)
                    .size(56.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(C.colors.accent)
                    .clickable(role = Role.Button) { capture.openAdd(clipboard.getText()?.text) }
                    .semantics { contentDescription = "Add a link or note" },
                contentAlignment = Alignment.Center,
            ) { Icon(CIcons.Plus, null, tint = C.colors.onAccent, modifier = Modifier.size(24.dp)) }
        }
        ToastHost(main.messenger, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = if (tab != null) 84.dp else 20.dp))
        CaptureSheetHost(capture)
    }
}

@Composable
private fun CaddisNavHost(nav: NavHostController, openItem: (Item) -> Unit) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE, arguments = listOf(navArgument("week") { type = NavType.BoolType; defaultValue = false })) { e ->
            HomeScreen(
                onOpenItem = { nav.navigate("item/$it") },
                onLibrary = { nav.navigate("library") { popUpTo(HOME_ROUTE); launchSingleTop = true } },
                onTidy = { nav.navigate("tidy") },
                scrollToWeek = e.arguments?.getBoolean("week") == true,
            )
        }
        composable("library") {
            LibraryScreen(
                onOpenItem = openItem,
                onTrash = { nav.navigate("trash") },
                onIntents = { nav.navigate("intents") },
                onCollection = { nav.navigate("shelf/$it") },
                onManageCollections = { nav.navigate("collections") },
            )
        }
        composable("profile") { ProfileScreen(navigate = { nav.navigate(it) }) }
        composable("item/{id}") {
            ItemScreen(onBack = back, onNote = { nav.navigate("note/$it") }, onManageIntents = { nav.navigate("intents") })
        }
        composable("note/{id}") { NoteScreen(onBack = back, onManageIntents = { nav.navigate("intents") }) }
        composable("shelf/{collectionId}") { ShelfScreen(onBack = back, onOpenItem = openItem) }
        composable("collections") { CollectionsScreen(onBack = back, onOpen = { nav.navigate("shelf/$it") }) }
        composable("tidy") { TidyScreen(onBack = back, onLibrary = { nav.navigate("library") { popUpTo(HOME_ROUTE) } }) }
        composable("trash") { TrashScreen(onBack = back) }
        composable("notifications") { NotificationSettingsScreen(onBack = back) }
        composable("rules") { RulesScreen(onBack = back) }
        composable("intents") { IntentsScreen(onBack = back) }
        composable("data") { DataScreen(onBack = back) }
        composable("account") { AccountScreen(onBack = back, onDeleted = { nav.navigate("home") { popUpTo(0) } }) }
        composable("privacy") { PrivacyScreen(onBack = back) }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = C.colors
    Column(Modifier.background(c.bg).navigationBarsPadding()) {
        HorizontalDivider(color = c.line)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp)) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Icon(t.icon, null, tint = if (on) c.accent else c.fg2, modifier = Modifier.size(24.dp))
                    Text(t.label, style = CaddisType.tag.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = if (on) c.fg else c.fg2)
                }
            }
        }
    }
}

/** The prototype's dark toast with an optional Undo, shown for 5 seconds. */
@Composable
private fun ToastHost(messenger: Messenger, modifier: Modifier) {
    var toast by remember { mutableStateOf<Toast?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { messenger.toasts.collect { toast = it } }
    LaunchedEffect(toast?.id) {
        if (toast != null) { delay(5_000); toast = null }
    }
    val t = toast ?: return
    val c = C.colors
    Row(
        modifier
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
                    toast = null
                    scope.launch { undo() }
                }.padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

private const val HOME_ROUTE = "home?week={week}"
