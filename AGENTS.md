# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository. `CLAUDE.md` imports this file;
edit this one.

Caddis is a native Android app that turns what you save into what you use: save a link in one tap
with a reason (an *intent*), get it back on Home when it fits, then finish it or delete it.
Product spec: [docs/prd.md](docs/prd.md). Technical decisions: [docs/technical-architecture.md](docs/technical-architecture.md).
How the code is laid out: [docs/architecture.md](docs/architecture.md). What is built: [docs/requirements-status.md](docs/requirements-status.md).

## Current phase

**Client only.** No Firebase, no sign-in, no sync, no analytics backend. Room on the phone is the only
store. Everything is shaped so sync can be added later (revision + `updatedAt` on every record,
user-edited fields tracked), but do not add network sync code until asked.

## Commands

```bash
./gradlew :domain:test                       # pure-Kotlin domain tests (JUnit 5 + Kotest)
./gradlew :app:testDebugUnitTest             # app unit + Robolectric tests (Room, repositories, ViewModels)
./gradlew assembleDebug                      # debug APK
./gradlew :app:lintDebug                     # Android lint
./gradlew test assembleDebug                 # what CI runs
```

On Windows use `gradlew.bat` or `./gradlew` from Git Bash. JDK 17+ is required (toolchain 17).

## Modules

- `:domain` — pure Kotlin/JVM. **No Android, Room, OkHttp or Compose imports.** Models, URL
  validation/normalisation, platform detection, intent guessing, Home picks, return scheduling,
  week maths, tidy-up selection, search query building, metadata parsing (Jsoup), export,
  analytics event definitions. Every rule the PRD states lives here, with tests.
- `:app` — Android adapters and UI: Room database (+ FTS4 search), repositories, DataStore settings,
  OkHttp metadata fetcher, WorkManager workers, notifications, share-sheet `ShareActivity`, Compose
  screens, Hilt modules.

## Rules

- **Product principles win** (PRD): used beats saved; zero taps to organise; Home shows at most 3;
  your words win (never overwrite user-edited fields); never lose a save; calm, no streaks;
  private by default.
- **No language models or AI anywhere.** Intent guesses are rules; search is keyword FTS.
- **Never call Meta.** Instagram and Facebook links are saved with platform + handle from the URL only.
- Never download or rehost original media. Thumbnails are cached by Coil with a 200 MB cap.
- Analytics events must be one of `AnalyticsEvent` in `:domain` and never carry URLs, titles,
  notes, reasons, creators or collection names.
- New behaviour: write it in `:domain` with tests first, then wire it in `:app`.
- A save is written to Room **before** anything else (details, sheet, network).
- Strings shown to users go in `res/values/strings.xml` when they are static; copy follows the
  prototype's tone (short, plain, no guilt).
- Dependency versions live in `gradle/libs.versions.toml` only.
- Room schema changes: bump the DB version, add a migration, and commit the exported schema in
  `app/schemas/`.

## Docs and tests are part of every change

- Update `docs/requirements-status.md` when a PRD requirement changes state.
- Update `docs/architecture.md` when you add a module, table, worker or screen.
- Add or update tests in the same change: domain logic in `domain/src/test`, Room/repository/ViewModel
  behaviour in `app/src/test` (Robolectric).
- Run `./gradlew test` before committing.
