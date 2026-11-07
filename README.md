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

- [Product requirements](docs/prd.md)
- [Technical decisions](docs/technical-architecture.md)
- [Code architecture](docs/architecture.md)
- [Requirements status](docs/requirements-status.md)
- [Agent guidance](AGENTS.md)

## Privacy

No ads, no AI, no analytics backend. Link details are fetched by the phone straight from each
source, and Instagram and Facebook are never contacted.

Fonts: Bricolage Grotesque and DM Sans, both under the SIL Open Font License, bundled in
`app/src/main/res/font`.
