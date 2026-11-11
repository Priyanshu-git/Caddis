package com.nexxlabs.caddis.ui.nav

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.ui.LocalDestinationScope
import com.nexxlabs.caddis.ui.LocalSharedTransitionScope
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.OpenedItem
import com.nexxlabs.caddis.ui.home.HomeScreen
import com.nexxlabs.caddis.ui.item.ItemScreen
import com.nexxlabs.caddis.ui.item.NoteScreen
import com.nexxlabs.caddis.ui.library.LibraryScreen
import com.nexxlabs.caddis.ui.library.ShelfScreen
import com.nexxlabs.caddis.ui.manage.CollectionsScreen
import com.nexxlabs.caddis.ui.manage.TidyScreen
import com.nexxlabs.caddis.ui.manage.TrashScreen
import com.nexxlabs.caddis.ui.navMotion
import com.nexxlabs.caddis.ui.profile.AccountScreen
import com.nexxlabs.caddis.ui.profile.DataScreen
import com.nexxlabs.caddis.ui.profile.IntentsScreen
import com.nexxlabs.caddis.ui.profile.NotificationSettingsScreen
import com.nexxlabs.caddis.ui.profile.PrivacyScreen
import com.nexxlabs.caddis.ui.profile.ProfileScreen
import com.nexxlabs.caddis.ui.profile.RulesScreen

internal const val HOME_ROUTE = "home?week={week}"

/** The shared-element layer sits around the host, so a thumbnail can fly between two destinations. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun CaddisNavHost(nav: NavHostController, opened: OpenedItem, openItem: (Item, String) -> Unit) {
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

/** A destination that can take part in shared-element transitions: it tells `sharedThumb` which scope it is drawn in. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalDestinationScope provides this) { content(entry) }
}

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
