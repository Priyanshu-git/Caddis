# Flows

End-to-end traces. File names are under `app/src/main/java/com/nexxlabs/caddis/` unless prefixed `domain/`.

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
   **This is before any UI is drawn**, so the save survives the sheet being swiped away.
5. The sheet state becomes `CaptureSheet.Saved(itemId, entry, sharedFrom)` (or `Duplicate`). `SaveSheet` observes the row
   (`savedItem`) so it fills in as details arrive (skeleton lines while `detailsFetchedAt == null`, no title).
6. The sheet asks one question: intent chips (guess shown as "Guessed from a/an <subject>. Tap to change."), an optional
   reason (≤ 140, saved debounced 400 ms and flushed on close), and the collection chips (see "Collection chips" below).
   The details card has one pencil ("Edit details"): it turns the card into an inline editor with **Title, Platform, By and
   Who sent it** (notes: Title and Who sent it). Each field starts with its current value; one the person has written shows a
   reset arrow ("Reset <field>") that empties the draft, which hands the field back to Caddis. The check ("Done editing"), Done,
   or dismissing saves only the fields that differ from what they started as (`CaptureViewModel.editFields`, on the app scope),
   so a fetch landing while the editor is open is never overwritten. "Who sent it" shows as "From …" on the card.
   Dismissing keeps the save (`dismiss` only flushes the reason, the edited fields, from, and a collection
   name still being typed, on the app scope).
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

When nothing could be fetched (offline, blocked, bot-check) the item keeps an empty `title` and the UI shows
`displayTitle` (a readable default from the URL). The real title replaces it as soon as one arrives.

Thumbnails: `Thumb` shows `thumbnailUrl`; if absent or failing, the site icon (`Favicons.forItem`); then a glyph
(note / play / document).

## 4. Edit details (item page pencil)

`ItemScreen` → `EditDetailsSheet(onSave = vm::saveDetails)` → `ItemViewModel.saveDetails` (on the app scope, awaited) →
`repo.saveDetails`:

