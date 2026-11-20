package com.nexxlabs.caddis.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexxlabs.caddis.R
import com.nexxlabs.caddis.domain.model.Intents
import com.nexxlabs.caddis.domain.onboarding.Onboarding
import com.nexxlabs.caddis.ui.Motion
import com.nexxlabs.caddis.ui.components.AppMark
import com.nexxlabs.caddis.ui.components.BtnStyle
import com.nexxlabs.caddis.ui.components.CButton
import com.nexxlabs.caddis.ui.components.CCard
import com.nexxlabs.caddis.ui.components.CChip
import com.nexxlabs.caddis.ui.components.Chips
import com.nexxlabs.caddis.ui.components.Fine
import com.nexxlabs.caddis.ui.components.Sub
import com.nexxlabs.caddis.ui.components.Tag
import com.nexxlabs.caddis.ui.components.Toggle
import com.nexxlabs.caddis.ui.pressScale
import com.nexxlabs.caddis.ui.settings.SettingsViewModel
import com.nexxlabs.caddis.ui.settings.SettingRowNoLine
import com.nexxlabs.caddis.ui.settings.rememberNotificationToggle
import com.nexxlabs.caddis.ui.theme.C
import com.nexxlabs.caddis.ui.theme.CaddisType
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Test tag of the swipeable pager. */
const val ONBOARDING_PAGER_TAG = "onboarding-pager"

/** A tap on Next glides a little longer than a fling settles, so it reads as one calm movement. */
private const val PAGE_GLIDE_MS = 420

/** How far, in pages, a page is from the one being shown: 0 when it is in view, ±1 when a full page away. */
private fun PagerState.offsetOf(page: Int): Float = (currentPage - page) + currentPageOffsetFraction

/**
 * First launch: five intro cards you swipe through (or step with the main button), then one question about
 * reminders. Skip leaves from any intro card. System back goes one card back, and leaves the app from the first.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val pager = rememberPagerState(pageCount = { Onboarding.PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val page = pager.currentPage

    fun goTo(target: Int) {
        scope.launch { pager.animateScrollToPage(Onboarding.clamp(target), animationSpec = tween(PAGE_GLIDE_MS, easing = Motion.Standard)) }
    }

    fun finish(skippedAtStep: Int?) {
        vm.finishOnboarding(skippedAtStep)
        onDone()
    }

    BackHandler(enabled = page > 0) { goTo(Onboarding.previous(page)) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterEnd) {
            SkipLink(pager) { finish(skippedAtStep = Onboarding.step(page)) }
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(ONBOARDING_PAGER_TAG),
            beyondViewportPageCount = 1,
            key = { it },
        ) { p ->
            when (p) {
                0 -> WelcomePage(pager)
                1 -> IntroPage(p, pager, R.string.onboarding_save_title, R.string.onboarding_save_body, R.string.onboarding_save_tip) { ShareArt() }
                2 -> IntroPage(p, pager, R.string.onboarding_why_title, R.string.onboarding_why_body, R.string.onboarding_why_foot) { WhyArt() }
                3 -> IntroPage(p, pager, R.string.onboarding_back_title, R.string.onboarding_back_body, R.string.onboarding_back_foot) { BackArt() }
                4 -> IntroPage(p, pager, R.string.onboarding_finish_title, R.string.onboarding_finish_body, R.string.onboarding_finish_foot) { FinishArt() }
                else -> NudgesPage(pager, vm)
            }
        }
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 22.dp)) {
            // Dots on the intro cards; "Not now" takes their place on the reminders question.
            Crossfade(
                targetState = page == Onboarding.NUDGES_PAGE,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                animationSpec = tween(Motion.Short),
                label = "dotsOrNotNow",
            ) { nudges ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (nudges) {
                        CButton(stringResource(R.string.onboarding_not_now), onClick = { finish(null) }, style = BtnStyle.Ghost, modifier = Modifier.fillMaxWidth())
                    } else {
                        ProgressDots(pager)
                    }
                }
            }
            MainButton(Onboarding.primary(page)) {
                if (Onboarding.isLast(page)) finish(null) else goTo(Onboarding.next(page))
            }
        }
    }
}

// ---------- chrome ----------

/** Skip fades away as the reminders question slides in, and cannot be tapped there. */
@Composable
private fun SkipLink(pager: PagerState, onSkip: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val canSkip = Onboarding.canSkip(pager.currentPage)
    // The outer box hides the faded link from screen readers once it can no longer be used.
    Box(
        Modifier
            .graphicsLayer { alpha = 1f - (pager.currentPage + pager.currentPageOffsetFraction - (Onboarding.INTRO_PAGES - 1)).coerceIn(0f, 1f) }
            .then(if (canSkip) Modifier else Modifier.clearAndSetSemantics { }),
    ) {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    enabled = canSkip,
                    role = Role.Button,
                    onClick = onSkip,
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.onboarding_skip), style = CaddisType.button, color = C.colors.fg2) }
    }
}

