# Invariants, traps and known gaps

Read this before changing behaviour. Section 1 are **hard rules** (a change that breaks one is a bug).
Section 2 are **traps**: things the code does that look wrong or surprising but are intentional or easy to
break. Section 3 are **known gaps**.

## 1. Hard rules

### Product
1. **Never lose a save.** The Room insert happens first; details, sheets and network come after. Saves from the share sheet run on the
   app scope and happen in `ShareActivity.onCreate` before the UI draws.
2. **Your words win.** Fetched details never overwrite a field in `Item.userEdited`, and never blank a field that already has a value
   (`mergeDetails`). Only the person emptying a field hands it back (`withEdits`).
3. **Home shows at most 3** (`HomePicker.maxPicks`). Notes never appear on Home. A deferred item (`returnAt` in the future) never appears before its date.
4. **Zero taps to organise:** the intent is guessed, not asked for; changing it is optional.
5. **Calm:** no streaks, no guilt copy, at most one notification a day (daily and weekly share the budget), none in quiet hours, no catch-up bursts.
   Notifications are opt-in (off by default).
6. **Just keep** (`keep`) cannot be deleted, has no return rule, and receives the saves of any deleted intent.
7. **Deleting a collection keeps its items.** **Deleting an item** moves it to Trash for 30 days (restorable); only Trash actions and the 30-day purge delete rows.
8. Done items stay in the library; Done keeps the item but removes it from Home.

### Privacy and platform
9. **Never call Meta.** Instagram and Facebook links are saved with platform + handle from the URL only: `DetailsPlanner` returns `Skip`, `DetailsFetcher`
   gives up, `refreshDetails`/`needingDetails`/`needsDetails` exclude them, and `Favicons` never returns a Meta icon (including `fbcdn.net`, `cdninstagram.com`).
   If you add any new request path (images, previews, icons), enforce this there too.
10. **No language models or AI anywhere.** Intent guessing is rules; search is keyword FTS.
11. Fetching uses only public, keyless endpoints (oEmbed / Open Graph). No API keys, login, paywall bypass or third-party proxy. Only the link is sent; no cookies.
12. **Never download or rehost original media.** Only thumbnails/icons are cached by Coil (200 MB cap, clearable).
13. **Analytics events** must be a subclass of `AnalyticsEvent` and never carry URLs, titles, notes, reasons, creators or collection names. Custom intents are reported as `custom`.
    The search event carries only a result-count bucket.
14. **Share-out never includes** the reason, note, intent or collections (`ShareText`); the title is only a real title, never a guessed default.
15. The clipboard is read only after the person taps Add (never in the background).
16. **Client only**: no sync, Firebase, sign-in or analytics backend code until asked.

