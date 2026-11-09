# Architecture

## Modules

```
:domain   pure Kotlin/JVM  — every product rule, unit-tested, no Android
:app      Android          — Room, DataStore, OkHttp, WorkManager, Compose UI, Hilt
```

`settings.gradle.kts` includes both. `:app` depends on `:domain` (`implementation(project(":domain"))`);
`:domain` never depends on `:app`.

**Dependency rule.** `:domain` must not import Android, Room, OkHttp, Hilt or Compose. It may use:
`kotlinx-coroutines-core` (exposed as `api`), `kotlinx-serialization-json` (export only) and
`jsoup` (HTML metadata parsing, pure Java so it is testable on the JVM). Tests use JUnit 5 + Kotest.

**Where new behaviour goes.** Write the rule in `:domain` with tests first, then wire it in `:app`.
The app layer should be adapters (Room, network, scheduling, UI) around domain functions.

## Layers inside `:app`

```
Compose screens  ──observe──►  ViewModels  ──call──►  LibraryRepository ──►  Room (DAOs)
      │                           │                         │  ▲                │
      │                           │                         │  └─ DetailsScheduler ─► WorkManager ─► DetailsWorker ─► DetailsFetcher (OkHttp)
      └── ItemActions (shared) ───┘                         └─ AnalyticsSink (Logcat in debug)
SettingsRepository (DataStore) ◄── ViewModels, workers
```

- **Reads** are cold `Flow`s from Room, combined in `LibraryRepository` / ViewModels and exposed to
  Compose as `StateFlow` (`stateIn`, usually `WhileSubscribed(5_000)` or `Eagerly`).
- **Writes** all go through `LibraryRepository` (the single owner of library writes). It writes
  Room first, then logs analytics and schedules background work.
- **Side effects that must outlive a screen** (saves, undo, item actions, the Edit details save)
  run on the injected application `CoroutineScope`, not `viewModelScope`.

## Package map

### `:domain` (`domain/src/main/kotlin/app/caddis/domain/`)

| Package | Contents |
| --- | --- |
| `model` | `Item`, `Intent`, `IntentRule`, `Collection`, `WeeklySummary`, enums (`Platform`, `ContentKind`, `ItemType`, `ItemStatus`, `IntentSource`, `ReturnRule`, `TimeOfDay`, `EditableField`); `Intents` (defaults, default rules, `ruleForNewIntent`, `analyticsId`); `IntentRule?.label()` |
| `link` | `Links` (check, normalize, extractFirst, couldBeNote, noteTitleFrom, host), `LinkCheck`, `PlatformDetector`/`Detection`, `DefaultTitles` |
| `intent` | `IntentGuesser`, `GuessInput`, `CorrectionStats` |
| `schedule` | `ReturnScheduler`, `HomePicker`/`Pick`, `TidyUp`, `Trash`, `Weeks`, `doneLabel()` (Scheduling.kt); `Reminders`, `ReminderState`, `ReminderDecision`, `QuietHours` (Reminders.kt) |
| `search` | `SearchQuery` (FTS match string, "matched in…" text), `resultBucket()` |
| `details` | `LinkDetails`, `FetchPlan`, `DetailsPlanner`, `DetailsParser`, `Item.mergeDetails`, `formatLength` (Details.kt); `DetailEdits`, `EditLimits`, `Item.withEdits`, `Item.withLink`, `Item.needsDetails` (Edits.kt); `Favicons` |
| `notes` | `Markdown.toPlainText`, `Markdown.preview` |
| `share` | `ShareText` |
| `export` | `Exporter` (JSON + Netscape HTML bookmarks) |
| `analytics` | `AnalyticsEvent` (closed set), `AnalyticsSink`, `ageBucket()` |

Details of every rule: [domain.md](domain.md).

### `:app` (`app/src/main/java/app/caddis/`)