/** One dot per intro card. The dot for the page in view stretches as you drag towards it. */
@Composable
private fun ProgressDots(pager: PagerState) {
    val c = C.colors
    val step = Onboarding.step(pager.currentPage) ?: Onboarding.INTRO_PAGES
    val label = stringResource(R.string.onboarding_step, step, Onboarding.INTRO_PAGES)
    val position = pager.currentPage + pager.currentPageOffsetFraction
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(Onboarding.INTRO_PAGES) { i ->
            val near = (1f - abs(position - i)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .height(6.dp)
                    .width(lerp(6.dp, 18.dp, near))
                    .clip(CircleShape)
                    .background(lerp(c.line, c.accent, near)),
            )
        }
    }
}

/** The one big button. Only its words change between cards, so they cross-fade in place. */
@Composable
private fun MainButton(action: Onboarding.Primary, onClick: () -> Unit) {
    val c = C.colors
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .pressScale(source)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(c.accent)
            .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = { fadeIn(tween(Motion.Short, delayMillis = Motion.Out)) togetherWith fadeOut(tween(Motion.Out)) },
            label = "mainButtonLabel",
        ) { a ->
            Text(
                stringResource(
                    when (a) {
                        Onboarding.Primary.GET_STARTED -> R.string.onboarding_get_started
                        Onboarding.Primary.NEXT -> R.string.onboarding_next
                        Onboarding.Primary.CONTINUE -> R.string.onboarding_continue
                    },
                ),
                style = CaddisType.button,
                color = c.onAccent,
            )
        }
    }
}

// ---------- pages ----------

/**
 * A page that melts into its neighbours while you swipe, and lets its picture trail behind its words
 * so the two move at different speeds. [content] receives that trailing modifier for the picture.
 */
@Composable
private fun PageShell(page: Int, pager: PagerState, content: @Composable ColumnScope.(art: Modifier) -> Unit) {
    val art = Modifier.graphicsLayer {
        val o = pager.offsetOf(page)
        translationX = o * size.width * 0.3f
        val s = 1f - 0.06f * abs(o).coerceAtMost(1f)
        scaleX = s
        scaleY = s
    }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - abs(pager.offsetOf(page)).coerceIn(0f, 1f) }
            .padding(horizontal = 22.dp),
    ) { content(art) }
}

@Composable
private fun WelcomePage(pager: PagerState) = PageShell(0, pager) { art ->
    Spacer(Modifier.height(36.dp))
    Box(art) { AppMark(64.dp) }
    Text(
        stringResource(R.string.onboarding_welcome_title),
        style = CaddisType.h1.copy(fontSize = 38.sp, lineHeight = 41.sp),
        color = C.colors.fg,
        modifier = Modifier.padding(top = 26.dp).semantics { heading() },
    )
    Sub(stringResource(R.string.onboarding_welcome_body), Modifier.padding(top = 10.dp), style = CaddisType.bodyLarge)
    Spacer(Modifier.weight(1f))
    Fine(stringResource(R.string.onboarding_privacy), Modifier.fillMaxWidth().padding(bottom = 4.dp), align = TextAlign.Center)
}

@Composable
private fun IntroPage(
    page: Int,
    pager: PagerState,
    title: Int,
    body: Int,
    foot: Int,
    art: @Composable () -> Unit,
) = PageShell(page, pager) { artModifier ->
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(title),
        style = CaddisType.section,
        color = C.colors.fg,
        modifier = Modifier.semantics { heading() },
    )
    Sub(stringResource(body), Modifier.padding(top = 6.dp), style = CaddisType.bodyLarge)
    // The drawing is decoration: screen readers get the words, not a pile of made-up examples.
    Box(artModifier.fillMaxWidth().weight(1f).padding(top = 32.dp).clearAndSetSemantics { }, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.verticalScroll(rememberScrollState())) { art() }
    }
    Fine(stringResource(foot), Modifier.fillMaxWidth().padding(bottom = 4.dp), align = TextAlign.Center)
}

/** "Want a nudge now and then?": the one real choice in onboarding. Everything is off until it is turned on. */
@Composable
private fun NudgesPage(pager: PagerState, vm: SettingsViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val daily = rememberNotificationToggle { vm.setDaily(it) }
    val weekly = rememberNotificationToggle { vm.setWeekly(it) }
    PageShell(Onboarding.NUDGES_PAGE, pager) { art ->
        val st = s
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(12.dp))
            Box(art) { AppMark(48.dp) }
            Text(
                "Want a nudge now and then?",
                style = CaddisType.section,
                color = C.colors.fg,
                modifier = Modifier.padding(top = 18.dp).semantics { heading() },
            )
            Sub("One reminder a day at most, and only when something fits. Change it any time.", Modifier.padding(top = 4.dp, bottom = 22.dp), style = CaddisType.bodyLarge)
            if (st != null) {
                CCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    SettingRowNoLine("Daily reminder", if (st.dailyReminder) "At most one a day" else "Off until you turn it on") {
                        Toggle(st.dailyReminder, "Daily reminder") { daily(it) }
                    }
                }
                CCard(Modifier.fillMaxWidth()) {
                    SettingRowNoLine("Weekly summary", "A recap of your week, on Sunday") { Toggle(st.weeklySummary, "Weekly summary") { weekly(it) } }
                }
            }
        }
        Fine("Private by default. No ads, no selling your data. Everything stays on this phone.", Modifier.fillMaxWidth().padding(bottom = 4.dp), align = TextAlign.Center)
    }
}

