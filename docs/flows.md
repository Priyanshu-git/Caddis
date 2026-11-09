# Flows

End-to-end traces. File names are under `app/src/main/java/app/caddis/` unless prefixed `domain/`.

## 1. Save from the share sheet

1. Another app sends `ACTION_SEND` `text/plain` → `share/ShareActivity` (translucent, separate task).
2. `onCreate` (only when `savedInstanceState == null`) calls `CaptureViewModel.fromShare(text, subject, sourceApp)`.
   `sourceApp` is the sharing app's label from the referrer (or `launchedFromPackage` on API 34+), empty if
   unknown or the app itself. If it returns `false` (nothing usable) → toast "Nothing to save in what was shared." and finish.
3. `fromShare` (runs on the **app scope**): finds a link with `Links.extractFirst(body)`, or a single token containing a
   `.` and no space; validates with `Links.check`.
   - **Link** → title hint = `subject` or the shared text with the link removed (≤ 200 chars) → `save(...)`.
   - **No link** → saved as a **note** (`repo.saveNote`, title = subject or first line ≤ 80 chars, body = text).
4. `repo.saveLink`: duplicate check → insert row + week `saved + 1` (one transaction) → log → schedule details
   (not for Instagram/Facebook). **This is before any UI is drawn**, so the save survives the sheet being swiped away.
5. The sheet state becomes `CaptureSheet.Saved(itemId, entry, sharedFrom)` (or `Duplicate`). `SaveSheet` observes the row
   (`savedItem`) so it fills in as details arrive (skeleton lines while `detailsFetchedAt == null`, no title, non-Meta).
6. The sheet asks one question: intent chips (guess shown as "Guessed from a/an <subject>. Tap to change."), an optional
   reason (≤ 140, saved debounced 400 ms and flushed on close), optional "Edit title" and "Who sent it", and collection
   picker. Dismissing keeps the save (`dismiss` only flushes the reason/title/from on the app scope).
7. When the sheet state returns to `None`, `ShareActivity` finishes. A `CaptureNav` event (from "Open earlier save")
   starts `MainActivity` with a `route` extra and finishes.

**Duplicate**: `DuplicateSheet` offers "Open earlier save" (navigates to the existing item) or "Save again"
(`saveLink(..., allowDuplicate = true)`; both copies are kept).

## 2. Add inside the app

`AddButton` (Home and Library tabs only) → `capture.openAdd(clipboardText)`. The clipboard is read **only after the tap**;
if it holds a valid link it is offered as "From your clipboard → Use". `AddSheet` has Link / Note modes.

- Link mode: `submitLink`. Valid → `save(...)` with entry `IN_APP` (no `sharedFrom`). Invalid and not blank → the sheet
  asks "Save as a note instead?" (`offerNote`); "Save as note" → `saveTypedAsNote` (title = first line ≤ 80, body = rest if
  different). Empty/invalid input shows the error message from `Links.check`.
- Note mode: `submitNote(title)` creates a note and navigates to `note/<id>`.

## 3. Details fetch

`LibraryRepository.saveLink` / `saveDetails` / `refreshDetails` / `scheduleMissingDetails` → `WorkScheduler.schedule(id)`
(`appScope.launch`, reads current settings for constraints) → unique `details-<id>` work (REPLACE) → `DetailsWorker`:

1. Load the item; stop (success) if gone or it has no URL.
2. `DetailsFetcher.fetch(url, platform)` (see [data.md](data.md#detailsfetcher-datadetailsdetailsfetcherkt)).
3. `Ok` → `repo.applyDetails(id, details, forUrl = url)`; `GiveUp` → `repo.markDetailsAttempted(id, forUrl = url)`;
   `Retry` → `Result.retry()` with 30 s exponential backoff up to 4 attempts, then end without marking (the next app start
   re-schedules it via `scheduleMissingDetails`).
4. `applyDetails` merges without touching `userEdited` fields or blanking known fields, re-guesses the intent only while
   `intentSource == GUESSED`, and ignores results whose `forUrl` no longer matches (the link was edited meanwhile).

When nothing could be fetched (offline, blocked, bot-check, Meta) the item keeps an empty `title` and the UI shows
`displayTitle` (a readable default from the URL). The real title replaces it as soon as one arrives.

Thumbnails: `Thumb` shows `thumbnailUrl`; if absent or failing, the site icon (`Favicons.forItem`); then a glyph
(note / play / document). Instagram and Facebook always get the glyph.

## 4. Edit details (item page pencil)

`ItemScreen` → `EditDetailsSheet(onSave = vm::saveDetails)` → `ItemViewModel.saveDetails` (on the app scope, awaited) →
`repo.saveDetails`:

- Bad link → message shown on the sheet. Link already on another save → "Another save already has this link: “…”."
- Otherwise text edits then link change in one transaction (see [data.md](data.md#edits)). If the link points somewhere new
  details are fetched again and a toast says "Link changed. Fetching its details."
- Clearing a field hands it back to Caddis; a changed field becomes the person's own and is never overwritten.

"Refresh" on the item page: Meta items get a toast ("Caddis doesn’t fetch <Platform> details. Edit them yourself any
time."); others schedule a fetch ("Refreshing details. Your edits are kept.").

## 5. Home

`HomeViewModel.state` combines `observeLibrary`, `observeRules`, `observeIntents`, `observeWeek(<current week>)` and a
**5-minute tick** (so time-of-day changes re-evaluate), then `HomePicker.pick(...)`. State: picks (≤ 3 items), intents, the
week summary + label, `staleCount` (`TidyUp.candidates`), and `nextUp` ("<Intent> saves return on <rule label>") when there are
no picks but some intent has active saves and a rule.

- When the pick ids change and are non-empty, `repo.markSurfaced(ids)` runs (once per item per day).
- Actions go through `ItemActions`: **Open** (`markOpened` + `ACTION_VIEW`), **Done** (`markDone` + toast "Marked “<label>”." with
  Undo), **Later** (`LaterSheet` → `bringBackLater` + toast "Back <when>." with Undo restoring the previous rule/time).
- `HomeViewed(picksShown, actedOn)` is logged when the ViewModel is cleared.
- Opening `home?week=true` (weekly summary notification) scrolls to the This week card and logs `SummaryViewed(fromNotification = true)`.
- Empty state: "You’re all caught up" + "Browse the library" + optional Next up card.
- If `staleCount > 0`, a "Time for a tidy-up" card links to Tidy-up.

## 6. Done, delete, restore

All via `ItemActions` (app scope) so leaving a screen never drops them:

- Done: `markDone` → toast with Undo (`markActive`). On the item page a DONE item shows "Mark active" instead.
- Delete: `delete(item, source)` → toast "Moved “<title ≤ 28>” to Trash" with Undo (`restore`). Item page Delete also pops back.
- Trash screen: Restore (`restore`, toast "Restored to your Library.") or Delete forever (`purge`, "Deleted for good.").
- Weekly counters adjust inside the same transactions (see [data.md](data.md#weekly-counters)).

## 7. Library and search

`LibraryViewModel` keeps UI state (`Filters`, query, `searching`, `showCollections`, `recent`) in a private `MutableStateFlow` and
combines it with the library, search results, intents and collections.

- **Filters**: intent chip row (toggle), Filter sheet (platform, status Active/Done/All), applied in memory by `Filters.apply`.
- **Search**: `openSearch()` resets query and filters. The query is debounced 120 ms into `repo.search(q)` (FTS prefix AND). Hits
  show `SearchQuery.explain` ("Matched in the title"). `submitSearch()` adds the words to `recent` (max 4, **memory only**) and logs
  `SearchRun(resultBucket)` — never the words.
- **Collections tab** lists collections with counts; a collection opens `shelf/<id>` (same `LibraryViewModel`, `collectionId`
  nav argument, status defaults to Active).

## 8. Item page and notes

`ItemScreen` (route `item/{id}`): thumbnail, intent chip (opens `IntentSheet`), platform chip, kind·length, title, "by … · from …
via …", description, "Why you kept it" (inline reason edit, ≤ 140), note card (opens `note/{id}`), collection chips + picker,
"Saved <date> · details loaded", Refresh. Top bar: Edit details, Share link (only with a URL), Bring back later. Bottom bar:
Open (links only), Done/Mark active, Delete.

`NoteScreen` (route `note/{id}`): rich-text editor (`richeditor-compose`) holding Markdown. Standalone notes also have an editable
title, intent chip and collections. Typing calls `ItemViewModel.noteChanged` → debounced 500 ms → `repo.setNote`; the top bar shows
"Saving…"/"Saved"; leaving flushes via `flushNote` on the app scope. Toolbar: bold, italic, bullets, numbers, code.

Share: `ItemActions.share(item)` → chooser with `ShareText.of(item)` (+ subject/title). Open: `ACTION_VIEW` on the saved URL (Android
picks the original app when installed, else the browser); failure toast "No app on this phone can open that link."

## 9. Tidy-up

`TidyViewModel` snapshots `TidyUp.candidates(library, now)` once (the queue does not shift while reviewing). **Keep** →
`repo.keep(id)` (`lastReviewedAt`); **Delete** → `ItemActions.delete(item, TIDY_UP, toast = false)`. Progress bar of segments; when finished,
logs `TidyUpFinished(kept, deleted)` and shows "All tidy". "Open it first" opens the link.

## 10. Notifications

- **Opt-in** on onboarding or Profile → Notifications. Turning a toggle on requests `POST_NOTIFICATIONS` on API 33+
  (`rememberNotificationToggle`); the setting is saved with the permission result.
- `ReminderWorker` (hourly) → `Reminders.decide` → `Notifier.showPick` (title = `displayTitle`, text = "Intent · length-or-kind";
  taps open `item/<id>`) or `Notifier.showWeekly` ("N saved · N used · N deleted"; taps open `home?week=true`). Channels:
  `reminders` (default importance), `weekly` (low).
- Sent dates are stored only when the notification was really posted.

## 11. Settings and data

- Quiet hours: tap cycles `TEN_TO_SEVEN → ELEVEN_TO_SIX → NINE_TO_EIGHT`.
- "Pause on low battery" (default on) and "Pause on mobile data" (default off) only change the **constraints of future** details work.
- **Export**: `ProfileViewModel.export(json)` builds text from Room, writes `cacheDir/exports/caddis-<date>.json|html`, gets a
  `FileProvider` URI and starts a chooser (`ACTION_SEND`, read grant).
- **Clear cache**: clears Coil memory and disk caches, then refreshes the displayed size.
- **Delete all data** (Account): confirm dialog → `repo.deleteAllData()`, `settingsRepo.reset()` (keeps `onboarded`), clear image cache, toast, navigate to Home with the back stack cleared.

## 12. Intents and return rules

- Intents screen: edit name (≤ 20) and Done label (≤ 16); Delete asks twice ("Sure? N saves move to Just keep") via `rememberConfirm`; Just keep is locked
  ("Default, always kept"). New intent card creates one with a weekday-afternoon rule.
- Return rules screen: per intent, day chips (Mon–Sun), time of day chips, max per day (− / +, 1..3). Changes call `repo.updateRule` immediately.
  An intent with no selected days "Never returns".

## 13. Bottom-bar navigation

`navigateToTab(route)` pops to Home with `saveState`, `launchSingleTop`, and `restoreState = !toHome`: leaving a tab saves its stack and
returning restores it, **except Home, which never restores** (as the start destination it would otherwise inherit the stack of the tab you left).
Covered by `TabNavigationTest`.
