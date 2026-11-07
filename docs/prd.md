# Caddis PRD v2

Nov 4, 2026 · @Priyanshu · Updated Nov 6, 2026 after the first UI design pass

## Summary

Caddis is an Android app that turns what you save into what you use. Its promise: **save it in one tap, and it comes back when you can actually use it.**

v1 of this PRD described a universal link library: save any link, show its metadata, organise it into collections, search it. That product already exists many times over. v2 keeps the reliable capture and library from v1 and adds the part no one does well:

- **Save with a reason.** Every save has an intent (Cook, Watch, Read, Learn, Try, Buy, Go there, Just keep, or one the person creates), guessed automatically.
- **Bring it back at the right moment.** Home shows at most three saves that fit now.
- **Finish or delete.** Every item has an end: done, or deliberately deleted.
- **A weekly ritual.** A "This week" summary on Home closes the loop on the week, Monday to Sunday.
- **Search everything you saved.** Find a save by any word you remember from its title, note, creator or link.

The product is judged by one number: the share of saves that are used or deliberately deleted within 30 days.

**Working name:** Caddis, after the caddisfly larva, which builds a protective case from pieces it gathers. The name still needs a Play Store and trademark check.

## Problem and opportunity

Saving is solved; using is not. People save reels, recipes, videos and articles every day and rarely open them again. Platform save lists, browser bookmarks and chats with yourself all become piles.

The market for saving links is crowded. Desk research in November 2026 found dozens of link savers with near-identical pitches, most with few ratings:

| Kind of product | Examples | Gap Caddis targets |
| --- | --- | --- |
| General bookmark managers | Raindrop.io, Karakeep, Linkwarden | Built for organising, not for using; Android apps are thin |
| Read-it-later apps | Instapaper, Readwise Reader | Articles only; no reels, recipes or places |
| Social-post savers | Tuck, Stash, Reel Cache, My Saved Links | Capture well, then become another pile |
| Notes with sources | Recall, Mymind | AI-heavy, paid, aimed at knowledge work |

Only a few apps try to resurface saves (Stash picks one a day). None ties resurfacing to *why* the person saved the item, and none treats deleting as part of the product.

The opportunity is to be the app that measures success by what you used, not by how much you saved.

## Product principles

These decide every trade-off. When a feature conflicts with one, the feature loses.

1. **Used beats saved.** The app celebrates what you finished, never how much you hoarded.
2. **One tap to save, zero taps to organise.** The app guesses the intent; the person only corrects.
3. **Less on screen.** Home shows at most three items. Every screen has one obvious action.
4. **Letting go is a feature.** Deleting things you no longer want is normal, quick and guilt-free.
5. **Your words win.** Anything the person types or edits is never overwritten by automation.
6. **Never lose a save.** Capture works offline and survives crashes, bad networks and dead links.
7. **Calm, not compulsive.** No streaks, badges or guilt notifications. Fewer, better nudges.
8. **Private by default.** What you save is yours: no ads, no selling data, no reading your notes for analytics, and a full export.

## Users and core jobs

The first users are Android owners worldwide who save a lot from Instagram, YouTube, Reddit, X and messaging apps and rarely go back to it. Launch is global from day one, in English first, with more languages after launch.

| User | What they save | What they want |
| --- | --- | --- |
| Everyday saver | Recipes, reels, places, products | To actually cook, visit or buy the things they saved |
| Learner | Tutorials, talks, long reads | To finish what they meant to learn, in the time they have |
| Planner | Trip ideas, gift ideas, home projects | To find everything for one plan in one place when it is time |

Core jobs:

- "When I see something good, let me keep it in one tap and get back to what I was doing."
- "When I have time to cook, learn or go out, show me the thing I saved for exactly this."
- "When I half-remember something, let me find it by any word I remember."
- "When my saves pile up, help me delete what no longer matters."

## Experience overview

The app is one loop: save with a reason, wait in the library, come back on the right day, then finish or delete.

&#91;embedded content: core loop · save, wait, return, finish\]

Anything not used stays in the library; anything untouched for 90 days is offered for review in a tidy-up card at the bottom of Home. Search sits outside the loop: the search bar at the top of the Library finds any item at any time.

The app has three tabs: **Home**, **Library** and **Profile**. Capture is a plus button available on Home and Library.

