# Caddis technical architecture

Nov 6, 2026 · @Priyanshu · Working draft: decisions are recorded as they are made

This document records the technical choices for Caddis (PRD v2). Section 1 holds what Priyanshu has decided. Section 2 holds defaults I would use if no one objects, and they are not decisions. Section 3 lists what is still open.

## 1. Decided

| # | Area | Decision | What it means |
| --- | --- | --- | --- |
| 1 | App | Native Android: Kotlin and Jetpack Compose, Android 9+ (API 28) | Direct access to the share sheet, WorkManager and Room. No iOS from the same code; iOS later is a separate build. |
| 2 | Data and sync | Room (SQLite) on the phone as the source of truth, with Firebase (Authentication with Google, Firestore) as the cloud copy and sync | Writes land in Room first and show instantly. Search runs on the phone. Firestore is enough for this data (see 1.1). |
| 3 | Link details | Fully native: the phone fetches details directly from each source | No Caddis server sits between the phone and the platforms. Each save's details sync to other devices through Firestore, so only the saving device fetches. |
| 4 | Instagram and Facebook | The app does not call Meta at all | It stores the link, the platform, the handle read from the URL and whatever the person types or the share sheet provides, and shows a generic card. No Meta token ships in the app (see 1.2). |
| 5 | Intelligence | No language model anywhere. The intent guess uses plain rules. Search matches typed keywords only | Phrases such as "recipes from last month" are not interpreted. SRC-02 in the PRD now says this. |
| 6 | Server tasks | Firebase's ready-made delete-user-data extension for account deletion; the phone purges Trash older than 30 days when the app opens | Needs the pay-as-you-go Blaze plan, which has a free quota. No custom server code. |
| 7 | Notes editor | A native Compose rich-text editor library, with Markdown import and export | Covers bold, italic, heading, lists, quote and code block. Complex Markdown may need limits. |
| 8 | Analytics and crashes | Firebase Analytics and Crashlytics, with a strict list of content-free events | Allows the success metrics without sending URLs, titles or notes (see 1.3). |
| 9 | Data location | Firestore in the US multi-region location (nam5) | Cannot be changed later. Chosen for durability and a neutral spot for a global audience; the phone reads its own copy first, so distance is rarely felt. |

### 1.1 Why Firestore (NoSQL) is enough

- **The data is document-shaped.** Items, collections, intents, return rules and weekly summaries are each one record. "An item can be in many collections" is a list of collection ids on the item.
- **Search never uses Firestore.** Keyword search runs on the phone against Room's full-text index, so Firestore's limited querying does not matter.
- **Conflicts are the weak spot.** Firestore resolves a clash per document: the last write wins. To avoid lost edits we keep user-edited fields marked as protected and keep a conflict copy of a note when two devices edit it (PRD: never lose a save).
- **Export is built from Room.** JSON and HTML bookmarks are produced from the local database, not by reading everything back from Firestore.
- **Isolation by account.** Security rules allow a signed-in person to read and write only their own records.

### 1.2 Why Instagram and Facebook are not fetched

The first plan was to keep a Meta token inside the app. Reading Meta's oEmbed page changed that:

- The endpoint is for embedding Instagram content in websites and apps. Using the metadata for any other purpose, including extracting or persisting it, is prohibited.
- If a thumbnail is shown instead of the full embed, it needs clear attribution to the author and Instagram, with a link to the post.
- Caddis stores titles, creators and thumbnails so a save survives a deleted post (MET-05), which does not fit those terms.
- It also needs a Meta developer app approved for oEmbed Read through app review, and any token inside an APK can be extracted.

So the app never calls Meta. It saves the link, detects the platform and the handle from the URL, and shows a generic card the person can edit. Previews for these two platforms stay plain by design. Revisit only if Meta offers terms that allow storing previews.

### 1.3 Analytics: the only events allowed

Events carry no URL, title, note, reason, creator or collection name. Only the fields below may be sent. Resolution rate is computed from `item_done` and `item_deleted` events that carry the save's age.

| Event | Allowed fields |
| --- | --- |
| `save_created` | platform, entry (share sheet, in-app), intent guessed (yes or no) |
| `intent_changed` | from intent id, to intent id (default intents only; custom intents are sent as "custom") |
| `item_opened` | platform, entry (Home, Library, search) |
| `item_done` | intent, age in days (bucketed) |
| `item_deleted` | intent, age in days (bucketed), source (item page, tidy-up) |
| `home_viewed` | number of picks shown, opened or acted on (yes or no) |
| `tidy_up_finished` | kept count, deleted count |
| `summary_viewed` | opened from notification (yes or no) |
| `notification_setting` | which setting, on or off |
| `search_run` | result count bucket (the words typed are never sent) |

## 2. Proposed defaults (not decisions)

Tell me which of these to change.

| Area | Default | Reason |
| --- | --- | --- |
| App structure | MVVM with one-way data flow, Hilt for dependency injection, Kotlin coroutines and Flow, Navigation Compose | Standard for Compose; keeps screens testable. |
| Background work | WorkManager for sync, detail fetching, Trash purge and reminders. Reminders are local notifications scheduled on the phone, so no push service is needed | Matches the PRD rule to use Android's scheduler. |
| Networking | OkHttp for requests, Jsoup for reading page metadata, kotlinx.serialization for JSON, Coil for thumbnails with a 200 MB disk cap | Small, common, works on low-end phones. |
| Local search | Room full-text search over title, description, caption, creator, URL, note, reason and source | Meets the "under 200 ms on 5,000 items" target offline. |
| Sync | Every change goes into an outbox table in Room with an idempotency key; each record carries a revision; the phone pulls changes since its last sync cursor. Firestore's own offline cache is switched off so data is not stored twice | Gives the revision-aware, retry-safe sync the PRD asks for. |
| Intent guess | A rule table (platform, content type, words in title and description) plus a local count of corrections that shifts the guess | No model; instant and offline. |
| Home picks | A scoring function over intent rule, time of day, day of week, length and last-shown time | PRD rule BRB-01; no model. |
| Protection | Firestore security rules per account; Firebase App Check with Play Integrity; Firebase emulator tests for the rules | Keeps one person from reading another's records. |
| Build | Gradle with Kotlin DSL and a version catalog, R8 on, GitHub Actions for build and tests, Play internal testing for the beta | Common, low setup cost. |
| Remote Config use | Extraction rules (per-platform patterns) and feature flags | Lets us fix a platform change without an app release. |
| Video length | Read from the page's structured data for YouTube. If that proves unreliable, use the YouTube Data API with a key restricted to this app (a key made to be public in apps) | Gives the length that Home uses to match a save to the time available. |

## 3. Open

No major decision is waiting. Two things remain:

- Confirm or change the proposed defaults in section 2.
- Later: a home-screen widget (Jetpack Glance when it is built), an iOS plan (the native choice means a separate app), what to do if Firestore costs grow, and whether Meta ever offers terms that allow stored previews.

## Sources

- [Meta: Embed an Instagram post (oEmbed)](https://developers.facebook.com/docs/instagram-platform/oembed): token types, oEmbed Read review, use restrictions and thumbnail attribution, read Nov 6, 2026. This is why Caddis does not call Meta.