- Bad link → message shown on the sheet. Link already on another save → "Another save already has this link: “…”."
- Otherwise text edits then link change in one transaction (see [data.md](data.md#edits)). If the link points somewhere new
  details are fetched again and a toast says "Link changed. Fetching its details."
- Clearing a field hands it back to Caddis (and schedules a fetch so it can be filled); a changed field becomes the
  person's own and is never overwritten. This includes the **Platform** field (links only, starts as the current name,
  placeholder is the host). A written field shows the same reset arrow as on the save sheet (shared `DetailField`).

"Refresh" on the item page schedules a fetch for any link ("Refreshing details. Your edits are kept.").

## 5. Home

`HomeViewModel.state` combines `observeLibrary`, `observeRules`, `observeIntents`, `observeWeek(<current week>)` and a
**5-minute tick** (so time-of-day changes re-evaluate), then `HomePicker.pick(...)`. State: picks (≤ 3 items), intents, the
week summary + label, `staleCount` (`TidyUp.candidates`), and `nextUp` ("<Intent> saves return on <rule label>") when there are
no picks but some intent has active saves and a rule.

- When the pick ids change and are non-empty, `repo.markSurfaced(ids)` runs (once per item per day).
- Actions go through `ItemActions`: **Open** (`markOpened` + `ACTION_VIEW`), **Done** (`markDone` + toast "Marked “<label>”." with
  Undo), **Later** (`LaterSheet` → `bringBackLater` + toast "Back <when>." with Undo restoring the previous rule/time). The sheet's
  **Not again** row sets `ReturnRule.NEVER` (toast "Won’t come back. Still in your Library."): the save stays in the Library, off Home and off Tidy-up.
- A **Just keep link** can be one of the picks: once a month, only when a slot is free (`HomePicker.keptReminder`, see [domain.md](domain.md)).
- `HomeViewed(picksShown, actedOn)` is logged when the ViewModel is cleared.
- Opening `home?week=true` (weekly summary notification) scrolls to the This week card and logs `SummaryViewed(fromNotification = true)`.
- Empty state: "You’re all caught up" + "Browse the library" + optional Next up card. With no saves at all (`firstRun`): "Nothing saved yet" and three ways to save
  (see [First launch](#14-first-launch)); the This week card is hidden.
- If `staleCount > 0`, a "Time for a tidy-up" card links to Tidy-up.

## 6. Done, delete, restore

All via `ItemActions` (app scope) so leaving a screen never drops them:

- Done: `markDone` → toast with Undo (`markActive`). On the item page a DONE item shows a "Completed · Made it · 12 Nov" card under the description and "Mark incomplete" (same `markActive`) in the bottom bar.
- Delete: `delete(item, source)` → toast "Moved “<title ≤ 28>” to Trash" with Undo (`restore`). Item page Delete also pops back.
- Trash screen: Restore (`restore`, toast "Restored to your Library.") or Delete forever (`purge`, "Deleted for good.").
- Weekly counters adjust inside the same transactions (see [data.md](data.md#weekly-counters)).

## 7. Library and search

`LibraryViewModel` keeps UI state (`Filters`, query, `searching`, `showCollections`, `recent`) in a private `MutableStateFlow` and
combines it with the library, search results, intents and collections.

- **Filters**: Library chip row = Filter button + "All" + one chip per platform in the library (`LibraryState.platforms`, scrolls, tap again to clear); there is no Kept chip, Just keep is an ordinary choice in the sheet's Intent group. Filter sheet (intent and platform; the Library passes `showStatus = false`, the shelf still shows Status Active/Done/All; the button counts intent, plus platform in search), applied in memory by `Filters.apply`. The Platform group is **dynamic**: `LibraryState.platforms` comes from
  `PlatformFacets.of` over the saves in scope (a collection shelf lists only its own), most saves first; first 6 shown, then "More · n"; the
  chosen platform stays visible; the group is hidden when there are no link saves; a filter on a platform whose last save is gone is dropped.
- **Completed section** (LIB-01): `LibraryState.sections = LibrarySections.of(filtered items)` splits the saves the filters let through into `active`
  (newest saved first, shown as before) and `completed` (status DONE, latest `doneAt` first). The count line counts `active` only. Under the active
  rows sits one "COMPLETED · n" row, **collapsed on first open**; tapping it shows the latest 3 (`LibrarySections.PREVIEW`) and "Show all n"
  shows the rest in place. Open/closed and show-all are `rememberSaveable` in `LibraryScreen`, so they survive opening a save and coming
  back or switching tabs, and start collapsed again after the app restarts. Collapsing resets "Show all". Filters apply to both groups. Completed rows
  keep their normal colours and thumbnails, gain a green tick (`CompletedTick`) and read "Made it · 12 Nov" (`Item.completedMeta`). With no
  active saves the list says "Nothing waiting. What you finished is below."; with nothing at all it shows the usual empty state.
- **Search**: there is no search box at rest; the Library header shows a search icon beside Trash. Tapping it (`openSearch()`) grows the
  icon into a full-width field in the header (the icon becomes a back arrow, ✕ clears the words and keeps search open, back arrow or system
  back calls `closeSearch()`). `openSearch()` and `closeSearch()` reset query and filters. The query is debounced 120 ms into `repo.search(q)` (FTS prefix AND). Hits
  show `SearchQuery.explain` ("Matched in the title"). `submitSearch()` adds the words to `recent` (max 4, **memory only**) and logs
  `SearchRun(resultBucket)` — never the words. Search returns active and completed saves together by relevance; a completed hit carries the
  tick and "Made it · 12 Nov" instead of a done-word pill.
- **Collections tab** lists collections with counts; a collection opens `shelf/<id>` (same `LibraryViewModel`, `collectionId`
  nav argument, status defaults to Active).

## 8. Item page and notes

`ItemScreen` (route `item/{id}`): thumbnail, intent chip (opens `IntentSheet`), platform chip, kind·length, title, "by … · from …
via …", description, "Why you kept it" (inline reason edit, ≤ 140), note card (a plain multi-line text box right on the page: new lines
allowed, no length limit, autosaves as you type, no other screen), "Collections" (rests as a read-only list of the collections it is in with an Edit button, "Add" when there are none; Edit opens
the collection chips in place and becomes Done, see "Collection chips" below),
"Saved <date> · details loaded", Refresh. Top bar: Edit details, Share link (only with a URL), Bring back later. Bottom bar:
Open (links only), Done/Mark incomplete, and Delete as a 48 dp trash icon button at the end (`CIconButton`, description "Delete") so the middle button has room for a long label.

**Collection chips** (`CollectionChips`, used by the save sheet directly, and by the item page and standalone notes through
`CollectionsSection`, which keeps them hidden until Edit is pressed; Done with a name still typed creates it). All collections show as chips in a
scroll area about two rows tall (a third row peeks out when there are more), newest first. Tapping a chip files or unfiles the save
(`setInCollection`); selected chips show a tick. The first chip is a dashed **New**: tapping it turns it into a name field in place (≤ 40
characters) with a tick and a cross. The keyboard's Done key or the tick calls `createCollectionAndFile`, which creates the collection,
files the save in it and, on the item page and notes, shows "Created “…” and added." with Undo; an empty name or the cross cancels. A name
that already exists files the save in that collection ("“…” already exists. Added."), with no error. On the save sheet the name is kept in the
sheet, so tapping Done or swiping the sheet away with a name typed still creates it; there is no toast there because the sheet's scrim
would cover it (the chip turning selected is the confirmation). With no collections, only **New** shows, with a line of encouragement.

The item page's note box shows `NoteMarkdown.toPlainText(item.note)` and stores `NoteMarkdown.fromPlainText(text)` through the same
`ItemViewModel.noteChanged` autosave (500 ms, flushed on leaving), so what is typed is kept as typed and older rich notes open as plain text.

`NoteScreen` (route `note/{id}`, **standalone notes only**: created from the Add sheet or opened from the Library): rich-text editor (`richeditor-compose`) holding the note as Markdown written by `NoteMarkdown`, so whatever is typed (`*`, `_`, `#`, `<`, `[`,
a leading `-`) is kept as typed. An editable title sits at the top with the intent chip and one chip per collection right under it (tap the intent for
`IntentSheet`, any collection chip for a sheet of tick chips with "New"). An empty note opens with the caret in the body. Typing calls
`ItemViewModel.noteChanged` → debounced 500 ms → `repo.setNote`; once something has changed, the top bar shows "Saving…" then "Saved"; leaving flushes
via `flushNote` on the app scope. Typing something and undoing it within the pause records the saved text as the latest draft, so the undone text is
never written. A screen turn reopens the editor on `currentNote()` (pending draft first). While the body has focus a formatting bar sits above the
keyboard: bold, italic, bullets, numbers, code, link (a sheet for the address; only web links), and undo (steps back through bursts of typing, a
pause of 1 s or more starting a new step). The ⋮ menu marks the note done (or active), brings it back later, or deletes it (to Trash, with Undo) and goes back.

Share: `ItemActions.share(item)` → chooser with `ShareText.of(item)` (+ subject/title). Open: `ACTION_VIEW` on the saved URL (Android
picks the original app when installed, else the browser); failure toast "No app on this phone can open that link."

## 9. Tidy-up

`TidyViewModel` snapshots `TidyUp.candidates(library, now)` once (the queue does not shift while reviewing). Just keep saves, saves set to
Not again, and notes are never in the queue. **Keep** →
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
- "Pause on low battery" (default on) only changes the **constraints of future** details work.
- **Export**: `ProfileViewModel.export(json)` builds text from Room, writes `cacheDir/exports/caddis-<date>.json|html`, gets a
  `FileProvider` URI and starts a chooser (`ACTION_SEND`, read grant).
- **Clear cache**: clears Coil memory and disk caches, then refreshes the displayed size.
- **Delete all data** (Profile → Your data → Delete all data, then "Delete it all"): confirm dialog → `repo.deleteAllData()`, `settingsRepo.reset()` (keeps `onboarded`), clear image cache, toast, navigate to Home with the back stack cleared.

## 12. Intents and return rules

- Intents screen: edit name (≤ 20) and Done label (≤ 16); Delete asks twice ("Sure? N saves move to Just keep") via `rememberConfirm`; Just keep is locked
  ("Default, always kept"). New intent card creates one with a weekday-afternoon rule.
- Return rules screen: per intent, day chips (Mon–Sun), time of day chips, max per day (− / +, 1..3). Changes call `repo.updateRule` immediately.
  An intent with no selected days "Never returns".

## 14. First launch

1. `MainViewModel.onboarded` is `false` (the default on a fresh install) → `CaddisRoot` shows `OnboardingScreen`; the splash is held until settings are known.
2. Five intro cards in a pager, then the reminders question. The person swipes either way, taps the main button (Get started / Next / Continue), or
   presses system back to return one card. Nothing is saved or asked on the cards.
3. **Skip** (cards 1–5) → `finishOnboarding(skippedAtStep = n)`: reminders stay as they are (off), `onboarded = true`, Home.
4. On the reminders question, turning a toggle on asks for `POST_NOTIFICATIONS` on Android 13+ (denied → the toggle stays off, no follow-up). **Continue** and
   **Not now** both → `finishOnboarding(null)`, Home.
5. Home shows the first-run state until the first save. A share made before onboarding ends is still saved first (`ShareActivity` is a separate task); the
   intro is shown again at the next launch because `onboarded` is only set on the way out.

## 15. App updates

Google Play's **flexible** in-app update (`com.google.android.play:app-update`); only for installs from Google Play (debug and sideloaded builds just see "no update").

1. `MainActivity` runs `UpdatePrompter.run` whenever it is started (`repeatOnLifecycle(STARTED)`). The share sheet never prompts. Nothing happens until `onboarded`.
2. `AppUpdater.refresh()` asks Play. `UpdateState`: `None`, `Available(versionCode)`, `Downloading`, `Ready`.
3. `Available` and `UpdatePrompts.shouldAsk(...)` → `settings.markUpdateAsked(...)` **first**, then Play's own dialog opens. A new version is asked once at once; the same version
   again only after 7 days. Declining just leaves the update `Available`.
4. Accepting downloads in the background while Caddis stays usable. When it is `Ready` a toast says "Update ready." with **Restart** (once per run of the app, however often the
   app is reopened); Profile keeps a "Restart to update" row. Restart installs and relaunches (`completeUpdate()`).
5. Profile shows "Update Caddis" whenever an update is `Available`, whatever was asked before; tapping it opens Play's dialog (not limited by the 7-day rule).
6. No analytics, no extra permission; Google Play does the checking and downloading.

## 13. Bottom-bar navigation

`navigateToTab(route)` pops to Home with `saveState`, `launchSingleTop`, and `restoreState = !toHome`: leaving a tab saves its stack and
returning restores it, **except Home, which never restores** (as the start destination it would otherwise inherit the stack of the tab you left).
Tapping the tab you are already on is a no-op (the pop-and-restore would rebuild the screen, a visible redraw).
Covered by `TabNavigationTest`.