| Screen | Its one job |
| --- | --- |
| Home | Show up to 3 saves that fit this moment, each with Open, Done and Later; below them, a tidy-up card when saves have gone stale and the "This week" summary |
| Save sheet | Confirm the save and ask one question: why are you keeping it? |
| Library | List every save and note, newest first; search bar and intent chips on top, a Filter button for platform and status, and a switch to Collections |
| Item page | Why you kept it, where it came from, your note; Open, Done, Delete |
| Trash | Restore deleted items or delete them forever, within 30 days |
| Profile | Preferences (return rules, intents, notifications), background work, your data, the privacy policy and account settings |

The screens are mocked up on the Caddis App Concept design canvas and in a clickable prototype covering 28 screens, in light and dark.

## Functional requirements

44 requirements across ten areas; 40 are Must for the first release. Change a row's priority in its dropdown.

### Capture

| ID | Requirement | Priority |
| --- | --- | --- |
| CAP-01 | Sharing any http(s) link to Caddis saves it on the phone instantly, before any details load. | Must |
| CAP-02 | The save sheet confirms the save and never blocks; dismissing it keeps the save. | Must |
| CAP-03 | Capture works offline; details are fetched when the phone is back online. | Must |
| CAP-04 | Add a link from inside the app; the clipboard is read only after the person taps Add, and shown as a suggestion. | Must |
| CAP-05 | Save a note or plain text without a link. | Must |
| CAP-06 | A likely duplicate (same normalised URL) shows the earlier save; the person can still save again. | Must |
| CAP-07 | Record the source app when Android provides it, plus an optional "from" field (e.g. Rohan). | Should |
| CAP-08 | Home-screen widget with quick capture and today's picks. | Later |

### Intent

| ID | Requirement | Priority |
| --- | --- | --- |
| INT-01 | Every save has exactly one intent: Cook, Watch, Read, Learn, Try, Buy, Go there, Just keep, or an intent the person created. | Must |
| INT-02 | The intent is guessed from platform, content type and details; the person changes it in one tap. | Must |
| INT-03 | Optional one-line "thought for future you" on the save sheet. | Must |
| INT-04 | Each intent has a default return rule (e.g. Cook returns on weekend mornings), editable in Profile. | Must |
| INT-05 | The person can create, rename and delete intents, and set the label of each intent's Done button. Deleting an intent moves its saves to Just keep, which cannot be deleted. A new intent returns on weekday afternoons, at most once a day, until its rule is changed. | Must |

### Bring it back

| ID | Requirement | Priority |
| --- | --- | --- |
| BRB-01 | Home shows at most 3 items, chosen by intent, time of day, day of week, item length and return rules. | Must |
| BRB-02 | An item deferred by the person does not reappear before its new date. | Must |
| BRB-03 | "Bring back later" offers tonight, this weekend, next week or a chosen date. | Must |
| BRB-04 | Notifications are opt-in, at most one a day, with quiet hours the person sets. | Must |
| BRB-05 | Go there items can return when the person is nearby, only with location permission. | Later |

### Finish and delete

| ID | Requirement | Priority |
| --- | --- | --- |
| FIN-01 | Every item has a done action named for its intent: Made it, Watched, Read, Learned, Tried, Bought, Visited, Done, or the label the person set. | Must |
| FIN-02 | Delete moves an item to a 30-day Trash, with restore and permanent delete. | Must |
| FIN-03 | A tidy-up flow, offered by a card at the bottom of Home, reviews items untouched for 90 days one at a time: keep or delete. | Must |
| FIN-04 | Done items stay in the library and search, and leave Home. | Must |

### This week

| ID | Requirement | Priority |
| --- | --- | --- |
| WK-01 | Home ends with a "This week" summary for the current Monday to Sunday: counts of saved, used and deleted. | Must |
| WK-02 | An optional weekly notification delivers the summary on Sunday; the person can turn it off in Notifications. | Should |

### Search

| ID | Requirement | Priority |
| --- | --- | --- |
| SRC-01 | One search bar at the top of the Library covers title, description, caption, creator, URL, note, reason, and source. | Must |
| SRC-02 | Search matches the keywords the person types, in any order, against the fields above. Phrases are not interpreted, and no language model is used. | Must |
| SRC-03 | Search runs on the phone and works offline. | Must |
| SRC-04 | Filters for intent, platform and status. Intent is also a row of one-tap chips above the list; there is no separate Tags screen. | Must |

### Library