### Engineering
17. All library writes go through `LibraryRepository`. Multi-row changes (save + weekly counter, done/delete + counter, intent delete + reassign, delete-all) use one `withTransaction`.
18. Inject `Clock`; never call `Instant.now()`/`LocalDate.now()` inside logic you want tested. (Some UI code still uses `LocalDate.now()`; see traps.)
19. `:domain` has no Android/Room/OkHttp/Hilt/Compose imports. New behaviour: domain + tests first, then wire.
20. Room schema change ⇒ bump `version`, add a `Migration`, commit the exported `app/schemas/.../<version>.json`, extend `MigrationTest`. No destructive fallback.
21. Dependency versions live only in `gradle/libs.versions.toml`.
22. Every record keeps `revision` and `updatedAt` correct (the repository's `edit()` helper and `mergeDetails`/`withEdits`/`withLink` bump them). Sync readiness depends on it.
23. Add/adjust tests in the same change; update the relevant doc in `docs/`.

## 2. Traps (read before editing nearby code)

**Links and duplicates**
- `Links.check` **accepts a bare host** and prepends `https://`; the host must contain a `.`. So `notaurl.com` is a link, `hello world` and `localhost` are not.
- There are **two tracking-param lists**. `Links.normalize` (duplicate key) drops more (`ref`, `ref_src`, `ref_url`, `s`, `t`, `feature`, …); `ShareText.cleanUrl` (what you share) keeps `t`
  and only drops identity params. Don't merge them.
- The duplicate key ignores scheme, `www.`, `m.`, fragment and param order, and folds `twitter.com` → `x.com` and `youtu.be/<id>` → `youtube.com/watch?v=<id>`. A duplicate check only
  matches **non-deleted** items. `saveLink(allowDuplicate = true)` ("Save again") bypasses it.
- `saveDetails` refuses a new link only if **another** item already has its key; re-saving the item's own link is fine.

**Details and merging**
- `detailsFetchedAt == null` means "still needs details" (drives retries at app start). `markDetailsAttempted` sets it on a permanent failure; an exhausted `Retry` deliberately leaves it null.
- A save with an empty `title` is normal; always render `Item.displayTitle`, never `title`, in UI. But `ShareText` and the Edit details sheet use the real `title`.
- `applyDetails` re-guesses the intent **only while `intentSource == GUESSED`**; `setIntent` flips it to `USER` (and `saveDetails` re-guesses on a link move only if still guessed).
- Intent corrections are recorded only when the person changes a **guessed** intent. A learned guess needs ≥ 2 corrections for that `platform:kind`; keyword matches win over learning.
- `IntentGuesser` keyword rules are ordered Cook → Buy → Go → Learn → Try; first match wins. The text includes the **URL**, so slugs matter before details load.
- Generic/bot-check titles are rejected by `DetailsParser`; a title equal to a bare site name ("Reddit", "Medium"…) is rejected for non-web platforms.
- Reddit pages usually refuse non-browsers, so Reddit saves rarely get a thumbnail; Medium may show the slug title when its bot check blocks.

**Home and scheduling**
- `FRESH_GRACE` is **12 hours** (the nearby comment says "a day"): a new save will not be picked until it is 12 h old, unless it has an explicit `returnAt` that has come due.
- Explicit returns (`returnAt`) bypass the day-of-week rule and `maxPerDay`, and are scored `100 + ageBonus`.
- `ReturnRule.NEVER` exists in the model and is excluded by `HomePicker`, but **no UI sets it**; the Later sheet offers only Tonight / This weekend / Next week / Pick a date. "Never returns" for an intent comes from a rule with no days.
- `markSurfaced` counts once per item per day, and clears an expired `returnAt` after the item has been shown on a later day than it came due.
- `TidyUp` includes notes (any ACTIVE item untouched 90 days); `HomePicker` excludes them. "Keep" sets `lastReviewedAt`, which resets the 90 days.
- `HomeScreen` passes `LocalDate.now()` to the Later sheet while `ItemViewModel` uses the injected clock; they agree outside of tests.
- `TimeOfDay.of(hour)` returns `EVENING` for hours outside 5–23 (late night counts as evening for scoring).

**Weekly counters**
- Counters are incremental, not recomputed. Undo/restore subtract from the week of the **original** `doneAt`/`deletedAt`. Deleting a *done* item adds to `deleted` and leaves `used` alone.
- `deleteAllData()` clears the weekly rows; `ProfileViewModel.deleteAll` is what also resets settings (keeping `onboarded`) and the image cache.

**Persistence**
- Enums are stored by **name**; renaming an enum constant needs a migration. `QuietHours` is stored by **ordinal**; don't reorder it.
- Weekday rules are stored as a bitmask (Monday = bit 0). `Instant`s are epoch millis. `userEdited` is a comma-separated string.
- `items_fts` is an external-content FTS4 table over `items`; its indexed `note` column holds raw Markdown. Changing its columns is a schema change.
- Backup rules include `caddis.db` and the `sharedpref` domain. DataStore preferences are stored under `files/datastore/`, which those rules do not include, so restored installs get the
  library but not the settings (including `onboarded`).
- Collection membership is in `item_collections`, so `ItemEntity` has no `collectionIds`; `setInCollection` touches the item to bump `updatedAt`/`revision`.

**UI and scopes**
- `CaptureViewModel` is created per activity; the share sheet and the main app do not share one.
- Use the app `CoroutineScope` (not `viewModelScope`) for anything the person expects to complete after leaving the screen (this is why `ItemActions`, `setTitle`, `saveDetails` do).
- Bottom bar: **Home never restores** a saved stack (it would resurrect the previous tab's stack). Don't "simplify" `navigateToTab`; `TabNavigationTest` guards it.
- `ToastHost` shows one toast at a time for 5 s; `Messenger` buffers 8.
- `LibraryViewModel.openSearch()` clears all filters; `recent` searches live only in that ViewModel's memory.
- The WorkManager initializer is removed in the manifest; do not re-enable auto-init or Hilt workers will fail.

**Naming and comments**
- Requirement labels (`CAP-…`, `MET-…`, `BRB-…`, `FIN-…`, `INT-…`, `SRC-…`, `WK-…`, `ACC-…`, `NFR`, "tech arch") and "Screen N" in comments refer to deleted documents; see [README.md](README.md).

## 3. Known gaps and limits

- No sign-in or sync. Account screen shows "On this phone · Sync Off · Sign-in None"; its only action is **Delete all data**.
- `AnalyticsSink` logs only in debug builds; release builds emit nothing.
- The full privacy policy link is unset (`FULL_POLICY_URL = null`); the button shows a toast saying it will be published before release.
- The note editor has no heading or quote buttons (they render if imported).
- Recent searches are not persisted.
- X and Reddit details depend on public oEmbed endpoints; if one stops answering, those saves fall back to the URL-derived title/handle.
- No Android instrumentation tests; UI composables are not unit-tested (only navigation, motion, ViewModels, repository, fetcher, migration and domain rules are).
- New default intents will not reach existing installs (defaults are seeded only when no intents exist).
- Home screen widget, location-based return and import are not implemented.
