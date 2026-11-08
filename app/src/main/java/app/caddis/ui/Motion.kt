package app.caddis.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import app.caddis.domain.model.Item

/**
 * One calm vocabulary for movement. Short, decelerating, never bouncy (Caddis is calm: no streaks,
 * no guilt, no fuss). Compose follows the system "Animator duration scale", so turning animations
 * off in Android settings makes all of this instant.
 */
object Motion {
    const val Quick = 120
    const val Short = 210
    const val Medium = 300
    /** The outgoing half of a fade-through: gone before the incoming half begins. */
    const val Out = 90

    /** Material's emphasized decelerate: things arriving. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Standard: things that move between two resting places. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Pushed screens travel a tenth of the width, not the whole screen. */
    private const val TRAVEL = 10
    private const val TAB_SCALE = 0.98f

    fun enter(m: NavMotion): EnterTransition = when (m) {
        NavMotion.TabSwitch ->
            fadeIn(tween(Short, Out, Decelerate)) + scaleIn(tween(Short, Out, Decelerate), initialScale = TAB_SCALE)
        NavMotion.Forward ->
            fadeIn(tween(Short, Out, Decelerate)) + slideInHorizontally(tween(Medium, easing = Decelerate)) { it / TRAVEL }
        NavMotion.Back ->
            fadeIn(tween(Short, 60, Decelerate)) + slideInHorizontally(tween(Medium, easing = Decelerate)) { -it / TRAVEL }
    }

    fun exit(m: NavMotion): ExitTransition = when (m) {
        NavMotion.TabSwitch -> fadeOut(tween(Out, easing = LinearEasing))
        NavMotion.Forward ->
            fadeOut(tween(Out, easing = LinearEasing)) + slideOutHorizontally(tween(Medium, easing = Standard)) { -it / TRAVEL }
        // The leaving screen of a back gesture fades over most of the drag, so it never vanishes early.
        NavMotion.Back ->
            fadeOut(tween(Short, easing = LinearEasing)) + slideOutHorizontally(tween(Medium, easing = Standard)) { it / TRAVEL }
    }
}

/** How a navigation should feel: peers fade through, going deeper slides in, going back slides out. */
enum class NavMotion { TabSwitch, Forward, Back }

internal val TAB_ROUTES = listOf("home", "library", "profile")

internal fun isTabRoute(route: String?): Boolean = route != null && TAB_ROUTES.any { route.startsWith(it) }

/**
 * Picks the motion for a move from [from] to [to]. Tabs are peers, so moving between them (or
 * arriving at one from somewhere deeper, e.g. Tidy-up → Library) fades through. Anything else is
 * a push, and a pop is its mirror.
 */
internal fun navMotion(from: String?, to: String?, pop: Boolean): NavMotion = when {
    isTabRoute(from) && isTabRoute(to) -> NavMotion.TabSwitch
    !pop && isTabRoute(to) -> NavMotion.TabSwitch
    pop -> NavMotion.Back
    else -> NavMotion.Forward
}

/** List rows glide into place, fade in when they arrive and fade out when they go. */
fun LazyItemScope.calmItem(): Modifier = Modifier.animateItem(
    fadeInSpec = tween(Motion.Short, delayMillis = 60, easing = LinearEasing),
    placementSpec = tween<IntOffset>(Motion.Medium, easing = Motion.Standard),
    fadeOutSpec = tween(Motion.Quick, easing = LinearEasing),
)

/** A tiny press-in (default 3%) so buttons and chips feel touched. Pair with the same [source] on `clickable`. */
@Composable
fun Modifier.pressScale(source: InteractionSource, to: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) to else 1f,
        animationSpec = tween(if (pressed) 80 else Motion.Quick, easing = Motion.Standard),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * What the screen being left tells the one being opened: which list the tap came from (so the
 * thumbnail flies back to the right place) and the item itself, so the screen can draw on its very
 * first frame instead of waiting for the database.
 */
class OpenedItem {
    var origin: String = ""
        private set
    private var item: Item? = null

    fun set(origin: String, item: Item) {
        this.origin = origin
        this.item = item
    }

    fun previewFor(id: String): Item? = item?.takeIf { it.id == id }
}

val LocalOpenedItem = staticCompositionLocalOf { OpenedItem() }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The animation scope of the navigation destination being drawn; provided by `screen(...)` in MainActivity. */
val LocalDestinationScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks a thumbnail as the same picture on two screens, so it grows from the list row into the
 * item header (and back). Does nothing outside a navigation destination.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedThumb(key: String): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalDestinationScope.current ?: return this
    return with(scope) {
        this@sharedThumb.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> tween<Rect>(Motion.Medium, easing = Motion.Standard) },
        )
    }
}
