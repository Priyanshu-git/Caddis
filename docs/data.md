# Data layer

Room is the single store; DataStore holds settings. Source: `app/src/main/java/app/caddis/data/`.

## Database

`CaddisDatabase` — name `caddis.db`, **version 3**, `exportSchema = true`, schemas committed in
`app/schemas/app.caddis.data.db.CaddisDatabase/{1,2,3}.json`. Opened with `addMigrations(*MIGRATIONS)`
(no destructive fallback). Type converters (`Converters.kt`): `Instant` ⇄ epoch-millis `Long`;
`Set<EditableField>` ⇄ comma-separated names; `Platform` ⇄ its `id` (label and behaviour come back from the registry);
other enums are stored by `name()` (Room default).

### Tables (`Entities.kt`)

| Table | Key | Columns / notes |
| --- | --- | --- |
| `items` | `id` (UUID string) | `type, url?, normalizedUrl?, platform, kind, title, description, creator, thumbnailUrl?, faviconUrl? (added v2, default NULL), lengthSeconds?, note, intentId, intentSource, reason, sourceApp, sourceFrom, status, createdAt, updatedAt, doneAt?, deletedAt?, returnRule, returnAt?, lastSurfacedAt?, surfacedCount, lastOpenedAt?, lastReviewedAt?, detailsFetchedAt?, userEdited, revision`. Indices: `normalizedUrl`, `status`, `createdAt`. **Collection membership is not a column** (see `item_collections`); `Item.collectionIds` is filled by joining. |
| `items_fts` | rowid = `items.rowid` | FTS4 external-content table on `items`, tokenizer `unicode61`; indexed columns: `title, description, creator, url, note, reason, sourceApp, sourceFrom`. Room maintains it with triggers. |
| `intents` | `id` | `name, doneLabel, isDefault, position, updatedAt, revision`. |
| `intent_rules` | `intentId` (FK → `intents.id`, CASCADE) | `days` (bitmask, Monday = bit 0), `timeOfDay`, `maxPerDay`. Keep has no row. |
| `collections` | `id` | `name` (unique index), `createdAt, updatedAt, revision`. |
| `item_collections` | (`itemId`, `collectionId`) | FKs to items and collections, both CASCADE; index on `collectionId`. |
| `weekly_summaries` | `weekStart` (ISO `yyyy-MM-dd`, a Monday) | `savedCount, usedCount, deletedCount`. |
| `intent_corrections` | (`key`, `intentId`) | `count`. `key` = `"<PLATFORM>:<KIND>"`. Local learning for `IntentGuesser`. |

Mapping between entities and domain objects is in `Mappers.kt` (`ItemEntity.toDomain(collectionIds)`,
`Item.toEntity()`, `daysToMask/daysFromMask`, rule/intent/collection mappers). `userEdited` and
`revision` round-trip; `collectionIds` do not go through `toEntity()`.

### Migrations

| From → to | Change |
| --- | --- |
| 1 → 2 | `ALTER TABLE items ADD COLUMN faviconUrl TEXT DEFAULT NULL` (`MIGRATION_1_2`) |
| 2 → 3 | `items.platform` is a lower-case platform id instead of an enum name (`MIGRATION_2_3`): `INSTAGRAM`→`instagram` etc.; old `WEB` rows are re-read with `PlatformDetector` (platform **and** kind, so a Drive link becomes Google Drive / FILE, an ordinary site its domain); `intent_corrections.key` platform half lower-cased (`INSTAGRAM:REEL`→`instagram:REEL`, `WEB:*`→`web:*`). No `revision` bump. A later registry improvement needs its own migration to reclassify stored rows. |