| Package / file | Responsibility |
| --- | --- |
| `CaddisApp.kt` | `@HiltAndroidApp` Application. Startup work, WorkManager config (Hilt worker factory), Coil `ImageLoader` factory. |
| `MainActivity.kt`, `MainViewModel.kt` | Launcher activity (`singleTask`). Splash, edge-to-edge, hands a route extra to the shell; `MainViewModel` exposes `onboarded` (null until DataStore answers), `pendingRoute`, and the toast `Messenger`. |
| `share/ShareActivity.kt` | Translucent share-sheet target. Saves in `onCreate`, shows the capture sheet, then finishes. |
| `data/LibraryRepository.kt` | The only writer of library data; all queries as `Flow`. Also `DetailsScheduler` interface, `SaveOutcome`, `DetailsResult`, `NameResult`. |
| `data/SettingsRepository.kt` | DataStore `Preferences` wrapper; `Settings` data class. |
| `data/db/` | `CaddisDatabase`, entities (`Entities.kt`), DAOs (`Daos.kt`), `Mappers.kt` (entity ↔ domain, weekday bitmask), `Converters.kt`. |
| `data/details/` | `DetailsFetcher` (OkHttp, never throws), `UserAgentInterceptor`. |
| `work/Work.kt` | `WorkScheduler` (implements `DetailsScheduler`, schedules reminders), `DetailsWorker`, `ReminderWorker`. |
| `notify/Notifier.kt` | Notification channels; daily pick and weekly summary notifications; permission check. |
| `di/AppModule.kt` | Hilt: DB, DataStore, `Clock`, app `CoroutineScope`, `OkHttpClient`, `AnalyticsSink`; `BindingsModule` binds `DetailsScheduler → WorkScheduler`. |
| `ui/` | `ItemActions` (shared item actions), `Messenger` (toasts), `Motion.kt` (animation vocabulary). Sub-packages below. |
| `ui/shell/` | `CaddisRoot`, `CaddisShell`, `BottomBar` (+ `Tab`), `ToastHost`. |
| `ui/nav/Navigation.kt` | The route table (`CaddisNavHost`), `HOME_ROUTE`, `navigateToTab`. |
| `ui/home/`, `ui/library/`, `ui/item/`, `ui/capture/`, `ui/manage/`, `ui/profile/`, `ui/sheets/` | Screens and their ViewModels. See [ui.md](ui.md). |
| `ui/components/` | Shared composables (`Components.kt`, `Fields.kt`, `Icons.kt`, `Sheet.kt`). |
| `ui/theme/Theme.kt` | `CaddisColors`, light/dark palettes, fonts, `CaddisType`, `C.colors`, `CaddisTheme`. |

## Dependency injection (Hilt)

`AppModule` (`SingletonComponent`):

| Provides | Notes |
| --- | --- |
| `CaddisDatabase` | `Room.databaseBuilder(..., "caddis.db").addMigrations(*MIGRATIONS)`. No destructive fallback. |
| `DataStore<Preferences>` | File `settings` (`preferencesDataStoreFile("settings")`). |
| `Clock` | `Clock.systemDefaultZone()`. **Inject `Clock` everywhere; never call `Instant.now()` in logic** (tests substitute `MutableClock`). |
| `CoroutineScope` | App-wide `SupervisorJob() + Dispatchers.Default`. Outlives screens. |
| `OkHttpClient` | connect 10 s, read 15 s, call 30 s, follows redirects, `UserAgentInterceptor`. Shared by `DetailsFetcher` **and Coil** (`CaddisApp.newImageLoader`). |
| `AnalyticsSink` | Logs `name + params` to Logcat tag `CaddisAnalytics` when `BuildConfig.DEBUG`; no-op in release. |

`BindingsModule` binds `DetailsScheduler` to `WorkScheduler` so `LibraryRepository` can request
background fetches without knowing about WorkManager (tests pass a recording fake).

Singletons: `LibraryRepository`, `SettingsRepository`, `DetailsFetcher`, `WorkScheduler`,
`Notifier`, `ItemActions`, `Messenger`. ViewModels are `@HiltViewModel`; workers are `@HiltWorker`.

## Threads and scopes

| Scope | Used for |
| --- | --- |
| `viewModelScope` | Screen-local work that may be cancelled with the screen (filters, debounced drafts, loads). |
| App `CoroutineScope` | Anything that must finish even if the screen/sheet closes: saves from the share sheet, `ItemActions`, last edits (`setTitle`, `editTitle`, final reason), Edit details save, `WorkScheduler.schedule`, startup tasks. |
| WorkManager | Details fetching (`DetailsWorker`), hourly reminder check (`ReminderWorker`). |
| `Dispatchers.IO` | `DetailsFetcher.fetch`, export file writing, cache size/clear. |

