# Caddis code architecture

How the code is laid out today. Product rules: [prd.md](prd.md). Technical decisions:
[technical-architecture.md](technical-architecture.md). Keep this file current when you add a
module, table, worker or screen.

## Phase

Client only. Room on the phone is the only store; there is no sign-in, sync, Firebase or analytics
backend yet. The data model already carries what sync needs (`revision`, `updatedAt`,
`userEdited`), and analytics go through a port that logs locally.

## Modules

```
:domain   pure Kotlin/JVM — every product rule, fully unit-tested
:app      Android: Room, DataStore, OkHttp, WorkManager, Compose UI, Hilt
```

`:domain` never imports Android, Room, OkHttp or Compose. It does use Jsoup (pure Java) so page
metadata parsing is testable on the JVM.

### :domain packages

| Package | What it holds | PRD |
| --- | --- | --- |
| `model` | `Item`, `Intent`, `IntentRule`, `Collection`, `WeeklySummary`, enums; `Intents` defaults and rules | INT-01/04/05, data model |
| `link` | `Links.check/normalize/extractFirst`, `PlatformDetector` (platform, kind, handle from the URL) | CAP-01/04/06, MET-01 |
| `intent` | `IntentGuesser` — keyword rules, then local correction counts, then platform default | INT-02, tech arch §5 |
| `schedule` | `HomePicker` (≤3 picks), `ReturnScheduler` (later options), `TidyUp`, `Trash`, `Weeks`, `Reminders` + `QuietHours` | BRB-01–04, FIN-02/03, WK-01/02 |
| `search` | `SearchQuery.toFtsMatch/explain`, result buckets | SRC-01/02 |
| `details` | `DetailsPlanner` (what to fetch; never Meta), `DetailsParser` (OG tags, oEmbed, YouTube length, reading time), `mergeDetails` (never overwrites edits) | MET-01–05 |
| `notes` | `Markdown.toPlainText/preview` | NOTE-01 |
| `export` | `Exporter.toJson`, `toHtmlBookmarks` (Netscape format) | ACC-02 |
| `analytics` | `AnalyticsEvent` — the only allowed events, typed so no content can be attached; `AnalyticsSink` port | tech arch §1.3 |

### :app packages

| Package | What it holds |
| --- | --- |
| `data.db` | Room: `items` (+ `items_fts` FTS4 external-content index), `intents`, `intent_rules`, `collections`, `item_collections`, `weekly_summaries`, `intent_corrections`. Schema exported to `app/schemas/`. |
| `data` | `LibraryRepository` (the single owner of library writes), `SettingsRepository` (DataStore) |
| `data.details` | `DetailsFetcher` — OkHttp, reads at most 512 KB, classifies Retry / GiveUp |
| `work` | `WorkScheduler` (implements `DetailsScheduler`), `DetailsWorker` (unique per item, backoff), `ReminderWorker` (hourly, decides via `Reminders`) |
| `notify` | `Notifier` — channels, daily pick and weekly summary notifications |
| `share` | `ShareActivity` — translucent share target; saves in `onCreate` before drawing |
| `ui` | Compose screens, `ItemActions` (done/delete/later/open with Undo toasts), `Messenger` (toasts) |
| `ui.theme` | Prototype tokens (light + dark), Bricolage Grotesque + DM Sans (variable fonts in `res/font`) |
| `di` | Hilt `AppModule` (DB, DataStore, clock, app scope, OkHttp, analytics) |

## Key flows

**Save from the share sheet** (CAP-01/02/03): `ShareActivity.onCreate` → `CaptureViewModel.fromShare`
on the app-wide scope → `LibraryRepository.saveLink` writes the row and bumps the week's saved
count in one transaction → schedules `DetailsWorker` (not for Instagram/Facebook) → the save sheet
observes the row and fills in as details arrive. Dismissing the sheet only flushes the reason.

**Details** (MET-01–05): `DetailsWorker` → `DetailsFetcher` (oEmbed for YouTube/X, page metadata for
the rest) → `applyDetails` merges without touching `userEdited` fields or blanking known fields,
and re-guesses the intent only while it is still a guess. Network constraint plus the person's
battery / mobile-data choices. Missing details are re-queued on every app start.

**Home** (BRB-01/02): `HomeViewModel` combines items, rules, intents, the week's counts and a
5-minute tick → `HomePicker.pick`. Picks are marked surfaced once per day per item.
A "later" date gets one full day on Home, then the item falls back to its intent rule.

**Search** (SRC-01–03): `SearchQuery.toFtsMatch` turns words into prefix terms (implicit AND) →
`ItemDao.search` joins `items_fts` on rowid. Fully offline.

**Trash** (FIN-02): delete sets `status = DELETED`, `deletedAt`; `CaddisApp.onCreate` purges rows
older than 30 days.

**Weekly summary** (WK-01): counters in `weekly_summaries` are adjusted inside the same
transaction as each save / done / delete (and their undo), so past weeks stay as they were.

## Screens (prototype numbers)

| # | Screen | File |
| --- | --- | --- |
| 2 | Notifications onboarding | `ui/profile/ProfileScreens.kt` `OnboardingScreen` |
| 3, 4, 5 | Save sheet, duplicate, Add | `ui/capture/CaptureSheets.kt` |
| 7, 8, 21 | Home, all clear, This week | `ui/home/HomeScreen.kt` |
| 9, 10 | Bring back later, change intent | `ui/sheets/Sheets.kt` |
| 11, 13, 17, 18 | Library, filters, search | `ui/library/LibraryScreen.kt` |
| 12 | Collection shelf | `ui/library/ShelfScreen.kt` |
| 14, 19, 20 | Collections, tidy-up, Trash | `ui/manage/ManageScreens.kt` |
| 15, 16 | Item page, note editor | `ui/item/` |
| 22–29 | Profile, notifications, rules, intents, data, account, privacy | `ui/profile/ProfileScreens.kt` |

Screen 1 (Google sign-in) is not built in the client-only phase; Account settings says so and
offers "Delete all data" in place of account deletion.

## Known limits

- The notes editor (`richeditor-compose` 1.0.0-rc13, chosen to stay on Kotlin 2.2) supports bold,
  italic, bulleted and numbered lists and inline code. Headings and quotes imported from Markdown
  render, but have no toolbar button yet.
- Recent searches are kept in memory only.
- X details come from the public oEmbed endpoint; if it stops answering, X saves fall back to
  the URL and handle.
