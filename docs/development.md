# Development

## Requirements

- JDK 17+ (both modules use `jvmToolchain(17)`), Android SDK 36 (`compileSdk`/`targetSdk` 36, `minSdk` 28).
- Windows: use `gradlew.bat`, or `./gradlew` from Git Bash. Gradle config: `org.gradle.jvmargs=-Xmx3072m`, parallel + build cache on.

## Commands

```bash
./gradlew :domain:test              # pure-Kotlin domain tests (JUnit 5 + Kotest)
./gradlew :app:testDebugUnitTest    # app unit + Robolectric tests (Room, repository, ViewModels, fetcher)
./gradlew test                      # both
./gradlew :app:connectedDebugAndroidTest   # instrumented tests on a connected device/emulator (journeys; not run in CI)
./gradlew assembleDebug             # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug            # Android lint
./gradlew test assembleDebug        # what CI runs (CI also runs :app:lintDebug)
```

Run `./gradlew test` before committing (AGENTS.md rule).

## CI (`.github/workflows/ci.yml`)

On push to `main` and on pull requests, `ubuntu-latest`, Temurin 17, `gradle/actions/setup-gradle@v4`:
`./gradlew test --stacktrace` → `./gradlew assembleDebug` → `./gradlew :app:lintDebug`.

## Versions (`gradle/libs.versions.toml` is the only place for dependency versions)

AGP 8.13.2 · Kotlin 2.2.20 · KSP 2.3.6 · Compose BOM 2025.06.00 · Material 3 · Navigation 2.9.2 · Hilt 2.57.1 (+ androidx.hilt 1.3.0) ·
Room 2.8.3 · DataStore 1.1.7 · WorkManager 2.10.5 · Coroutines 1.10.2 · kotlinx-serialization 1.8.1 · OkHttp 4.12.0 · Jsoup 1.18.3 ·
Coil 3.3.0 · richeditor-compose 1.0.0-rc13 · Lifecycle 2.9.1 · Activity Compose 1.10.1 · splashscreen 1.0.1.
Test: JUnit 4.13.2 (app), JUnit 5.11.4 + Kotest 5.9.1 (domain), Robolectric 4.14.1, Turbine 1.2.0, MockWebServer 4.12.0,
work-testing, navigation-testing, room-testing. Instrumented (`app/src/androidTest`): androidx test runner/core/ext-junit, Compose UI test (`ui-test-junit4`),
Espresso core + intents, `hilt-android-testing`, room-testing. The catalog also lists `mockk`, which no source set currently uses.

Plugins applied in `:app`: `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt`, `room` (schema dir `app/schemas`), plus `google-services` and `firebase-crashlytics` **only when `app/google-services.json` exists**
(download it from the Firebase console for `com.nexxlabs.caddis`; it is gitignored, so CI builds without Crashlytics).
`:domain`: `kotlin.jvm`, `kotlin.serialization`. Release builds: `isMinifyEnabled`, `isShrinkResources` (see `app/proguard-rules.pro`).

## Tests

### `:domain` (`domain/src/test/kotlin/…`)

JUnit 5 (`org.junit.jupiter.api.Test`) with Kotest matchers (`shouldBe`). Pure functions; pass times explicitly (see `RemindersTest`,
`SchedulingTest` which use fixed `ZonedDateTime`s). Files:

| Test file | Covers |
| --- | --- |
| `link/LinksTest` | check, normalize, extractFirst, platform detection |
| `link/DefaultTitlesTest` | readable fallback titles, slugs, `displayTitle` |
| `intent/IntentGuesserTest` | keyword order, defaults, corrections threshold, deleted intents |
| `schedule/SchedulingTest` | `ReturnScheduler`, `HomePicker` (incl. the Just keep reminder), `TidyUp` (what is never offered), `Trash`, `Weeks`, rule labels |
| `onboarding/OnboardingTest` | page order, back/next at the ends, skip, main-button label, progress step, the finished event |
| `schedule/RemindersTest` | opt-in, one a day, quiet hours, weekly on Sunday evening |
| `details/DetailsTest` | site name, kind and oEmbed discovery from a page, oEmbed parsing, generic titles, `mergeDetails`, `formatLength` |
| `details/EditsAndIconsTest` | `withEdits`, `withLink`, favicon parsing/fallbacks/merge, `couldBeNote` |
| `share/ShareTextTest` | share text, tracking-param stripping, private words never shared |
| `notes/NoteMarkdownTest` | editor HTML ⇄ stored Markdown: paragraphs, lists, marks, escaping of every typed character, spaces, old-format notes |
| `notes/NoteRoundTripPropertyTest` | save-and-reopen keeps every character's styles: all bold/italic sequences of up to 4 pieces (~70,000), 60,000 seeded random notes with every style, special character, list and empty line |
| `MiscTest` | search query, Markdown (plain text/preview), JSON/HTML export, analytics buckets |

### `:app` (`app/src/test/java/…`)

JUnit 4. Robolectric config in `app/src/test/resources/robolectric.properties` (`sdk=34`, plain `Application`).

- `TestSupport.kt`: `MutableClock` (movable `Clock`, default zone `Asia/Kolkata`), `RecordingAnalytics` (collects events),
  `RecordingScheduler` (collects scheduled item ids), and **`Fixture`**: fixed clock (Wed 4 Nov 2026 11:08 IST), in-memory Room
  (`allowMainThreadQueries`) and a real `LibraryRepository` wired with the fakes. Use `Fixture` for any repository/ViewModel test; call `close()`.
- `data/LibraryRepositoryTest`: seeding, saves, duplicates, search, done/undo counters, trash/restore/purge, intent corrections, details merging,
  intent deletion, collections, bring-back/surfacing, export/delete-all, edit-details and link-change behaviour.
- `data/DetailsFetcherTest`: `MockWebServer` (no real network). Page parsing and site names, retry vs give-up, Instagram/Facebook fetched like any site, bot-check pages, advertised oEmbed + page,
  User-Agent behaviour.
- `ui/CaptureViewModelTest`, `ui/ItemActionsShareTest`, `TabNavigationTest` (uses `navigation-testing`), `MotionTest`.
- `ui/NoteEditorRoundTripTest`: the real editor state (Robolectric) loading stored notes through `NoteMarkdown` and saving them again: typed special characters, bold and
  italic, lists, empty lines, spaces and notes written by earlier versions all come back unchanged. It also plays every order of switching bold and italic on and off
  while typing (~55,000 sequences, the real `toggleSpanStyle`/`addTextAfterSelection`) and bold/italic applied to selections afterwards, and compares the style of every character.
- `ui/ItemViewModelNoteTest`: note autosave (Saving…, one write per pause, bursts collapse, undo within the pause writes nothing, flush on leaving,
  clearing, `currentNote()` preferring a pending draft). Waits in real time for Room; the ViewModel's scopes are cancelled in `tearDown`.

Test conventions: backticked sentence names; one behaviour per test; no network; inject the clock.

### `:app` instrumented (`app/src/androidTest/java/…`)

Run on a device or emulator (`./gradlew :app:connectedDebugAndroidTest`; one class: `-Pandroid.testInstrumentationRunnerArguments.class=com.nexxlabs.caddis.journeys.HomeJourneyTest`).
CI does not run them (it has no emulator). The runner is `com.nexxlabs.caddis.HiltTestRunner`, which starts `HiltTestApplication`, so `CaddisApp.onCreate`
(channels, reminders work, seeding) does **not** run; the base test seeds the default intents itself.