Room suspend DAO calls and `withTransaction` are used for multi-row consistency (saves + weekly
counters, done/delete + counters, intent deletion + reassignment, delete-all).

## Startup sequence

1. `CaddisApp.onCreate`: `notifier.createChannels()`, `work.scheduleReminders()` (unique periodic
   hourly work, `KEEP`), then on the app scope: `repo.seedDefaultsIfNeeded()` (only if there are no
   intents), `repo.purgeExpiredTrash()`, `repo.scheduleMissingDetails()` (retry links that never
   got details; skips Instagram/Facebook).
2. WorkManager auto-init is **disabled** in the manifest (`androidx.work.WorkManagerInitializer`
   removed); `CaddisApp` implements `Configuration.Provider` with `HiltWorkerFactory`.
3. `MainActivity.onCreate`: `installSplashScreen()`, `enableEdgeToEdge()`, keeps the splash until
   `MainViewModel.onboarded` is non-null, fades the splash out over `Motion.Short`, reads the
   `route` string extra into `MainViewModel.pendingRoute`, then `setContent { CaddisRoot(...) }`.
4. `CaddisRoot` shows `OnboardingScreen` when `onboarded == false`, else `CaddisShell`.
5. `CaddisShell` navigates to `pendingRoute` once (then clears it) and forwards `CaptureNav`
   events (open note / open earlier save) from `CaptureViewModel`.

## Android entry points (manifest)

| Component | Details |
| --- | --- |
| `MainActivity` | Launcher. `launchMode=singleTask`, theme `Theme.Caddis.Splash`, `adjustResize`. `onNewIntent` accepts a `route` extra. |
| `ShareActivity` | Exported; `ACTION_SEND` + `text/plain` only. `launchMode=standard`, `taskAffinity=""`, `excludeFromRecents`, theme `Theme.Caddis.Share` (translucent). |
| Route extras | `MainActivity.EXTRA_ROUTE = "route"`; `ROUTE_ITEM_PREFIX = "item/"`; `ROUTE_WEEK = "home?week=true"`. Notifications and `ShareActivity` hand routes this way. Notes use `note/<id>`. |
| Permissions | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested at runtime on API 33+ only when a reminder is turned on). |
| `<queries>` | `VIEW https` (to open links in the original app) and `MAIN/LAUNCHER` (to resolve the sharing app's label). |
| `FileProvider` | `${applicationId}.files`, path `cache-path exports/` — used to hand export files to the share sheet. |
| Backup | `allowBackup=true`; rules include database `caddis.db` and the `sharedpref` domain. |

## Background work

| Work | Scheduling | Behaviour |
| --- | --- | --- |
| `DetailsWorker` | `WorkScheduler.schedule(itemId)`: unique name `details-<id>`, `ExistingWorkPolicy.REPLACE`, exponential backoff 30 s. Constraints from settings: network `UNMETERED` if "pause on mobile data" else `CONNECTED`; battery-not-low if "pause on low battery" (default on). | Loads the item; fetches; `Ok` → `repo.applyDetails(..., forUrl)`, `GiveUp` → `repo.markDetailsAttempted`, `Retry` → `Result.retry()` up to `MAX_ATTEMPTS = 4`, then success without marking (next app start retries). |
| `ReminderWorker` | `WorkScheduler.scheduleReminders()`: unique periodic **every 1 hour**, battery-not-low, `KEEP`. | If neither reminder is on, return. Otherwise asks `HomePicker` for the top pick and `Reminders.decide(...)`; posts via `Notifier`; records the send date in settings only if the notification was actually posted. |

## Image loading

Coil 3 with the shared OkHttp client (`OkHttpNetworkFetcherFactory`), crossfade `Motion.Short`, disk
cache in `cacheDir/thumbnails` capped at 200 MB (`CaddisApp.THUMB_CACHE_BYTES`). Only thumbnails
and site icons are cached; original media is never downloaded or re-hosted.

## Build configuration summary

See [development.md](development.md) for commands. Key facts: AGP 8.13.2, Kotlin 2.2.20, KSP,
Hilt, Room with `exportSchema` to `app/schemas`, JVM toolchain 17, Compose BOM 2025.06.00,
release builds minified + resource-shrunk (`app/proguard-rules.pro` keeps the export serializers).