| ID | Requirement | Priority |
| --- | --- | --- |
| LIB-01 | The Library opens on all saves and notes, newest first. Intent chips narrow the list in one tap. | Must |
| LIB-02 | Collections, reached from a switch at the top of the Library: create, rename, delete; an item can be in many; deleting a collection keeps its items. | Must |
| LIB-03 | Filter by platform: Instagram, Facebook, Reddit, YouTube, Medium, X, Web. | Must |
| LIB-04 | Item page: preview, reason, source, note, collections, and Open, Done, Delete. | Must |
| LIB-05 | Open goes to the original app when installed, otherwise the browser. | Must |

### Previews and details

| ID | Requirement | Priority |
| --- | --- | --- |
| MET-01 | Title, thumbnail, creator and length for YouTube, Reddit, X and Medium, plus a generic card from page metadata for all others. For Instagram and Facebook the app does not call Meta: it keeps the link, the platform, the handle read from the URL and what the person adds. | Must |
| MET-02 | Details load progressively and keep loading in the background after the sheet closes. | Must |
| MET-03 | Fields the person edited are never overwritten; a manual refresh is available. | Must |
| MET-04 | No downloading or rehosting of original media; cached thumbnails are small and clearable. | Must |
| MET-05 | The saved details stay if the original post is deleted or made private. | Must |

### Notes

| ID | Requirement | Priority |
| --- | --- | --- |
| NOTE-01 | One note per item, edited visually and stored as Markdown. | Must |
| NOTE-02 | Standalone notes take an intent and collections like any other item. | Must |

### Account and data

| ID | Requirement | Priority |
| --- | --- | --- |
| ACC-01 | Google sign-in; items, intents, notes, collections, done and trash state sync across devices. | Must |
| ACC-02 | Export the whole library as JSON and as HTML bookmarks. | Must |
| ACC-03 | Delete the account and all its data from inside the app, from Account settings in Profile. | Must |
| ACC-04 | Profile holds preferences and background-work settings, a short in-app privacy policy with a link to the full policy, and Account settings with sign out and delete account. | Must |

## Non-functional requirements

The targets below are starting points, set for a mid-range Android phone; confirm them in the first performance pass.

| Area | Requirement |
| --- | --- |
| Speed | Save sheet appears within 300 ms of choosing Caddis in the share menu. The app opens to Home with content in under 1 second. Search returns in under 200 ms on 5,000 items. |
| Reliability | A save is never lost to a crash, a closed app, a restart or a bad network. Queued saves and edits survive process death. Retries never create duplicates. |
| Offline | Everything except opening the original and fetching new details works offline: Home, library, search, notes, intents, done, delete. |
| Privacy | Records are scoped to the signed-in account. No ads, no sale of data. Analytics never include URLs, titles or note text. Details are fetched by the phone directly from each source; only the link itself is sent, and no AI service sees any save. |
| Platform rules | Use only public metadata methods the platforms permit. Never bypass logins, private accounts or paywalls. Never download original media. |
| Battery and data | Background work uses Android's scheduler, batches requests, and pauses on low battery and metered networks if the person chooses. Thumbnail cache is capped (default 200 MB). |
| Accessibility | Touch targets at least 44 px. Text contrast at least 4.5:1. Intents are told apart by label, not colour alone. Full screen-reader labels. Supports system font size and dark mode. |
| Low-end phones | Usable on Android 9+ with 3 GB RAM and on slow 3G connections. |

## Data model changes

v1's item, collection and membership entities stay. v2 adds the fields that make intent, return and finishing work. Tags, favorites, read/unread and archive remain out.

| Entity | New or changed field | Purpose |
| --- | --- | --- |
| Item | `intent` (the id of an intent; defaults are cook, watch, read, learn, try, buy, go, keep) | Drives filters, return rules and the done verb |
| Item | `intentSource` (guessed, user) | Lets the guess improve without overriding the person |
| Item | `reason` (short text) | The "thought for future you" |
| Item | `sourceApp`, `sourceFrom` | Where it came from and who sent it |
| Item | `status` (active, done, deleted) with `doneAt`, `deletedAt` | Replaces v1's simple trash flag; deleted items are purged after 30 days |
| Item | `returnAt`, `returnRule` (default, tonight, weekend, nextWeek, date, never) | When the item may appear on Home |
| Item | `lengthSeconds` (video length or reading time) | Matches items to the time available |
| Item | `lastSurfacedAt`, `surfacedCount`, `lastOpenedAt` | Avoids repeats and finds stale items for tidy-up |
| Intent | `name`, `doneLabel`, `isDefault` | Lets the person create, rename and delete intents; Just keep is protected and cannot be deleted |
| Intent rule | `intent`, `days`, `timeOfDay`, `maxPerDay` | Return rule per intent, editable in Profile; new intents start on weekday afternoons, once a day |
| Weekly summary | `weekStart` (a Monday), `savedCount`, `usedCount`, `deletedCount` | A stored snapshot so a past week stays the same when reopened |

