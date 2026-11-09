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
| `link` | `Links.check/normalize/extractFirst`, `couldBeNote`/`noteTitleFrom` (typed text that is not a link becomes a note), `PlatformDetector` (platform, kind, handle from the URL), `DefaultTitles` (readable title from the URL alone, used until a real one arrives) | CAP-01/04/06, MET-01/05 |
| `intent` | `IntentGuesser` — keyword rules, then local correction counts, then platform default | INT-02, tech arch §5 |
| `schedule` | `HomePicker` (≤3 picks), `ReturnScheduler` (later options), `TidyUp`, `Trash`, `Weeks`, `Reminders` + `QuietHours` | BRB-01–04, FIN-02/03, WK-01/02 |
| `search` | `SearchQuery.toFtsMatch/explain`, result buckets | SRC-01/02 |
| `details` | `DetailsPlanner` (what to fetch; never Meta), `DetailsParser` (OG tags, oEmbed, YouTube length, reading time; drops bot-check pages and bare site names), `mergeDetails` (never overwrites edits), `withEdits` (the Edit details sheet: a changed field becomes yours, an emptied one goes back to Caddis), `withLink` (a new link re-reads platform and kind, drops what was fetched for the old one, keeps your edits), `Favicons` (site icon fallback; none for Meta) | MET-01–05 |
| `notes` | `Markdown.toPlainText/preview` | NOTE-01 |
| `share` | `ShareText` — the text the item page's Share sends: real title (if any), the link without tracking ids, then "Shared via Caddis app"; never the reason, note, intent or collections | LIB-04, private by default |
| `export` | `Exporter.toJson`, `toHtmlBookmarks` (Netscape format) | ACC-02 |
| `analytics` | `AnalyticsEvent` — the only allowed events, typed so no content can be attached; `AnalyticsSink` port | tech arch §1.3 |

### :app packages

| Package | What it holds |
| --- | --- |
| `data.db` | Room: `items` (+ `items_fts` FTS4 external-content index), `intents`, `intent_rules`, `collections`, `item_collections`, `weekly_summaries`, `intent_corrections`. Schema exported to `app/schemas/`; version 2 added `items.faviconUrl` (`MIGRATION_1_2`, tested in `MigrationTest`). |
| `data` | `LibraryRepository` (the single owner of library writes), `SettingsRepository` (DataStore) |
| `data.details` | `DetailsFetcher` — OkHttp, reads at most 512 KB, classifies Retry / GiveUp, never throws; `UserAgentInterceptor` names Caddis on every request without its own User-Agent, including Coil thumbnails |
| `work` | `WorkScheduler` (implements `DetailsScheduler`), `DetailsWorker` (unique per item, backoff), `ReminderWorker` (hourly, decides via `Reminders`) |
| `notify` | `Notifier` — channels, daily pick and weekly summary notifications |
| `share` | `ShareActivity` — translucent share target; saves in `onCreate` before drawing |
| `ui` | Compose screens, `ItemActions` (done/delete/later/open with Undo toasts, share via the system chooser), `Messenger` (toasts) |
| `ui.shell` | The app frame that `MainActivity` hosts: `CaddisRoot` (theme and the onboarding-to-app fade), `CaddisShell` (nav host, bottom bar, add button, capture sheets), `BottomBar` + `Tab`, `ToastHost` |
| `ui.nav` | `Navigation.kt` — the route table (`CaddisNavHost`, shared-element layer, transitions), `HOME_ROUTE`, `navigateToTab` |
| `ui.theme` | Prototype tokens (light + dark), Bricolage Grotesque + DM Sans (variable fonts in `res/font`) |
| `di` | Hilt `AppModule` (DB, DataStore, clock, app scope, OkHttp, analytics) |

## Key flows

**Save from the share sheet** (CAP-01/02/03): `ShareActivity.onCreate` → `CaptureViewModel.fromShare`
on the app-wide scope → `LibraryRepository.saveLink` writes the row and bumps the week's saved
count in one transaction → schedules `DetailsWorker` (not for Instagram/Facebook) → the save sheet
observes the row and fills in as details arrive. Dismissing the sheet only flushes the reason.

