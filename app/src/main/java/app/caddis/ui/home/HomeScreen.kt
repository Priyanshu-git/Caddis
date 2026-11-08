package app.caddis.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import app.caddis.ui.calmItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.caddis.domain.model.Item
import app.caddis.domain.model.WeeklySummary
import app.caddis.domain.schedule.doneLabel
import app.caddis.ui.components.BigTitle
import app.caddis.ui.components.BtnStyle
import app.caddis.ui.components.CButton
import app.caddis.ui.components.CCard
import app.caddis.ui.components.EmptyState
import app.caddis.ui.components.Fine
import app.caddis.ui.components.SectionTitle
import app.caddis.ui.components.Tag
import app.caddis.ui.components.Thumb
import app.caddis.ui.intentName
import app.caddis.ui.meta
import app.caddis.ui.sheets.LaterSheet
import app.caddis.ui.theme.C
import app.caddis.ui.theme.CaddisType
import java.time.LocalDate

/** Screens 7, 8, 21: Home — at most 3 picks, then This week, then the tidy-up card. */
@Composable
fun HomeScreen(
    onOpenItem: (Item) -> Unit,
    onLibrary: () -> Unit,
    onTidy: () -> Unit,
    scrollToWeek: Boolean,
    vm: HomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var laterFor by remember { mutableStateOf<Item?>(null) }
    val list = rememberLazyListState()

    LaunchedEffect(scrollToWeek, s.loaded) {
        if (scrollToWeek && s.loaded) {
            vm.summaryViewed(fromNotification = true)
            list.animateScrollToItem(s.picks.size.coerceAtLeast(1) + 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BigTitle("Home")
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 100.dp)) {
            item(key = "today") { SectionTitle("Today", Modifier.padding(top = 6.dp, bottom = 12.dp).semantics { heading() }) }
            if (!s.loaded) {
                item(key = "loading") { Spacer(Modifier.height(120.dp)) }
            } else if (s.picks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        "You’re all caught up",
                        "Nothing fits right now. Your saves are waiting in the Library.",
                        action = { CButton("Browse the library", onClick = onLibrary) },
                    )
                    s.nextUp?.let { next ->
                        CCard(Modifier.fillMaxWidth().padding(top = 22.dp)) {
                            Fine("Next up")
                            Text(next, style = CaddisType.strong, color = C.colors.fg, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            } else {
                items(s.picks.withIndex().toList(), key = { it.value.id }) { (i, item) ->
                    PickCard(
                        item = item,
                        featured = i == 0,
                        intentLabel = intentName(s.intents, item.intentId),
                        doneText = doneLabel(s.intents, item.intentId),
                        onOpenItem = { onOpenItem(item) },
                        onOpen = { vm.open(item) },
                        onDone = { vm.done(item) },
                        onLater = { laterFor = item },
                        modifier = calmItem(),
                    )
                }
                item(key = "hint") { Fine("Everything else waits in the Library.", Modifier.fillMaxWidth().padding(top = 6.dp).then(calmItem()), align = TextAlign.Center) }
            }
            // Shown once data is in, so the week card fades in with the picks instead of popping in empty.
            if (s.loaded) item(key = "week") { Box(calmItem()) { WeekCard(s.week, s.weekLabel) } }
            if (s.staleCount > 0) {
                item(key = "tidy") {
                    CCard(Modifier.fillMaxWidth().padding(top = 24.dp).then(calmItem()), feature = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Time for a tidy-up", style = CaddisType.strong, color = C.colors.fg)
                                Fine("${s.staleCount} ${if (s.staleCount == 1) "save" else "saves"} untouched for 90 days")
                            }
                            CButton("Review", onClick = onTidy, style = BtnStyle.Accent, small = true)
                        }
                    }
                }
            }
        }
    }

    laterFor?.let { item ->
        LaterSheet(item, LocalDate.now(), onPick = { rule, date -> vm.later(item, rule, date); laterFor = null }, onDismiss = { laterFor = null })
    }
}

@Composable
private fun PickCard(
    item: Item,
    featured: Boolean,
    intentLabel: String,
    doneText: String,
    onOpenItem: () -> Unit,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = C.colors
    CCard(modifier.fillMaxWidth().padding(bottom = 12.dp), feature = featured) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenItem),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Thumb(item, if (featured) 84.dp else 68.dp, share = "home")
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Tag(intentLabel)
                Text(item.displayTitle, style = CaddisType.cardTitle, color = c.fg, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Fine(item.meta() + if (item.sourceFrom.isNotBlank()) " · from ${item.sourceFrom}" else "")
            }
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.url != null) CButton("Open", onClick = onOpen, style = if (featured) BtnStyle.Primary else BtnStyle.Accent, small = true)
            CButton(doneText, onClick = onDone, small = true)
            CButton("Later", onClick = onLater, small = true)
        }
    }
}

/** WK-01: counts for the current Monday–Sunday week. Counts only; no streaks. */
@Composable
private fun WeekCard(w: WeeklySummary, label: String) {
    val c = C.colors
    Column(Modifier.padding(top = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            SectionTitle("This week", Modifier.weight(1f).semantics { heading() })
            Fine(label)
        }
        CCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Stat(w.savedCount, "Saved", Modifier.weight(1f))
                androidx.compose.material3.VerticalDivider(color = c.line, modifier = Modifier.height(54.dp))
                Stat(w.usedCount, "Used", Modifier.weight(1f))
                androidx.compose.material3.VerticalDivider(color = c.line, modifier = Modifier.height(54.dp))
                Stat(w.deletedCount, "Deleted", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(n: Int, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n.toString(), style = CaddisType.stat, color = C.colors.fg)
        Fine(label)
    }
}

@Suppress("unused")
@Composable
private fun Divider() = HorizontalDivider(color = C.colors.line)
