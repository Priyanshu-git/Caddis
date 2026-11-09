package app.caddis.data

import androidx.room.withTransaction
import app.caddis.data.db.CaddisDatabase
import app.caddis.data.db.CollectionEntity
import app.caddis.data.db.IntentCorrectionEntity
import app.caddis.data.db.IntentEntity
import app.caddis.data.db.ItemCollectionRef
import app.caddis.data.db.WeeklySummaryEntity
import app.caddis.data.db.toDomain
import app.caddis.data.db.toEntity
import app.caddis.domain.analytics.AnalyticsEvent
import app.caddis.domain.analytics.AnalyticsSink
import app.caddis.domain.analytics.ageBucket
import app.caddis.domain.details.DetailEdits
import app.caddis.domain.details.LinkDetails
import app.caddis.domain.details.needsDetails
import app.caddis.domain.details.withEdits
import app.caddis.domain.details.withLink
import app.caddis.domain.details.mergeDetails
import app.caddis.domain.export.Exporter
import app.caddis.domain.intent.CorrectionStats
import app.caddis.domain.intent.GuessInput
import app.caddis.domain.intent.IntentGuesser
import app.caddis.domain.link.LinkCheck
import app.caddis.domain.link.Links
import app.caddis.domain.link.PlatformDetector
import app.caddis.domain.model.Collection
import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.EditableField
import app.caddis.domain.model.Intent
import app.caddis.domain.model.IntentRule
import app.caddis.domain.model.IntentSource
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Item
import app.caddis.domain.model.ItemStatus
import app.caddis.domain.model.ItemType
import app.caddis.domain.model.Platform
import app.caddis.domain.model.ReturnRule
import app.caddis.domain.model.WeeklySummary
import app.caddis.domain.schedule.ReturnScheduler
import app.caddis.domain.schedule.Trash
import app.caddis.domain.schedule.Weeks
import app.caddis.domain.search.SearchQuery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Asks for an item's details to be fetched in the background (WorkManager in the app, a fake in tests). */
fun interface DetailsScheduler {
    fun schedule(itemId: String)
}

sealed interface SaveOutcome {
    data class Saved(val item: Item) : SaveOutcome
    data class Duplicate(val existing: Item, val link: LinkCheck.Valid) : SaveOutcome
}