**Details** (MET-01–05): `DetailsWorker` → `DetailsFetcher` (public oEmbed for YouTube, X and Reddit,
page metadata for the rest; no API keys, no login, no third-party proxy) → `applyDetails` merges
without touching `userEdited` fields or blanking known fields, and re-guesses the intent only while
it is still a guess. Network constraint plus the person's battery / mobile-data choices. Missing
details are re-queued on every app start.

When a source refuses (Reddit and Medium often serve a bot check to non-browsers), is offline, or is
never called (Instagram, Facebook), the save keeps no title and `Item.displayTitle` falls back to
`DefaultTitles`: for example "Instagram reel by @name", "Post by @name", or the words in a Reddit or
Medium URL slug. The real title replaces it as soon as one is fetched.

**Thumbnails** (MET-04): `Thumb` shows the fetched thumbnail; without one (or when it fails to load)
it shows the site's icon (`Favicons.forItem`: the icon the page declared, else `https://<host>/favicon.ico`),
then a glyph. Instagram and Facebook always get the glyph, since loading their icon would call Meta.

**Editing** (MET-03): the item page's pencil opens `EditDetailsSheet` (link, title, by, description) →
`LibraryRepository.saveDetails`, which checks the link (bad links and links another save already
has are refused on the sheet), then applies `withEdits` and `withLink` in one transaction. When the
link points somewhere new, the intent is re-guessed while it is still a guess and details are
fetched again (never for Instagram or Facebook). `DetailsWorker` passes the URL it fetched, and
details for a link that has since changed are dropped. The save sheet has
an "Edit title" field that commits through `editTitle` when the sheet closes.

**Add a link** (CAP-04/05): if what was typed is not a web link, the Add sheet asks "Save as a note
instead?" and `CaptureViewModel.saveTypedAsNote` saves it (first line as the title).

**Bottom bar**: `navigateToTab` saves each tab's stack and restores it on return, except Home,
which never restores: as the start destination it inherits the stack of the tab you just left
(`TabNavigationTest`).

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
| 9, 10 | Bring back later, change intent, edit details | `ui/sheets/Sheets.kt` |
| 11, 13, 17, 18 | Library, filters, search | `ui/library/LibraryScreen.kt` |
| 12 | Collection shelf | `ui/library/ShelfScreen.kt` |
| 14, 19, 20 | Collections, tidy-up, Trash | `ui/manage/ManageScreens.kt` |
| 15, 16 | Item page, note editor | `ui/item/` |
| 22–29 | Profile, notifications, rules, intents, data, account, privacy | `ui/profile/ProfileScreens.kt` |

## Motion

All movement lives in `ui/Motion.kt`: shared durations and easings (short, decelerating, never
bouncy), `navMotion(from, to, pop)` which picks a fade-through for tabs and a shared-axis slide for
going deeper or back (used by the `NavHost` in `MainActivity.kt`, so predictive back scrubs it),
`calmItem()` for list rows, `pressScale` for buttons/chips/cards, and `sharedThumb` which makes a
list thumbnail grow into the item header (`OpenedItem` tells the item screen which list it came from
and hands it the item for its first frame). Cold start holds the AndroidX splash until settings are
known (`Theme.Caddis.Splash`). Compose follows the system animator scale, so turning animations off
in Android settings makes everything instant. Tests: `MotionTest`.

Screen 1 (Google sign-in) is not built in the client-only phase; Account settings says so and
offers "Delete all data" in place of account deletion.

## Known limits

- The notes editor (`richeditor-compose` 1.0.0-rc13, chosen to stay on Kotlin 2.2) supports bold,
  italic, bulleted and numbered lists and inline code. Headings and quotes imported from Markdown
  render, but have no toolbar button yet.
- Recent searches are kept in memory only.
- X and Reddit details come from public oEmbed endpoints; if one stops answering, those saves fall
  back to the URL and handle (Reddit: the URL slug). Reddit pages usually refuse non-browsers, so
  Reddit saves rarely get a thumbnail. Medium articles fetch when Medium's bot check lets the phone
  in, otherwise they show the slug title.