- `TestModule.kt` (`@TestInstallIn` replacing `AppModule` and `BindingsModule`): in-memory Room, a new DataStore file per test, a plain `OkHttpClient`
  (nothing should reach the network), `RecordingAnalytics`, and:
  - **`TestClock`**: a movable `Clock`, Wed 4 Nov 2026 11:08 IST, so Home's day and time-of-day rules are the same on every run. Move it with `daysLater(n)`.
  - **`FakeDetails`** (the `DetailsScheduler`): no WorkManager and no network. By default a fetch "gives up", so a save shows the title built from its link.
    `serve(url, LinkDetails(…))` makes details arrive, `hold()`/`release()` keep them waiting (to see the skeleton).
  - **`FakeUpdater`** (the `AppUpdater`): nothing waits until a test calls `offer(versionCode)`; opening the dialog (`start`) begins the download, `downloaded()` finishes it,
    `starts` / `restarts` count what the app asked for.
- `journeys/JourneyTest`: base class. `HiltAndroidRule` + `createEmptyComposeRule`, grants `POST_NOTIFICATIONS` up front (no system dialog), and gives
  `launchApp(route?)`, data helpers (`save`, `saveNote`, `library()`, `item()`, `trash()`), finders (`button`, `tab`, `field(label)`, `described`) and waiters
  (`awaitText`, `await`, `eventually`). Failures print the on-screen semantics tree.
- One class per journey (all `@HiltAndroidTest`): `OnboardingJourneyTest`, `AddJourneyTest` (plus button, clipboard, duplicate, note), `ShareSheetJourneyTest`
  (`ShareActivity`), `HomeJourneyTest`, `LibraryJourneyTest` (search, filters, collections), `ItemJourneyTest` (item page, edit details, note editor),
  `TrashAndTidyJourneyTest`, `ProfileJourneyTest` (notifications, rules, intents, export, delete all), `NavigationJourneyTest`,
  `UpdateJourneyTest` (Google Play update offer, Profile row, restart), `NoteEditorJourneyTest` and `NoteLifecycleJourneyTest` (below).
- `journeys/NoteJourneyBase`: base for the note journeys. It types through the editor's own `InputConnection` one call at a time, like a keyboard
  (`commit`, `composing`, `enter`, `backspace`, `select`, `deleteSelection`, `typeSlowly`), opens notes (`openNote`, `openEditor`, `start`), leaves
  them (`leaveEditor` closes the keyboard first, as the first Back only hides it) and reads back `shown()`/`visible()`, `lit("Bold")` (toolbar
  button selected) and `assertStored(id, markdown)`. Quirks to know: the editor does not keep the keyboard's composing region between calls, and a list
  button rewrites the text, so the helper reconnects after list toggles. Selection offsets are in the editor's text (line breaks count as one
  character, list items start with "• ").
- `NoteEditorJourneyTest`: Return in every flavour (after a word, while composing, as a newline character, with the word, twice, mid-word, in lists,
  after bold) never repeats a line; formatting kept as Markdown; the toolbar lighting up with the caret; editing saved notes (append, insert, delete,
  replace, clear, bold text, long text, emoji), typed `* _ # < [ -` and spaces stored escaped and reopened as typed. `NoteLifecycleJourneyTest`: creating from the Add sheet, autosave, leaving and reopening, screen turn
  right after typing, title, intent, collections, notes on links (item page preview), finding a note in the Library.

Things to know when writing more:
- The compose test clock only moves when the test lets it. `await*` and `eventually` do; a bare `Thread.sleep` does not (an activity that finishes from a
  `LaunchedEffect` will look stuck).
- `repo.get(id)` returns an item **without** its collection ids; use `library()` to check collection membership.
- Taps on `CButton`/chips are found by text + `Role.Button` (`button("Done")`); the intent chips in the save sheet and the Done button can share a word.
  `Eyebrow` text is upper-cased on screen (`"WHY YOU KEPT IT"`).
- Opening a link or the share chooser must be stubbed with Espresso Intents (`Intents.init()` + `intending(...)`), or a real browser opens.
- Some labels use the system clock (`Item.rightTag`, `savedLabel`) rather than the injected one, so tests do not assert on dates.

## Recipes

### Add or change a product rule
1. Put the rule in the right `:domain` package with a pure function/class taking time as a parameter.
2. Add/adjust a test in `domain/src/test` first.
3. Wire it in `:app` (repository, ViewModel or worker).
4. Update [domain.md](domain.md) (and [invariants.md](invariants.md) if it is a hard rule).

