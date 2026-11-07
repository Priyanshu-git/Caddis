# Requirements status

State of every PRD requirement in this codebase. Update a row in the same change that moves it.

**Built** = implemented and covered by tests where logic is involved. **Partial** = works with a
noted gap. **Deferred** = waits for the sync/account phase. **Later** = PRD priority Later.

## Capture

| ID | Status | Where / notes |
| --- | --- | --- |
| CAP-01 | Built | `ShareActivity` saves in `onCreate`; `LibraryRepository.saveLink` |
| CAP-02 | Built | Save sheet never blocks; dismiss keeps the save |
| CAP-03 | Built | Row first, details via `DetailsWorker` with a network constraint |
| CAP-04 | Built | Clipboard read only on tapping +, shown as a suggestion |
| CAP-05 | Built | Notes from Add, and shared text without a link becomes a note |
| CAP-06 | Built | `Links.normalize` + `findByNormalizedUrl`; "Save again" keeps both |
| CAP-07 | Built | Source app from the share referrer; optional "Who sent it" field |
| CAP-08 | Later | Widget |

## Intent

| ID | Status | Where / notes |
| --- | --- | --- |
| INT-01 | Built | `Intents.defaults` + custom intents |
| INT-02 | Built | `IntentGuesser`; one-tap change on the save sheet and item page; corrections shift guesses |
| INT-03 | Built | "A thought for future you" on the save sheet; editable on the item page |
| INT-04 | Built | `Intents.defaultRules`; Return rules screen |
| INT-05 | Built | Create / rename / delete, Done label; delete moves saves to Just keep; new intents weekday afternoons |

## Bring it back

| ID | Status | Where / notes |
| --- | --- | --- |
| BRB-01 | Built | `HomePicker` |
| BRB-02 | Built | `returnAt` excludes until the date |
| BRB-03 | Built | Tonight, this weekend, next week, date picker |
| BRB-04 | Built | Opt-in, one a day shared with the weekly summary, quiet hours (`Reminders`) |
| BRB-05 | Later | Location-based return |

## Finish and delete

| ID | Status | Where / notes |
| --- | --- | --- |
| FIN-01 | Built | Done button uses the intent's label |
| FIN-02 | Built | 30-day Trash, restore, delete forever; purge on app open |
| FIN-03 | Built | Tidy-up card at ≥1 stale save; one at a time, keep or delete |
| FIN-04 | Built | Done stays in Library and search, leaves Home |

## This week

| ID | Status | Where / notes |
| --- | --- | --- |
| WK-01 | Built | Stored per-week counters |
| WK-02 | Built | Sunday evening notification, opt-in |

## Search

| ID | Status | Where / notes |
| --- | --- | --- |
| SRC-01 | Built | FTS4 over title, description, creator, URL, note, reason, source app, from |
| SRC-02 | Built | Prefix keywords, any order, no interpretation |
| SRC-03 | Built | Room on the phone |
| SRC-04 | Built | Intent chips + Filter sheet (intent, platform, status) |

## Library

| ID | Status | Where / notes |
| --- | --- | --- |
| LIB-01 | Built | Newest first, intent chips |
| LIB-02 | Built | Collections switch, manage screen, many per item |
| LIB-03 | Built | Platform filter |
| LIB-04 | Built | Item page |
| LIB-05 | Built | `ACTION_VIEW` lets Android open the platform app via app links, else the browser |

## Previews and details

| ID | Status | Where / notes |
| --- | --- | --- |
| MET-01 | Built | oEmbed (YouTube, X), page metadata (Reddit, Medium, Web); Meta never called |
| MET-02 | Built | Save sheet updates live from Room |
| MET-03 | Partial | Edited fields are protected and Refresh exists; only the note title is editable in the UI so far |
| MET-04 | Built | Coil disk cache capped at 200 MB, clearable; thumbnails only |
| MET-05 | Built | Merge never blanks a known field; failures keep what we have |

## Notes

| ID | Status | Where / notes |
| --- | --- | --- |
| NOTE-01 | Partial | Visual editor stored as Markdown; no heading/quote buttons yet (see architecture.md) |
| NOTE-02 | Built | Standalone notes take an intent and collections |

## Account and data

| ID | Status | Where / notes |
| --- | --- | --- |
| ACC-01 | Deferred | Google sign-in and sync wait for the cloud phase |
| ACC-02 | Built | JSON and HTML bookmarks via the share sheet |
| ACC-03 | Partial | "Delete all data" on this phone; account deletion comes with accounts |
| ACC-04 | Partial | Profile, background work, privacy summary, account settings; full-policy link not published yet |

## Non-functional

| Area | Status | Notes |
| --- | --- | --- |
| Offline | Built | Everything but opening the original and fetching details |
| Reliability | Built | Saves run on the app scope inside a transaction; unique work per item |
| Privacy | Built | No analytics backend; event types carry no content |
| Battery and data | Built | Scheduler constraints follow the two Profile toggles |
| Accessibility | Partial | 44 dp+ targets, labels, switch/tab roles, dark mode; needs a TalkBack pass |
| Speed targets | Not measured | Needs the first performance pass on a mid-range phone |