All new fields sync with the same revision-aware rules as v1. Fields the person edits are marked user-managed and protected from automation.

## Out of scope and later

**Deliberately never**, because each one works against the principles:

- Tags, as a separate screen or a separate concept. Intents (with one-tap filter chips) and collections cover organisation; tags become a chore.
- Favorites, stars, ratings, read/unread and archive. Done and Delete replace them.
- An in-app browser or reader. Content is opened where it lives.
- Streaks, badges, points or guilt-driven notifications.
- A social feed, public profiles or follower counts.
- Folders inside folders.
- Language models or AI features of any kind, on the phone or on a server. Intent guesses use plain rules, and search uses keyword matching.
- Downloading or rehosting reels, videos or articles.
- Ads or selling data.

**Later**, once the first release meets its success target:

- Shared collections with a partner or friend (e.g. weekend recipes for two).
- Location-based return for Go there items (BRB-05).
- Home-screen widget with quick capture and today's picks (CAP-08); lower priority than the core loop.
- Import from Instagram and YouTube saved lists through their data exports.
- Recipe ingredients turned into a shopping list.
- iOS app and a web version.
- Paid plan, if one is ever needed to fund platform upkeep; the core loop stays free.

## Success metrics

The north-star metric is the **resolution rate**: the share of saves that are marked done or deliberately deleted within 30 days of saving. Targets are first guesses to revisit after the beta.

| Metric | Definition | First target |
| --- | --- | --- |
| Resolution rate (north star) | Saves done or deleted within 30 days ÷ all saves | 30% |
| Home usefulness | Home sessions where at least one item is opened or marked done | 40% |
| Intent accuracy | Guessed intents the person did not change | 80% |
| Capture success | Shared valid links that become saved items | 99.9% |
| Capture speed | Share tap to save confirmed (median) | under 1 second |
| Detail coverage | Saves with a title and thumbnail, tracked per platform (Instagram and Facebook excluded: they show a generic card by design) | 85% |
| Weekly summary opens | Weekly summary notifications opened ÷ delivered | 50% |
| Week-4 retention | People who open the app in week 4 after install | 35% |
| Notification opt-out | People who turn notifications off within 30 days | under 15% |

Saves per user is tracked but is not a goal: a rising save count with a flat resolution rate means Caddis is becoming another pile.

## Risks, open questions and release plan

### Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Platforms restrict public metadata | Thin previews; weaker intent guesses | Save always succeeds; generic card fallback; person can edit details; track coverage per platform. Instagram and Facebook are never fetched, because Meta's terms do not allow storing its preview data |
| Resurfacing feels random or naggy | People ignore Home or turn notifications off | At most 3 items, one notification a day, easy "not now"; watch opt-out rate |
| Intent guesses are wrong | Extra taps; wrong return times | One-tap change; learn from corrections; "Just keep" as a safe default |
| People create many intents and lose the simplicity | A long chip row; weaker guesses for custom intents | Defaults stay first; new intents get a safe return rule; review how many intents people create in the beta |
| Free with no revenue | No budget to maintain extraction | Keep backend small; decide on funding before scaling past the beta |
| Competitors copy resurfacing | Loss of the wedge | Move fast on intent-based return and the weekly ritual; build habit early |
| Android limits background work | Late details and late reminders | Use Android's scheduler; fetch details on next app open as a fallback |

### Open questions

- [ ] Is the first audience right? Run 8–10 interviews with heavy savers across at least three regions before build.
- [ ] Do people accept being asked "why" on save, or should it be skippable by default?
- [ ] Which intents matter most? Test whether eight is too many, and whether people create their own.
- [ ] Does the name Caddis clear Play Store and trademark checks?
- [ ] How will the product be funded if usage grows?
- [ ] Does a summary of counts on Home (no pick, no stale list) keep the weekly ritual meaningful?

### Release plan

1. **Prototype (4 weeks).** Capture, intent, library and item page; test with 10 people. Gate: 8 of 10 save without help and most keep the guessed intent.
2. **Closed beta (8 weeks).** Adds Home, bring back later, done and delete, search, sync. Gate: resolution rate of 20% and week-4 retention of 25% among 200 testers.
3. **Public release.** Adds the This week summary, tidy-up, export, Profile settings and notifications. Gate: the targets in Success metrics.
4. **After release.** Items from the Later list, in order of what testers ask for most.
