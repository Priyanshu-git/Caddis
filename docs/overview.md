# Overview

## What Caddis is

A native Android app (Kotlin, Jetpack Compose, minSdk 28, targetSdk 36) that turns what you save
into what you use. The loop:

1. **Save** a link in one tap from the system share sheet (or add a link/note inside the app).
2. Say **why** you kept it with an *intent* (Cook, Watch, Read…). Caddis guesses with plain rules;
   changing the guess is one tap.
3. Caddis **brings it back** on Home, at most three saves at a time, when the intent's return rule
   fits the day and time.
4. Every save **ends**: marked done (with an intent-specific label such as "Watched") or deleted
   (30-day Trash).

Package/application id: `app.caddis`. Version `0.1.0` (code 1).

## Phase: client only

Room on the phone is the only store. There is **no** sign-in, sync, Firebase, remote analytics,
push service or server. Every record already carries `revision` and `updatedAt`, and the fields a
person edits are tracked in `userEdited`, so sync can be added later. Do not add network sync code
unless asked.

## Principles (they decide ties)

- Used beats saved. Zero taps to organise. Home shows at most 3.
- Your words win: automation never overwrites a field the person edited (`userEdited`).
- Never lose a save: the Room write happens before anything else (details, sheet, network).
- Calm: no streaks, no guilt, no catch-up notification bursts, counts only.
- Private by default: no AI, no ads, no remote analytics, nothing about Instagram/Facebook is ever
  fetched from Meta, shares never include the person's own reason/note/intent/collections.

## Vocabulary

| Term | Meaning | Defined in |
| --- | --- | --- |
| **Item** | One save. `type` is `LINK` or `NOTE`. Everything (library, Home, Trash) is a filtered view of items. | `domain/…/model/Models.kt` |
| **Link / Note** | A link has `url`; a note has `url = null`, `platform = NOTE`, `kind = NOTE`, and Markdown in `note`. A link may also carry a note (one per item). | `Models.kt` |
| **Platform** | `INSTAGRAM, FACEBOOK, REDDIT, YOUTUBE, MEDIUM, X, WEB, NOTE`, read from the URL. `isMeta` is true for Instagram/Facebook. | `Models.kt`, `link/Links.kt` |
| **ContentKind** | `REEL, VIDEO, SHORT, POST, CHANNEL, PROFILE, SUBREDDIT, ARTICLE, WEBPAGE, PDF, NOTE`; `isVideo` for REEL/VIDEO/SHORT. | `Models.kt` |
| **Intent** | Why you kept it. Eight defaults (ids `cook, watch, read, learn, try, buy, go, keep`) plus custom ones (ids `u-xxxxxxxx`). Each has a `doneLabel`. `keep` ("Just keep") is protected and has no return rule. | `model/Intents.kt` |
| **IntentSource** | `GUESSED` (by rules) or `USER`. Fetched details may still re-guess while `GUESSED`. | `Models.kt` |
| **IntentRule** | Per intent: days of week, `TimeOfDay`, `maxPerDay` (1..3). Decides when saves of that intent return. | `Models.kt` |
| **ReturnRule / returnAt** | A per-item "bring back later" override: `DEFAULT, TONIGHT, WEEKEND, NEXT_WEEK, DATE, NEVER`. `returnAt` is the moment it is due. | `Models.kt`, `schedule/Scheduling.kt` |
| **ItemStatus** | `ACTIVE`, `DONE`, `DELETED` (Trash). | `Models.kt` |
| **Collection** | A named group; many-to-many with items; deleting a collection keeps its items. | `Models.kt` |
| **WeeklySummary** | Per Monday–Sunday week: saved / used (done) / deleted counts. | `Models.kt` |
| **EditableField / userEdited** | `TITLE, DESCRIPTION, CREATOR, THUMBNAIL, LENGTH`. Once in `userEdited`, fetched details never replace it. | `Models.kt` |
| **displayTitle** | `title`, else a readable default built from the URL (`DefaultTitles`), else "Untitled note". | `Models.kt` |
| **Pick** | An item chosen for Home with a score. | `schedule/Scheduling.kt` |
| **Surfaced** | Home showed the item that day (`lastSurfacedAt`, `surfacedCount`). | `LibraryRepository.markSurfaced` |

## Item lifecycle

```
            saveLink / saveNote
                   │
                   ▼
   ┌────────────  ACTIVE  ◄───────── markActive (undo done / "Mark active")
   │               │  ▲                    ▲
   │ markDone      │  │ restore            │
   ▼               │  │ (back to DONE if doneAt != null, else ACTIVE)
  DONE ──delete──► DELETED ──purge / 30 days──► row removed
```

- `markDone` sets `doneAt`, clears `returnAt`/`returnRule`, adds 1 to the week's *used* count.
- `delete` sets `deletedAt` (keeps `doneAt`), adds 1 to the week's *deleted* count.
- `restore` and `markActive` reverse the counters in the week the original event happened.
- Trash older than 30 days is purged at every app start (`CaddisApp.onCreate`).

## Built (what exists in code)

- **Capture**: share-sheet target (`ShareActivity`, `text/plain` only), in-app Add sheet (link or
  note), clipboard suggestion read only after the person taps Add, duplicate detection with
  "Open earlier save" / "Save again", plain-text share becomes a note, "Who sent it" field, source
  app name from the share referrer.
- **Intents**: eight defaults, create/rename/delete custom intents, custom Done label, per-intent
  return rules editor, rule-based guessing with local learning from corrections.
- **Details**: background fetch of title/description/creator/thumbnail/length/site icon via public
  oEmbed (YouTube, X, Reddit) or Open Graph tags (Medium, web); readable fallback titles; Edit
  details sheet (link, title, by, description); Refresh button.
- **Home**: ≤3 picks, "Bring it back" later options (tonight / weekend / next week / date),
  Done and Open, This week card, "Time for a tidy-up" card, "Next up" hint when nothing fits.
- **Library**: all saves and notes newest first; intent chips; Filter sheet (platform, status);
  collections tab and shelves; offline keyword search (FTS4) with "why it matched" text and
  recent searches (in memory only).
- **Item page** and **Note editor** (rich text stored as Markdown, autosave).
- **Finish**: Done/Mark active, delete to Trash with Undo, Trash screen (restore / delete forever),
  Tidy-up (keep or delete saves untouched for 90 days).
- **Notifications** (opt-in): one daily pick and a Sunday-evening weekly summary, quiet hours,
  shared one-a-day budget; local only.
- **Profile**: return rules, intents, notifications, battery/mobile-data pause for background
  fetching, export (JSON and HTML bookmarks), thumbnail cache (200 MB cap) with clear, privacy
  points, account screen (no account; "Delete all data").
- **Share out**: Share action on the item page (title, clean link, "Shared via Caddis app").
- **Theme**: light/dark paper palette with one teal accent, Bricolage Grotesque + DM Sans,
  calm motion, shared-element thumbnail transitions, predictive back.

## Not built (do not assume it exists)

- Sign-in, accounts, sync across devices, any server or Firebase.
- Remote analytics: `AnalyticsSink` only logs to Logcat in debug builds.
- Any language model or AI feature (explicitly forbidden).
- Home-screen widget, location-based return, import from files/bookmarks.
- A published full privacy policy (`FULL_POLICY_URL` in `ui/profile/ProfileScreens.kt` is `null`).
- Heading and quote buttons in the note editor toolbar (headings/quotes still render if present).
- Persisted recent searches.
- `androidTest` instrumentation tests (dependencies are declared but there is no test source set).