### Add a field to `Item`
Touch, in order: `domain/…/model/Models.kt` (field with default) → `data/db/Entities.kt` (`ItemEntity`) → `Mappers.kt` (both directions) → Room
migration (below) → wherever it is written (`LibraryRepository`) → `Exporter` only if it should be exported (bump `FORMAT_VERSION` on shape change) →
`items_fts` only if it should be searchable (it is a separate entity `ItemFts`; changing it is a migration too) → tests → [data.md](data.md).

### Add a Room column or table
1. Edit the entity; bump `version` in `CaddisDatabase`.
2. Add a `Migration(n, n+1)` and add a `MIGRATIONS` array to `CaddisDatabase` and pass it with `addMigrations` in `AppModule`. Never use destructive fallback.
3. Build once so Room writes `app/schemas/com.nexxlabs.caddis.data.db.CaddisDatabase/<n+1>.json`; **commit it**.
4. Add a migration test (create the old version from the schema JSON, open with migrations, assert data survives); `MigrationTest` was removed with the v1 reset, so re-add the test assets source set in `app/build.gradle.kts`.
5. Update [data.md](data.md).

### Add an analytics event
Add a subclass of `AnalyticsEvent` in `domain/…/analytics/Analytics.kt` with typed, non-content params (use `Intents.analyticsId`, buckets, enums); log it
through the injected `AnalyticsSink`; test it. Never add a field that can hold a URL, title, note, reason, creator or collection name.

### Support a new platform (e.g. TikTok)
**Nothing to do.** A platform is the site's own name, read from its page, so a new site works the first time a link from it is saved: it shows as its host
(`tiktok.com`) until the page is read, then as the name it gives itself (`og:site_name`), and appears in the Filter sheet while it has saves. There is
deliberately no registry to add it to. If a site shows the wrong name, the fix is in how pages are read (`DetailsParser.parsePage`, `Platform.cleanSiteName`),
tested with that site's markup in `DetailsTest`, never a host or brand entry. A change to how names are read that should reach existing saves needs a
migration that marks links as not fetched so they are read again.

### Add a default intent
`Intents.defaults` + `Intents.defaultRules` + a keyword rule in `IntentGuesser` if useful. Existing installs only seed defaults when **no intents exist**, so
a new default will not appear for existing users without a migration/seed step.

### Add a screen
Composable in the matching `ui/<area>/` package with a `@HiltViewModel` if it needs state → add the route in `ui/nav/Navigation.kt` → navigate with
`nav.navigate("route")` (tabs: `navigateToTab`) → static strings in `res/values/strings.xml` → [ui.md](ui.md).

### Add a background job
Worker in `work/Work.kt` (`@HiltWorker`), scheduled from `WorkScheduler`; respect the battery/data settings for network work; keep it idempotent
(unique work names); [architecture.md](architecture.md).

### Add a setting
Key + field + setter in `SettingsRepository` (`Settings` default), expose via `ProfileViewModel`, UI in `ProfileScreens.kt`. Do not store enums by ordinal
unless you also freeze their order.

## Windows / tooling notes

- The repo is developed on Windows; shell scripts in docs assume Git Bash. Paths in Gradle output use backslashes.
- `.gitignore` excludes build output, IDE files, keystores/`google-services.json`, `.env*`, and Claude local settings (`.claude/settings.local.json`,
  `.claude/worktrees/`, `CLAUDE.local.md`).
- Fonts: Bricolage Grotesque and DM Sans (SIL OFL) are bundled in `app/src/main/res/font`.

## Checking the real Google Play update dialog

The in-app update API only works for an app installed from Google Play, so the unit tests use Play's `FakeAppUpdateManager` and the journeys use `FakeUpdater`.
To see the real dialog: upload build N to an **internal testing** track and install it from Play on a device, then upload a higher `versionCode` to the same track.
Open Caddis (after onboarding): Play's dialog should appear once; Profile shows "Update Caddis" until installed. Play can take a while to offer a new release.