/**
 * The single owner of library writes. Every write goes to Room first (principle 6: never lose a
 * save); details, analytics and reminders follow.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val db: CaddisDatabase,
    private val clock: Clock,
    private val analytics: AnalyticsSink,
    private val details: DetailsScheduler,
) {
    private val items = db.items()
    private val collections = db.collections()
    private val intents = db.intents()
    private val weeks = db.weeks()

    private fun now(): Instant = clock.instant()
    private fun zone() = clock.zone

    // ---------- seed ----------

    suspend fun seedDefaultsIfNeeded() {
        if (intents.count() > 0) return
        db.withTransaction {
            val t = now()
            Intents.defaults.forEach { intents.upsert(IntentEntity(it.id, it.name, it.doneLabel, true, it.position, t, 1)) }
            Intents.defaultRules.forEach { intents.upsertRule(it.toEntity()) }
        }
    }

    // ---------- reads ----------

    private val membership: Flow<Map<String, Set<String>>> = collections.observeRefs()
        .map { refs -> refs.groupBy({ it.itemId }, { it.collectionId }).mapValues { it.value.toSet() } }

    /** LIB-01: all saves and notes, newest first. */
    fun observeLibrary(): Flow<List<Item>> = combine(items.observeLibrary(), membership) { list, m ->
        list.map { it.toDomain(m[it.id].orEmpty()) }
    }

    fun observeTrash(): Flow<List<Item>> = items.observeTrash().map { l -> l.map { it.toDomain() } }

    fun observeItem(id: String): Flow<Item?> = combine(items.observe(id), collections.observeForItem(id)) { e, c ->
        e?.toDomain(c.toSet())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(q: String): Flow<List<Item>> {
        val match = SearchQuery.toFtsMatch(q) ?: return flowOf(emptyList())
        return items.search(match).flatMapLatest { list ->
            membership.map { m -> list.map { it.toDomain(m[it.id].orEmpty()) } }
        }
    }

    fun observeIntents(): Flow<List<Intent>> = intents.observeAll().map { l -> l.map { it.toDomain() } }

    fun observeRules(): Flow<Map<String, IntentRule>> =
        intents.observeRules().map { l -> l.associate { it.intentId to it.toDomain() } }

    fun observeCollections(): Flow<List<Collection>> = collections.observeAll().map { l -> l.map { it.toDomain() } }

    fun observeWeek(weekStart: LocalDate): Flow<WeeklySummary> = weeks.observe(weekStart.toString()).map {
        WeeklySummary(weekStart, it?.savedCount ?: 0, it?.usedCount ?: 0, it?.deletedCount ?: 0)
    }

    suspend fun get(id: String): Item? = items.get(id)?.toDomain()

    suspend fun findDuplicate(normalized: String): Item? = items.findByNormalizedUrl(normalized)?.toDomain()

    // ---------- capture ----------

    /**
     * CAP-01/06: save a checked link right away. Unless [allowDuplicate], an existing live save with
     * the same normalised URL is returned instead and nothing is written.
     */
    suspend fun saveLink(
        link: LinkCheck.Valid,
        entry: AnalyticsEvent.SaveEntry,
        sourceApp: String = "",
        sourceFrom: String = "",
        titleHint: String = "",
        allowDuplicate: Boolean = false,
    ): SaveOutcome {
        if (!allowDuplicate) findDuplicate(link.normalized)?.let { return SaveOutcome.Duplicate(it, link) }
        val d = PlatformDetector.detect(link.url)
        val t = now()
        val guessed = guess(GuessInput(d.platform, d.kind, link.url, titleHint))
        val item = Item(
            id = UUID.randomUUID().toString(),
            type = ItemType.LINK,
            url = link.url,
            normalizedUrl = link.normalized,
            platform = d.platform,
            kind = d.kind,
            // MET-01: for Meta links the handle from the URL is all we know; the share text may add a title.
            title = titleHint,
            creator = d.creator,
            intentId = guessed,
            sourceApp = sourceApp,
            sourceFrom = sourceFrom,
            createdAt = t,
        )
        db.withTransaction {
            items.upsert(item.toEntity())
            bumpWeek(t, saved = 1)
        }
        analytics.log(AnalyticsEvent.SaveCreated(d.platform, entry, intentGuessed = true))
        if (!d.platform.neverContact) details.schedule(item.id)
        return SaveOutcome.Saved(item)
    }

    /** CAP-05: a note or plain text without a link. */
    suspend fun saveNote(title: String, body: String = "", entry: AnalyticsEvent.SaveEntry = AnalyticsEvent.SaveEntry.IN_APP, sourceApp: String = ""): Item {
        val t = now()
        val item = Item(
            id = UUID.randomUUID().toString(), type = ItemType.NOTE, url = null, normalizedUrl = null,
            platform = Platform.NOTE, kind = ContentKind.NOTE, title = title.trim(), note = body,
            intentId = Intents.KEEP, sourceApp = sourceApp, createdAt = t,
            userEdited = if (title.isNotBlank()) setOf(EditableField.TITLE) else emptySet(),
        )
        db.withTransaction {
            items.upsert(item.toEntity())
            bumpWeek(t, saved = 1)
        }
        analytics.log(AnalyticsEvent.SaveCreated(Platform.NOTE, entry, intentGuessed = false))
        return item
    }

    private suspend fun guess(input: GuessInput): String {
        val valid = intents.all().map { it.id }.toSet().ifEmpty { Intents.defaults.map { it.id }.toSet() }
        val stats = intents.corrections().groupBy { it.key }.mapValues { e -> e.value.associate { it.intentId to it.count } }
        return IntentGuesser(valid).guess(input, CorrectionStats(stats))
    }

    // ---------- details ----------

    /** MET-02/03: merge fetched details, keep the person's edits, and refine a guessed intent. */
    /** [forUrl]: the link these details were fetched for; if the link was edited since, they are dropped. */
    suspend fun applyDetails(id: String, d: LinkDetails, forUrl: String? = null) {
        db.withTransaction {
            val current = items.get(id)?.toDomain() ?: return@withTransaction
            if (forUrl != null && forUrl != current.url) return@withTransaction
            var merged = current.mergeDetails(d, now())
            if (current.intentSource == IntentSource.GUESSED) {
                merged = merged.copy(intentId = guess(GuessInput(merged.platform, merged.kind, merged.url.orEmpty(), merged.title, merged.description)))
            }
            items.upsert(merged.toEntity())
        }
    }

    /** A fetch that will never succeed (404, blocked): stop retrying, keep what we have (MET-05). */
    suspend fun markDetailsAttempted(id: String, forUrl: String? = null) {
        db.withTransaction {
            val current = items.get(id)?.toDomain() ?: return@withTransaction
            if (forUrl != null && forUrl != current.url) return@withTransaction
            edit(id) { it.copy(detailsFetchedAt = now()) }
        }
    }

    suspend fun refreshDetails(id: String) {
        val it = items.get(id) ?: return
        if (it.platform.neverContact || it.type != ItemType.LINK) return
        details.schedule(id)
    }

    suspend fun scheduleMissingDetails() {
        items.needingDetails().filter { !it.platform.neverContact }.forEach { details.schedule(it.id) }
    }

    // ---------- edits ----------

    private suspend fun edit(id: String, block: (Item) -> Item): Item? = db.withTransaction {
        val current = items.get(id)?.toDomain() ?: return@withTransaction null
        val next = block(current).let { it.copy(updatedAt = now(), revision = current.revision + 1) }
        items.upsert(next.toEntity())
        next
    }

    /** INT-02: one-tap change. Records the correction so future guesses shift. */
    suspend fun setIntent(id: String, intentId: String) {
        val before = items.get(id)?.toDomain() ?: return
        if (before.intentId == intentId && before.intentSource == IntentSource.USER) return
        edit(id) { it.copy(intentId = intentId, intentSource = IntentSource.USER) }
        if (before.intentId != intentId) {
            if (before.intentSource == IntentSource.GUESSED) {
                val key = CorrectionStats.key(before.platform, before.kind)
                val c = intents.correction(key, intentId)
                intents.upsertCorrection(IntentCorrectionEntity(key, intentId, (c?.count ?: 0) + 1))
            }
            analytics.log(AnalyticsEvent.IntentChanged(before.intentId, intentId))
        }
    }

    suspend fun setReason(id: String, reason: String) = edit(id) { it.copy(reason = reason.trim().take(140)) }

    suspend fun setSourceFrom(id: String, from: String) = edit(id) { it.copy(sourceFrom = from.trim().take(60)) }

    suspend fun setNote(id: String, markdown: String) = edit(id) { it.copy(note = markdown) }

    suspend fun setTitle(id: String, title: String) = edit(id) { it.copy(title = title, userEdited = it.userEdited + EditableField.TITLE) }

    /**
     * MET-03: the Edit details sheet. [edits] is built from the stored item inside the transaction,
     * so a details fetch that lands at the same moment is never mistaken for the person's edit.
     */
    suspend fun editDetails(id: String, edits: (Item) -> DetailEdits) {
        db.withTransaction {
            val current = items.get(id)?.toDomain() ?: return@withTransaction
            val next = current.withEdits(edits(current), now())
            if (next != current) items.upsert(next.toEntity())
        }
    }

    sealed interface DetailsResult {
        /** [refetching]: the link moved and details are being fetched again. */
        data class Saved(val refetching: Boolean = false) : DetailsResult
        data class BadLink(val message: String) : DetailsResult
        data class LinkTaken(val other: Item) : DetailsResult
    }

    /**
     * The Edit details sheet (MET-03): text edits, then the link. A link that points somewhere new
     * re-reads the platform, re-guesses the intent while it is still a guess, and fetches details
     * again. Text edits are applied first so the old link's fetched title is never mistaken for yours.
     */
    suspend fun saveDetails(id: String, rawUrl: String?, edits: DetailEdits): DetailsResult {
        val before = items.get(id)?.toDomain() ?: return DetailsResult.Saved()
        val link = if (before.isNote || rawUrl == null || rawUrl.trim() == before.url) null else when (val c = Links.check(rawUrl)) {
            is LinkCheck.Invalid -> return DetailsResult.BadLink(c.message)
            is LinkCheck.Valid -> c
        }
        if (link != null && link.normalized != before.normalizedUrl) {
            findDuplicate(link.normalized)?.takeIf { it.id != id }?.let { return DetailsResult.LinkTaken(it) }
        }
        var refetch = false
        db.withTransaction {
            val current = items.get(id)?.toDomain() ?: return@withTransaction
            var next = current.withEdits(edits, now())
            if (link != null) {
                val moved = next.withLink(link, now())
                if (moved.platform != next.platform || moved.normalizedUrl != next.normalizedUrl) {
                    refetch = moved.needsDetails()
                    if (moved.intentSource == IntentSource.GUESSED) {
                        next = moved.copy(intentId = guess(GuessInput(moved.platform, moved.kind, moved.url.orEmpty(), moved.title, moved.description)))
                    } else next = moved
                } else next = moved
            }
            if (next != current) items.upsert(next.toEntity())
        }
        if (refetch) details.schedule(id)
        return DetailsResult.Saved(refetch)
    }

    /** The title field on the save sheet; everything else stays as it is. */
    suspend fun editTitle(id: String, title: String) = editDetails(id) { DetailEdits(title, it.description, it.creator) }

    // ---------- finish and delete ----------

    /** FIN-01/04: done items stay in the library and leave Home. */
    suspend fun markDone(id: String) {
        val t = now()
        val before = items.get(id)?.toDomain() ?: return
        if (before.status == ItemStatus.DONE) return
        db.withTransaction {
            edit(id) { it.copy(status = ItemStatus.DONE, doneAt = t, returnAt = null, returnRule = ReturnRule.DEFAULT) }
            bumpWeek(t, used = 1)
        }
        analytics.log(AnalyticsEvent.ItemDone(before.intentId, ageBucket(before.createdAt, t)))
    }

    /** Undo of done, or "Mark active" on the item page. */
    suspend fun markActive(id: String) {
        val before = items.get(id)?.toDomain() ?: return
        if (before.status != ItemStatus.DONE) return
        db.withTransaction {
            edit(id) { it.copy(status = ItemStatus.ACTIVE, doneAt = null) }
            before.doneAt?.let { bumpWeek(it, used = -1) }
        }
    }

    /** FIN-02: delete moves to a 30-day Trash. */
    suspend fun delete(id: String, source: AnalyticsEvent.DeleteSource) {
        val t = now()
        val before = items.get(id)?.toDomain() ?: return
        if (before.status == ItemStatus.DELETED) return
        db.withTransaction {
            // doneAt survives the trip to Trash, so restore knows whether it was done.
            edit(id) { it.copy(status = ItemStatus.DELETED, deletedAt = t) }
            bumpWeek(t, deleted = 1)
        }
        analytics.log(AnalyticsEvent.ItemDeleted(before.intentId, ageBucket(before.createdAt, t), source))
    }

    /** Restore from Trash (or undo a delete): back to the library, as it was before. */
    suspend fun restore(id: String) {
        val before = items.get(id)?.toDomain() ?: return
        if (before.status != ItemStatus.DELETED) return
        db.withTransaction {
            edit(id) { it.copy(status = if (it.doneAt != null) ItemStatus.DONE else ItemStatus.ACTIVE, deletedAt = null) }
            before.deletedAt?.let { bumpWeek(it, deleted = -1) }
        }
    }

    suspend fun purge(id: String) = items.delete(id)

    /** Tech arch decision 6: the phone purges Trash older than 30 days when the app opens. */
    suspend fun purgeExpiredTrash(): Int = items.purgeDeletedBefore(now().minus(Trash.RETENTION).toEpochMilli())

    // ---------- bring it back ----------

    /** BRB-02/03. Returns the label shown in the toast, e.g. "tonight at 7 pm". */
    suspend fun bringBackLater(id: String, rule: ReturnRule, date: LocalDate? = null): String {
        val at = ReturnScheduler.returnAt(rule, clock.instant().atZone(zone()), date)
        edit(id) { it.copy(returnRule = rule, returnAt = at) }
        return ReturnScheduler.label(rule, at, zone())
    }

    suspend fun setReturn(id: String, rule: ReturnRule, at: Instant?) = edit(id) { it.copy(returnRule = rule, returnAt = at) }

    suspend fun markOpened(id: String, entry: AnalyticsEvent.OpenEntry) {
        val it = edit(id) { it.copy(lastOpenedAt = now()) } ?: return
        analytics.log(AnalyticsEvent.ItemOpened(it.platform, entry))
    }

    /** Record that Home showed these today; counted once per item per day. */
    suspend fun markSurfaced(ids: List<String>) {
        val t = now()
        val today = t.atZone(zone()).toLocalDate()
        db.withTransaction {
            ids.forEach { id ->
                val e = items.get(id) ?: return@forEach
                val last = e.lastSurfacedAt?.atZone(zone())?.toLocalDate()
                if (last != today) {
                    // A "later" date gets one full day on Home. If it was already shown on an earlier
                    // day since it came due, it falls back to its intent rule from today.
                    val clearReturn = e.returnAt != null && !e.returnAt.isAfter(t) &&
                        e.lastSurfacedAt != null && !e.lastSurfacedAt.isBefore(e.returnAt)
                    items.upsert(
                        e.copy(
                            lastSurfacedAt = t,
                            surfacedCount = e.surfacedCount + 1,
                            returnAt = if (clearReturn) null else e.returnAt,
                            returnRule = if (clearReturn) ReturnRule.DEFAULT else e.returnRule,
                        ),
                    )
                }
            }
        }
    }

    /** FIN-03 keep: it stays, and is not offered again for another 90 days. */
    suspend fun keep(id: String) = edit(id) { it.copy(lastReviewedAt = now()) }

    // ---------- collections ----------

    sealed interface NameResult {
        data class Ok(val id: String) : NameResult
        data class Error(val message: String) : NameResult
    }

    suspend fun createCollection(name: String): NameResult {
        val n = name.trim()
        if (n.isEmpty()) return NameResult.Error("Give the collection a name.")
        if (collections.findByName(n) != null) return NameResult.Error("You already have a collection with that name.")
        val t = now()
        val id = UUID.randomUUID().toString()
        collections.upsert(CollectionEntity(id, n, t, t, 1))
        return NameResult.Ok(id)
    }

    suspend fun renameCollection(id: String, name: String): NameResult {
        val n = name.trim()
        if (n.isEmpty()) return NameResult.Error("Give the collection a name.")
        val clash = collections.findByName(n)
        if (clash != null && clash.id != id) return NameResult.Error("Pick a name that isn’t used yet.")
        val current = collections.all().firstOrNull { it.id == id } ?: return NameResult.Error("That collection is gone.")
        collections.upsert(current.copy(name = n, updatedAt = now(), revision = current.revision + 1))
        return NameResult.Ok(id)
    }

    /** LIB-02: deleting a collection keeps its items. */
    suspend fun deleteCollection(id: String) = collections.delete(id)

    suspend fun setInCollection(itemId: String, collectionId: String, inIt: Boolean) {
        if (inIt) collections.addRef(ItemCollectionRef(itemId, collectionId)) else collections.removeRef(itemId, collectionId)
        edit(itemId) { it }
    }

    // ---------- intents ----------

    suspend fun createIntent(name: String, doneLabel: String): NameResult {
        val n = name.trim().take(20)
        if (n.isEmpty()) return NameResult.Error("Give the intent a name.")
        val all = intents.all()
        if (all.any { it.name.equals(n, ignoreCase = true) }) return NameResult.Error("You already have an intent with that name.")
        val id = "u-" + UUID.randomUUID().toString().take(8)
        val t = now()
        db.withTransaction {
            // New intents sit just before Just keep, which stays last.
            val keep = all.firstOrNull { it.id == Intents.KEEP }
            val pos = keep?.position ?: all.size
            keep?.let { intents.upsert(it.copy(position = pos + 1)) }
            intents.upsert(IntentEntity(id, n, doneLabel.trim().take(16).ifEmpty { "Done" }, false, pos, t, 1))
            intents.upsertRule(Intents.ruleForNewIntent(id).toEntity())
        }
        return NameResult.Ok(id)
    }

    suspend fun updateIntent(id: String, name: String, doneLabel: String): NameResult {
        val n = name.trim().take(20)
        if (n.isEmpty()) return NameResult.Error("Give the intent a name.")
        val all = intents.all()
        if (all.any { it.id != id && it.name.equals(n, ignoreCase = true) }) return NameResult.Error("You already have an intent with that name.")
        val cur = all.firstOrNull { it.id == id } ?: return NameResult.Error("That intent is gone.")
        intents.upsert(cur.copy(name = n, doneLabel = doneLabel.trim().take(16).ifEmpty { "Done" }, updatedAt = now(), revision = cur.revision + 1))
        return NameResult.Ok(id)
    }

    /** INT-05: deleting an intent moves its saves to Just keep, which cannot be deleted. Returns how many moved. */
    suspend fun deleteIntent(id: String): Int {
        if (id == Intents.KEEP) return 0
        return db.withTransaction {
            val moved = items.reassignIntent(id, Intents.KEEP, now().toEpochMilli())
            intents.deleteCorrections(id)
            intents.delete(id)
            moved
        }
    }

    suspend fun countForIntent(id: String) = items.countForIntent(id)

    suspend fun updateRule(rule: IntentRule) = intents.upsertRule(rule.toEntity())

    // ---------- weekly summary ----------

    private suspend fun bumpWeek(at: Instant, saved: Int = 0, used: Int = 0, deleted: Int = 0) {
        val w = Weeks.weekStart(at, zone()).toString()
        weeks.insertIgnore(WeeklySummaryEntity(w, 0, 0, 0))
        weeks.add(w, saved, used, deleted)
    }

    // ---------- data ----------

    suspend fun exportJson(): String {
        val m = collections.allRefs().groupBy({ it.itemId }, { it.collectionId })
        return Exporter.toJson(
            items.all().map { it.toDomain(m[it.id].orEmpty().toSet()) },
            intents.all().map { it.toDomain() },
            collections.all().map { it.toDomain() },
            now(),
        )
    }

    suspend fun exportHtml(): String {
        val m = collections.allRefs().groupBy({ it.itemId }, { it.collectionId })
        return Exporter.toHtmlBookmarks(
            items.all().map { it.toDomain(m[it.id].orEmpty().toSet()) },
            collections.all().map { it.toDomain() },
            intents.all().map { it.toDomain() },
        )
    }

    /** ACC-03 in the client-only phase: wipe everything on this phone, then reseed defaults. */
    suspend fun deleteAllData() {
        db.withTransaction {
            items.deleteAll()
            collections.deleteAll()
            intents.deleteAllCorrections()
            intents.deleteAll()
            weeks.deleteAll()
        }
        seedDefaultsIfNeeded()
    }
}
