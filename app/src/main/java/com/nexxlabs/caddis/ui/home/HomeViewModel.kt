package com.nexxlabs.caddis.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexxlabs.caddis.data.LibraryRepository
import com.nexxlabs.caddis.domain.analytics.AnalyticsEvent
import com.nexxlabs.caddis.domain.analytics.AnalyticsSink
import com.nexxlabs.caddis.domain.model.Intent
import com.nexxlabs.caddis.domain.model.Item
import com.nexxlabs.caddis.domain.model.ItemStatus
import com.nexxlabs.caddis.domain.model.ReturnRule
import com.nexxlabs.caddis.domain.model.WeeklySummary
import com.nexxlabs.caddis.domain.model.label
import com.nexxlabs.caddis.domain.schedule.HomePicker
import com.nexxlabs.caddis.domain.schedule.TidyUp
import com.nexxlabs.caddis.domain.schedule.Weeks
import com.nexxlabs.caddis.ui.ItemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val picks: List<Item> = emptyList(),
    val intents: List<Intent> = emptyList(),
    val week: WeeklySummary = WeeklySummary(LocalDate.MIN, 0, 0, 0),
    val weekLabel: String = "",
    val staleCount: Int = 0,
    val nextUp: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: LibraryRepository,
    private val actions: ItemActions,
    private val analytics: AnalyticsSink,
    private val clock: Clock,
) : ViewModel() {

    private val picker = HomePicker()
    private var actedOn = false
    private var shownCount = 0

    /** Picks depend on the clock (time of day), so re-evaluate every few minutes. */
    private val tick = flow { while (true) { emit(clock.instant()); delay(5 * 60_000L) } }

    private val weekStart = tick.map { Weeks.weekStart(it, clock.zone) }.distinctUntilChanged()

    val state: StateFlow<HomeState> = combine(
        repo.observeLibrary(),
        repo.observeRules(),
        repo.observeIntents(),
        weekStart.flatMapLatest { w -> repo.observeWeek(w) },
        tick,
    ) { items, rules, intents, week, now ->
        val zoned = now.atZone(clock.zone)
        val picks = picker.pick(items, rules, zoned).map { it.item }
        val active = items.filter { it.status == ItemStatus.ACTIVE }
        val nextUp = if (picks.isEmpty()) {
            intents.firstOrNull { i -> rules[i.id] != null && active.any { it.intentId == i.id && it.returnRule != ReturnRule.NEVER } }
                ?.let { "${it.name} saves return on ${rules[it.id].label().lowercase()}" }
        } else null
        HomeState(
            loaded = true,
            picks = picks,
            intents = intents,
            week = week,
            weekLabel = Weeks.label(week.weekStart),
            staleCount = TidyUp.candidates(items, now).size,
            nextUp = nextUp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        viewModelScope.launch {
            state.map { s -> s.picks.map { it.id } }.distinctUntilChanged().collect { ids ->
                if (ids.isNotEmpty()) repo.markSurfaced(ids)
                shownCount = maxOf(shownCount, ids.size)
            }
        }
    }

    fun open(item: Item) { actedOn = true; actions.open(item, AnalyticsEvent.OpenEntry.HOME) }
    fun done(item: Item) { actedOn = true; actions.done(item, state.value.intents) }
    fun later(item: Item, rule: ReturnRule, date: LocalDate? = null) { actedOn = true; actions.later(item, rule, date) }

    fun summaryViewed(fromNotification: Boolean) = analytics.log(AnalyticsEvent.SummaryViewed(fromNotification))

    override fun onCleared() {
        if (state.value.loaded) analytics.log(AnalyticsEvent.HomeViewed(shownCount, actedOn))
    }
}
