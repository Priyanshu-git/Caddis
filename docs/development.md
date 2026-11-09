# Development

## Requirements

- JDK 17+ (both modules use `jvmToolchain(17)`), Android SDK 36 (`compileSdk`/`targetSdk` 36, `minSdk` 28).
- Windows: use `gradlew.bat`, or `./gradlew` from Git Bash. Gradle config: `org.gradle.jvmargs=-Xmx3072m`, parallel + build cache on.

## Commands

```bash
./gradlew :domain:test              # pure-Kotlin domain tests (JUnit 5 + Kotest)
./gradlew :app:testDebugUnitTest    # app unit + Robolectric tests (Room, repository, ViewModels, fetcher, migration)
./gradlew test                      # both
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
work-testing, navigation-testing, room-testing. The catalog also lists `mockk`, Espresso and Compose UI test artifacts that no source set
currently uses (`app/src/androidTest` does not exist).

Plugins applied in `:app`: `android.application`, `kotlin.android`, `kotlin.compose`, `ksp`, `hilt`, `room` (schema dir `app/schemas`).
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
| `schedule/SchedulingTest` | `ReturnScheduler`, `HomePicker`, `TidyUp`, `Trash`, `Weeks`, rule labels |
| `schedule/RemindersTest` | opt-in, one a day, quiet hours, weekly on Sunday evening |
| `details/DetailsTest` | planner (Meta never fetched), page/oEmbed parsing, generic titles, `mergeDetails`, `formatLength` |
| `details/EditsAndIconsTest` | `withEdits`, `withLink`, favicon parsing/fallbacks/merge, `couldBeNote` |
| `share/ShareTextTest` | share text, tracking-param stripping, private words never shared |
| `MiscTest` | search query, Markdown, JSON/HTML export, analytics buckets |

### `:app` (`app/src/test/java/…`)

JUnit 4. Robolectric config in `app/src/test/resources/robolectric.properties` (`sdk=34`, plain `Application`).

- `TestSupport.kt`: `MutableClock` (movable `Clock`, default zone `Asia/Kolkata`), `RecordingAnalytics` (collects events),
  `RecordingScheduler` (collects scheduled item ids), and **`Fixture`**: fixed clock (Wed 4 Nov 2026 11:08 IST), in-memory Room
  (`allowMainThreadQueries`) and a real `LibraryRepository` wired with the fakes. Use `Fixture` for any repository/ViewModel test; call `close()`.
- `data/LibraryRepositoryTest`: seeding, saves, duplicates, search, done/undo counters, trash/restore/purge, intent corrections, details merging,
  intent deletion, collections, bring-back/surfacing, export/delete-all, edit-details and link-change behaviour.
- `data/DetailsFetcherTest`: `MockWebServer` (no real network). Page parsing, retry vs give-up, Meta never requested, bot-check pages, Reddit oEmbed + page,
  User-Agent behaviour.
- `data/MigrationTest`: builds a v1 database from `app/schemas/.../1.json` (schemas are added as test assets via `sourceSets.test.assets`) and opens it with the migrations.
- `ui/CaptureViewModelTest`, `ui/ItemActionsShareTest`, `TabNavigationTest` (uses `navigation-testing`), `MotionTest`.

Test conventions: backticked sentence names; one behaviour per test; no network; inject the clock.

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
2. Add a `Migration(n, n+1)` and append it to `CaddisDatabase.MIGRATIONS`. Never use destructive fallback.
3. Build once so Room writes `app/schemas/app.caddis.data.db.CaddisDatabase/<n+1>.json`; **commit it**.
4. Extend `MigrationTest` (create the old version from the schema JSON, open with migrations, assert data survives).
5. Update [data.md](data.md).

### Add an analytics event
Add a subclass of `AnalyticsEvent` in `domain/…/analytics/Analytics.kt` with typed, non-content params (use `Intents.analyticsId`, buckets, enums); log it
through the injected `AnalyticsSink`; test it. Never add a field that can hold a URL, title, note, reason, creator or collection name.

### Support a new platform (e.g. TikTok)
`Platform` enum + label → `PlatformDetector.detect` branch (kind + handle) → `DefaultTitles.of` branch (the `when` is exhaustive) → `DetailsPlanner.plan`
branch (also exhaustive) → `Platform.filterable` if it should be a filter → `IntentGuesser.defaultFor` if its kinds need a default → tests in `LinksTest`,
`DefaultTitlesTest`, `DetailsTest`. If the platform must never be contacted, treat it like Meta (`isMeta`, `Favicons`).

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