// ---------- drawings ----------
// Static pictures of the real screens, built from the app's own pieces and made-up examples.
// Nothing here is tappable and nothing is saved.

@Composable
private fun Placeholder(size: Int) {
    val c = C.colors
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(c.tint).border(1.dp, c.line, RoundedCornerShape(12.dp)))
}

@Composable
private fun ShareArt() {
    val c = C.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Placeholder(52)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.onboarding_sample_title), style = CaddisType.cardTitle, color = c.fg)
                    Fine(stringResource(R.string.onboarding_sample_site))
                }
            }
        }
        CCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(stringResource(R.string.onboarding_share_heading), style = CaddisType.strong, color = c.fg, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ShareApp(stringResource(R.string.onboarding_share_messages), false)
                ShareApp(stringResource(R.string.onboarding_share_mail), false)
                ShareApp(stringResource(R.string.onboarding_share_notes), false)
                ShareApp(stringResource(R.string.app_name), true)
            }
        }
    }
}

@Composable
private fun ShareApp(label: String, caddis: Boolean) {
    val c = C.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (caddis) AppMark(48.dp) else Box(Modifier.size(48.dp).clip(CircleShape).background(c.tint).border(1.dp, c.line, CircleShape))
        Text(label, style = if (caddis) CaddisType.tag else CaddisType.fine, color = if (caddis) c.accent else c.fg2, maxLines = 1)
    }
}

@Composable
private fun WhyArt() {
    val c = C.colors
    CCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(stringResource(R.string.onboarding_sample_title), style = CaddisType.cardTitle, color = c.fg)
        Fine(stringResource(R.string.onboarding_why_guess), Modifier.padding(top = 2.dp, bottom = 12.dp))
        Chips { Intents.defaults.forEach { CChip(it.name, selected = it.id == Intents.COOK) } }
    }
}

@Composable
private fun BackArt() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PickPreview(R.string.onboarding_sample_title, R.string.onboarding_sample_meta, Intents.defaults.first { it.id == Intents.COOK }.name, featured = true, faded = false)
        PickPreview(R.string.onboarding_sample_title_2, R.string.onboarding_sample_meta_2, Intents.defaults.first { it.id == Intents.WATCH }.name, featured = false, faded = false)
        PickPreview(R.string.onboarding_sample_title_3, R.string.onboarding_sample_meta_3, Intents.defaults.first { it.id == Intents.READ }.name, featured = false, faded = true)
    }
}

@Composable
private fun PickPreview(title: Int, meta: Int, intent: String, featured: Boolean, faded: Boolean) {
    val c = C.colors
    CCard(Modifier.fillMaxWidth().graphicsLayer { alpha = if (faded) 0.45f else 1f }, feature = featured) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Placeholder(if (featured) 64 else 52)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Tag(intent)
                Text(stringResource(title), style = CaddisType.cardTitle, color = c.fg, maxLines = 2)
                Fine(stringResource(meta))
            }
        }
    }
}

@Composable
private fun FinishArt() {
    val c = C.colors
    val cook = Intents.defaults.first { it.id == Intents.COOK }
    val keep = Intents.defaults.first { it.id == Intents.KEEP }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(stringResource(R.string.onboarding_sample_title), style = CaddisType.cardTitle, color = c.fg)
            Fine(stringResource(R.string.onboarding_sample_meta), Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PictureButton(cook.doneLabel, c.accent, c.onAccent, c.accent)
                PictureButton(stringResource(R.string.onboarding_delete), androidx.compose.ui.graphics.Color.Transparent, c.crit, c.crit)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.fg).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.onboarding_trashed), style = CaddisType.sub, color = c.bg, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.onboarding_undo), style = CaddisType.strong, color = c.toastLink)
        }
        CCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CChip(keep.name, selected = true)
                Fine(stringResource(R.string.onboarding_kept_hint))
            }
        }
    }
}

/** A button drawn like [CButton] but not tappable. */
@Composable
private fun PictureButton(text: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color, edge: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        Modifier.heightIn(min = 38.dp).clip(shape).background(fill).border(BorderStroke(1.dp, edge), shape).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = CaddisType.buttonSm, color = ink, maxLines = 1) }
}