`MigrationTest` rebuilds a v1 and a v2 database from `app/schemas/.../{1,2}.json`, inserts rows, opens them with the
shipped migrations (Room validates the schema on open) and asserts data survives and platforms/keys are rewritten.
See the recipe in [development.md](development.md#add-a-room-column-or-table).

### DAOs (`Daos.kt`)

- **`ItemDao`**: `observeLibrary` (status ≠ DELETED, newest first), `observeTrash` (DELETED, by
  `deletedAt` desc), `observe(id)`, `get`, `all`, `active`, `findByNormalizedUrl` (most recent
  non-deleted match), `search(match)` (join `items_fts`, excludes DELETED, newest first),
  `needingDetails` (LINK, `detailsFetchedAt IS NULL`, not DELETED; the repository drops `neverContact` platforms, so SQL knows no platform names),
  `upsert`, `delete`, `purgeDeletedBefore(cutoffMillis)`, `reassignIntent(from, to, now)` (bumps
  `revision`), `countForIntent`, `deleteAll`.
- **`CollectionDao`**: observe/list collections and refs, `findByName` (case-insensitive), `upsert`,
  `delete`, `addRef` (IGNORE on conflict), `removeRef`, `deleteAll`.
- **`IntentDao`**: observe/list intents (by `position`) and rules, `count`, upserts, deletes,
  corrections (`corrections`, `correction(key, intentId)`, `upsertCorrection`, `deleteCorrections`).
- **`WeekDao`**: `observe/get(weekStart)`, `insertIgnore`, `add(weekStart, saved, used, deleted)`
  (each counter clamped at ≥ 0 with `MAX(0, …)`), `deleteAll`.

## `LibraryRepository`

`@Singleton`, constructor `(db, clock, analytics: AnalyticsSink, details: DetailsScheduler)`. **All writes go
through here.** Reads are `Flow`s.

### Reads

| Method | Returns |
| --- | --- |
| `observeLibrary()` | all non-deleted items (links and notes), newest first, with `collectionIds` joined |
| `observeTrash()` | deleted items |
| `observeItem(id)` | one item or `null`, with collection ids |
| `search(q)` | `Flow<List<Item>>`; empty flow for a query with no terms; uses `SearchQuery.toFtsMatch` |
| `observeIntents()`, `observeRules()` (`Map<intentId, IntentRule>`), `observeCollections()` | |
| `observeWeek(weekStart)` | `WeeklySummary` (zeros when the row does not exist yet) |
| `get(id)`, `findDuplicate(normalized)`, `countForIntent(id)` | suspend |

### Capture

- `seedDefaultsIfNeeded()`: if there are zero intents, insert the eight defaults and their rules in one transaction.
- `saveLink(link, entry, sourceApp, sourceFrom, titleHint, allowDuplicate)` → `SaveOutcome.Saved(item)` or
  `SaveOutcome.Duplicate(existing, link)`. Unless `allowDuplicate`, an existing non-deleted item with the same
  `normalizedUrl` is returned and **nothing is written**. Otherwise: detect platform/kind/handle, guess the
  intent (using valid intent ids + stored corrections), insert the item (`title = titleHint`, `creator` = `Detection.creator`: the handle, or the domain for an
  ordinary site) and bump the week's `saved` in **one transaction**, log `SaveCreated`, and schedule details
  unless the platform is `neverContact`.
- `saveNote(title, body, entry, sourceApp)` → `Item` (type NOTE, intent `keep`, TITLE in `userEdited` if a title
  was given; bumps `saved`).

### Details

- `applyDetails(id, LinkDetails, forUrl?)`: in a transaction, **drops the result if `forUrl` no longer equals the
  item's URL**; merges with `mergeDetails`; if `intentSource == GUESSED`, re-guesses the intent from the richer
  text. 
- `markDetailsAttempted(id, forUrl?)`: sets `detailsFetchedAt` so a hopeless fetch stops retrying.
- `refreshDetails(id)`: schedule a fetch (no-op for Meta or notes). `scheduleMissingDetails()`: schedule every
  item returned by `needingDetails()`.

### Edits

`edit(id, block)` is the private helper: loads, applies `block`, **always** sets `updatedAt = now` and
`revision + 1`, upserts. Public edit methods:

- `setIntent(id, intentId)`: sets `intentSource = USER`. If the intent actually changed: when the previous source
  was `GUESSED`, increments `intent_corrections[(platform:kind, newIntent)]`; always logs `IntentChanged`.
- `setReason` (trim, ≤ 140), `setSourceFrom` (trim, ≤ 60), `setNote(markdown)`, `setTitle` (marks TITLE user-edited).
- `editDetails(id, edits: (Item) -> DetailEdits)`: builds the edits **inside** the transaction from the stored row so a
  concurrent fetch is never mistaken for a user edit; applies `withEdits`.
- `saveDetails(id, rawUrl?, DetailEdits): DetailsResult` (`Saved(refetching)`, `BadLink(message)`, `LinkTaken(other)`):
  validates the link (notes ignore it), refuses a link whose duplicate key belongs to **another** item, then in one
  transaction applies `withEdits` **first** and `withLink` second (so the old link's fetched title is not mistaken
  for the person's); re-guesses the intent if the platform/key moved and the intent is still guessed; schedules a
  refetch when `needsDetails()`.
- `editTitle(id, title)` (used by the save sheet): `editDetails` with only the title changed.

### Finish and delete

- `markDone(id)`: no-op if already DONE. Sets `DONE`, `doneAt`, clears `returnAt`/`returnRule`; week `used + 1`; logs `ItemDone`.
- `markActive(id)`: only from DONE; clears `doneAt`; week `used − 1` **for the week of the original `doneAt`**.
- `delete(id, source)`: no-op if already DELETED. Sets `DELETED` + `deletedAt` (keeps `doneAt`); week `deleted + 1`; logs `ItemDeleted`.
- `restore(id)`: only from DELETED; back to `DONE` if `doneAt != null` else `ACTIVE`; week `deleted − 1` for the week of `deletedAt`.
- `purge(id)` deletes the row. `purgeExpiredTrash()` deletes DELETED rows older than 30 days; returns the count.

### Bring it back

- `bringBackLater(id, rule, date?)`: computes `returnAt` with `ReturnScheduler` and stores rule + time; returns the toast label.
- `setReturn(id, rule, at)`: raw setter (used by Undo).
- `markOpened(id, entry)`: sets `lastOpenedAt`, logs `ItemOpened`.
- `markSurfaced(ids)`: once per item per calendar day: sets `lastSurfacedAt`, `surfacedCount + 1`. If the item has a
  due `returnAt` and it was already surfaced on/after that moment on an earlier day, clears `returnAt`/`returnRule`
  (a "later" date gets one full day on Home, then falls back to its intent rule).
- `keep(id)`: sets `lastReviewedAt` (Tidy-up "Keep": not offered again for 90 days).

### Collections

`createCollection(name)` / `renameCollection(id, name)` → `NameResult.Ok(id)` | `Error(message)` (empty name; duplicate
name, case-insensitive). `deleteCollection(id)` keeps its items. `setInCollection(itemId, collectionId, inIt)` adds or
removes the ref and touches the item (`edit(id){ it }`) so `updatedAt`/`revision` change.

### Intents

- `createIntent(name, doneLabel)`: name trimmed to 20, done label to 16 (default "Done"); case-insensitive unique name;
  id `u-<8 chars>`; inserted at the position of Just keep, which is pushed to the end; gets `ruleForNewIntent`.
- `updateIntent(id, name, doneLabel)`; `updateRule(rule)`.
- `deleteIntent(id)`: no-op (0) for `keep`. In one transaction reassigns **all** items (including Trash) to `keep`, deletes
  that intent's corrections and the intent (its rule cascades). Returns how many items moved.

### Weekly counters

`bumpWeek(at, saved, used, deleted)` inserts the week row if missing then adds deltas (clamped ≥ 0). It always runs
inside the same transaction as the change it counts, and reversals use the week of the original event, so past weeks
stay as they were. **Counters are not recomputed from items.**

### Data

`exportJson()` / `exportHtml()` read everything (including Trash for JSON) and delegate to `Exporter`.
`deleteAllData()`: one transaction clearing items, collections (refs cascade), corrections, intents (rules cascade) and
weekly rows; then `seedDefaultsIfNeeded()`. The caller (`ProfileViewModel.deleteAll`) also resets settings and the
image cache.

## `SettingsRepository` (DataStore `Preferences`, file `settings`)

`Settings` data class and keys:

| Field | Key (type) | Default |
| --- | --- | --- |
| `onboarded` | `onboarded` (bool) | false |
| `dailyReminder` | `daily_reminder` (bool) | false (opt-in) |
| `weeklySummary` | `weekly_summary` (bool) | false (opt-in) |
| `quietHours` | `quiet_hours` (int, **enum ordinal**) | `TEN_TO_SEVEN` |
| `pauseOnLowBattery` | `pause_low_battery` (bool) | true |
| `pauseOnMobileData` | `pause_mobile_data` (bool) | false |
| `lastDailySent` | `last_daily_sent` (ISO date string) | null |
| `lastWeeklySentWeek` | `last_weekly_week` (ISO date string) | null |

`Settings.reminderState()` builds the domain `ReminderState`. `markWeeklySent(date, week)` also sets
`last_daily_sent` (the one-a-day budget is shared). `reset()` clears everything **except** `onboarded`.
`current()` = first emission of `settings`.

## `DetailsScheduler` and `SaveOutcome`

`fun interface DetailsScheduler { fun schedule(itemId: String) }` — implemented by `WorkScheduler`, faked in tests.
`SaveOutcome` is the sealed result of `saveLink`. Both are declared in `LibraryRepository.kt`.

## `DetailsFetcher` (`data/details/DetailsFetcher.kt`)

`suspend fun fetch(url, platform): FetchResult` on `Dispatchers.IO`, **never throws** (cancellation is rethrown):
`Ok(LinkDetails)`, `Retry` (IO errors, HTTP 429, 5xx), `GiveUp` (other 4xx, non-HTML for pages, parse failures,
Skip plan, any other exception).

- Executes `DetailsPlanner.plan`. For oEmbed plans it merges oEmbed (wins for title/creator/thumbnail) with the page
  (fills description/length/favicon); if the merged result is empty it returns `Retry` when either request said
  retry, else `GiveUp`.
- Request headers: `User-Agent: Mozilla/5.0 (Linux; Android 14) CaddisLinkPreview/0.1`, `Accept` (JSON or HTML),
  `Accept-Language: en`. No cookies, no body; only the link is sent.
- Reads at most `MAX_BYTES = 512 KB` of the response; HTML responses must have an `html` content type.

`UserAgentInterceptor` adds the same User-Agent to any request on the shared client that has none (so Coil image
requests are identified too).
