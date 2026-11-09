# Caddis

Save it in one tap, and it comes back when you can actually use it.

Caddis is a native Android app (Kotlin, Jetpack Compose, Android 9+). Share a link to Caddis,
say why you kept it, and Home brings back at most three saves that fit the moment. Every save
ends: done, or deliberately deleted.

This is the **client-only** first build: everything lives on the phone (Room). Sign-in and sync
come later.

## Build and test

Requirements: JDK 17+, Android SDK 36.

```bash
./gradlew test             # domain + app unit tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

## Docs

Written for AI coding agents, and describing the code as it is:

- [Docs index](docs/README.md): [overview](docs/overview.md), [architecture](docs/architecture.md),
  [domain rules](docs/domain.md), [data layer](docs/data.md), [flows](docs/flows.md), [UI](docs/ui.md),
  [development](docs/development.md), [invariants and traps](docs/invariants.md)
- [Agent guidance](AGENTS.md)

## Privacy

No ads, no AI, no analytics backend. Link details are fetched by the phone straight from each
source, and Instagram and Facebook are never contacted.

Fonts: Bricolage Grotesque and DM Sans, both under the SIL Open Font License, bundled in
`app/src/main/res/font`.
