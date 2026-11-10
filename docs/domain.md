# `:domain` reference

Pure Kotlin/JVM rules. Source: `domain/src/main/kotlin/app/caddis/domain/`. Tests:
`domain/src/test/kotlin/app/caddis/domain/` (JUnit 5 + Kotest `shouldBe`). Everything takes time as a
parameter (`Instant`, `ZonedDateTime`, `LocalDate`); nothing reads the clock.

## Models (`model/`)

`Models.kt` — see [overview.md](overview.md) for meanings.

- `Item` fields: `id, type, url, normalizedUrl, platform, kind, title, description, creator,
  thumbnailUrl, faviconUrl, lengthSeconds, note, intentId, intentSource, reason, sourceApp,
  sourceFrom, status, createdAt, updatedAt, doneAt, deletedAt, returnRule, returnAt,
  lastSurfacedAt, surfacedCount, lastOpenedAt, lastReviewedAt, detailsFetchedAt, userEdited,
  collectionIds, revision`.
- `Item.displayTitle` = `title` if not blank, else `DefaultTitles.of(url)` if there is
  a URL, else `"Untitled note"`.
- `Item.lastTouchedAt` = max of `createdAt`, `lastOpenedAt`, `lastReviewedAt` (drives Tidy-up).
- `Platform` (`model/Platform.kt`) is **the site's own name**, not an enum and not a list: `Platform(label)`, compared by
  `id = label.lowercase()`. Before a page is read it is the host (`Platform.fromUrl`: `www. m. mobile. amp.` stripped, no host →
  `web`); once the page says what it calls itself (`og:site_name`) `Platform.named(name)` replaces it (`ChatGPT`, `Google Drive`).
  `Platform.cleanSiteName` accepts a name only if it is non-blank, ≤ 40 chars, and not a web address. `Platform.fromStored(text)`
  loads whatever was stored (`note`/`Note` → `NOTE`). Only `label` is stored. There is no registry: nothing in the code knows any site.
- `ContentKind` keeps `REEL, SHORT, CHANNEL, SUBREDDIT, FILE, FOLDER, DOCUMENT` so old saves load, but nothing produces them any more; a page can only make a link `VIDEO`, `ARTICLE`, `PROFILE` or `PDF`.
- `TimeOfDay`: `MORNING 5–12`, `AFTERNOON 12–17`, `EVENING 17–23` (hours, end exclusive).
  `TimeOfDay.of(hour)` falls back to `EVENING` for 23 and 0–4.
- `Intent.isProtected` is true only for id `keep`.
- `IntentRule` requires `maxPerDay in 1..3` (constructor `require`).

`Intents.kt`

- Ids: `cook, watch, read, learn, try, buy, go, keep`. Names/done labels:
  Cook/"Made it", Watch/"Watched", Read/"Read", Learn/"Learned", Try/"Tried", Buy/"Bought",
  Go there/"Visited", Just keep/"Done". Positions 0–7.
- Default rules: Cook weekends morning; Watch every day evening; Read every day afternoon; Learn
  weekdays morning; Try weekends afternoon; Buy weekdays evening; Go weekends morning;
  **Keep has no rule** (never returns on its own). All `maxPerDay = 1`.
- `ruleForNewIntent(id)` = weekdays, afternoon, 1 per day.
- `analyticsId(id)` = the id for defaults, `"custom"` otherwise.
- `IntentRule?.label()`: `null` → "Stays where it is"; empty days → "Never returns"; weekend/weekdays/
  all days → "Weekend mornings" / "Weekday afternoons" / "Evenings"; otherwise "Mon, Wed · Morning".

## Links (`link/Links.kt`)

`Links.check(raw): LinkCheck` (`Valid(url, normalized)` | `Invalid(message)`):

1. Trim; empty → "Paste or type a link first."
2. Reject schemes `tel mailto sms file javascript data intent content` → "Only web links (http or https) can be saved."
3. Add `https://` when no `http(s)://` prefix (so bare `youtube.com/x` is valid).
4. Reject whitespace, unparsable URIs, hosts without a `.` or starting/ending with `.` → "That doesn't look like a web link."

`Links.normalize(url)` (the **duplicate key**): strip scheme, drop `#fragment`, lower-case host, remove
`www.` and `m.` prefixes, trim trailing `/` on the path, drop tracking query params, sort the
remaining query params. **No host is special**: `youtu.be/x` and `youtube.com/watch?v=x` are different keys. Tracking params removed: `utm_*`, `fbclid`, `igsh`, `igshid`, `si`, `gclid`,
`ref`, `ref_src`, `ref_url`, `mc_cid`, `mc_eid`, `s`, `t`, `feature`.

Other helpers: `extractFirst(text)` (first `http(s)://…` in text, trailing `.,)]!?;:` trimmed),
`couldBeNote(raw)` (non-blank and not a valid link), `noteTitleFrom(text)` (first non-blank line,
≤ 80 chars), `host(url)` (lower-case, no `www.`).

## Platforms (`platform/`, `model/Platform.kt`, `link/Links.kt`)

A platform is the site's own name, read from its page (see [Details](#details-details)). Until then it is the host.

`PlatformFacets.of(items): List<PlatformFacet(platform, count)>` — the Filter sheet's list: link saves only, grouped by platform (so
`en.wikipedia.org` and `de.wikipedia.org` are one `Wikipedia` once both pages say so), most saves
first then name (case-insensitive). `PlatformFacets.visible(facets, selected, expanded, limit = 6)` folds a long list after 6, keeps the
selected platform in view, and never folds a single platform.

`PlatformDetector.detect(url): Detection(platform, kind, host)` — no network, no site list. `platform = Platform.fromUrl(url)`; `kind` is
`PDF` when the path ends `.pdf`, else `WEBPAGE`; `Detection.creator` = the host (notes: empty) until a page names an author.

`DefaultTitles.of(url)` — readable title when none is known (see also `Item.displayTitle`): the URL without its scheme, for every site.

## Intent guessing (`intent/IntentGuesser.kt`)

`IntentGuesser(validIntentIds).guess(GuessInput, CorrectionStats): String`:

1. Lower-case `url + title + description`. Keyword rules are tried **in this order**: Cook, Buy, Go,
   Learn, Try. The first rule whose regex (`\b(…)\b`) matches wins, provided the intent id is still
   valid. (So "recipe" beats "tutorial" when both appear.)
2. Else the learned correction: for key `"<platform id>:<KIND>"` (e.g. `instagram:REEL`, `chatgpt:WEBPAGE`; per site, lower case), the intent with the highest count
   **if count ≥ `MIN_CORRECTIONS` (2)** and the intent is valid.
3. Else the default: NOTE→keep; video kinds→watch; CHANNEL/PROFILE/SUBREDDIT/FILE/FOLDER→keep; everything else
   (including PDF)→read. A save is guessed from its URL first, then **guessed again when its page arrives** (`og:type` video → watch), unless the person chose an intent.

Keyword lists live in the class (cook: recipe, cooking, dinner, pasta, soup…; buy: deal, price,
review, amazon…; go: travel, itinerary, restaurant, hotel…; learn: tutorial, course, how to, python…;
try: diy, workout, routine, tips…). Edit them there; tests: `IntentGuesserTest`.

`CorrectionStats.key(platform, kind)` builds the map key used by the repository's
`intent_corrections` table.

## Scheduling (`schedule/Scheduling.kt`)

`ReturnScheduler.returnAt(rule, now: ZonedDateTime, date?)`:

| Rule | Result |
| --- | --- |
| `DEFAULT`, `NEVER` | `null` |
| `TONIGHT` | 19:00 today, or 19:00 tomorrow if already past |
| `WEEKEND` | the next weekend morning (09:00) still ahead: Saturday, else Sunday, else next Saturday |
| `NEXT_WEEK` | next Monday 09:00 |
| `DATE` | `date` at 09:00 in `now`'s zone (`date` required) |

`ReturnScheduler.label(rule, at, zone)` → "tonight at 7 pm", "Saturday morning"/"Sunday morning",
"next Monday", "on 12 Nov". Used in toasts and Library tags ("Back …").

`HomePicker(maxPicks = 3).pick(items, rules, now): List<Pick>`:

- Candidates: `ACTIVE` **links only** (notes never appear), `returnRule != NEVER`.
- **Explicit return** (`returnAt != null`): not eligible until `returnAt` has passed; once due it scores
  `100 + ageBonus` and is **not** subject to `maxPerDay`.
- Otherwise the item needs a rule for its intent (none → never, e.g. Just keep), today must be in the
  rule's days, and the item must be at least `FRESH_GRACE` (**12 hours**) old.
- Score = `10` + `6` if the rule's time of day equals now's slot + `lengthFit` + `ageBonus` −
  `recentlyShownPenalty`.
  - `lengthFit(seconds, weekend, slot)`: unknown → 0; long (≥ 20 min) on weekend or evening → +3;
    short on a weekday morning/afternoon → +2; long otherwise → −2; else 0.
  - `ageBonus` = whole weeks since creation, clamped 0..4.
  - `recentlyShownPenalty`: last surfaced < 20 h ago → 0 (keep the day's picks stable); < 48 h → 6;
    < 7 days → 3; else 0; plus `min(surfacedCount, 5)`.
- Sort by score descending, then a stable per-day hash tie-break (`id + date`).
- Take up to 3, enforcing each intent's `maxPerDay` (explicit returns are exempt).

`TidyUp.candidates(items, now)`: `ACTIVE` items (links **and** notes) not waiting on a future `returnAt`,
with `lastTouchedAt` ≥ 90 days old (`STALE_AFTER`), oldest first.

`Trash`: `RETENTION = 30 days`; `daysLeft(deletedAt, now)` (rounded up to days, min 0); `isExpired`.

`Weeks`: Monday–Sunday. `weekStart(date | instant, zone)`; `label(weekStart)` → "Mon 2 – Sun 8 Nov" or
"Mon 27 Oct – Sun 2 Nov".

`doneLabel(intents, intentId)` → the intent's `doneLabel`, else "Done".

## Reminders (`schedule/Reminders.kt`)

`QuietHours` (`TEN_TO_SEVEN` 22→7, `ELEVEN_TO_SIX` 23→6, `NINE_TO_EIGHT` 21→8; `contains(hour)` handles
the midnight wrap; `next()` cycles). **Persisted by ordinal** — do not reorder.

`Reminders.decide(state, now, hasPick)`:

1. In quiet hours → `None`.
2. `lastDailySent == today` → `None` (**one notification a day, shared by daily and weekly**).
3. Weekly on, Sunday, hour ≥ 18, and `lastWeeklySentWeek != this week` → `Weekly`.
4. Daily on and `hasPick` → `Daily`.
5. Else `None`.

## Details (`details/`)

There is no per-site fetch plan: **every link is treated the same** (Instagram, Facebook and Threads included). `DetailsFetcher`
reads the page; if the page advertises an oEmbed endpoint (`<link rel="alternate" type="application/json+oembed" href="https://…">`,
https only) it reads that too. All public and keyless: no API keys, login, paywall bypass or proxy. Notes are never fetched.

`DetailsParser`:

- `parsePage(html, baseUrl)`: **site name** = `og:site_name` → `application-name`, accepted by `Platform.cleanSiteName`. Title = `og:title` →
  `twitter:title` → `<title>`. A **generic title returns empty details** (bot-check/error wording such as "Just a moment", "Access denied",
  exact "404"/"Not found"/"Blocked", or a title that is just the site's own fetched name). A page that serves only its own name (a login wall)
  still returns that name, so the platform gets named. Description: `og:description`/`twitter:description`/`description`. Image:
  `og:image:secure_url`/`og:image`/`twitter:image` (made absolute). Creator: `author`, `article:author` (non-URL), `itemprop=name`,
  `twitter:creator` (**not** `og:site_name`, which is the platform). Kind: `og:type` `video*` or `twitter:card=player`→VIDEO, `article`→ARTICLE,
  `profile`→PROFILE, else none. Length: `itemprop=duration` (ISO-8601), `og:video:duration`/`video:duration` (seconds), `twitter:data1` "N min",
  or an `<article>` word count (≥ 120 words, 230 wpm, rounded up to whole minutes). Favicon: touch icon first, then any `rel~=icon`, SVG skipped.
  `oEmbedUrl`: the advertised endpoint above.
- `parseOEmbed(body)`: `title`, `author_name`, `thumbnail_url`, `duration`, `provider_name` (site name). With no `title`, the first `<p>` of `html`
  (≤ 90 chars, also the description and a reading-time length), else "Post by <author>".
- `isoDurationSeconds("PT1H2M3S")`.
- The fetcher merges oEmbed over page for title/creator/thumbnail/length; the **page's** site name wins over the oEmbed provider's.

`LinkDetails.isEmpty` ignores `faviconUrl` ("an icon alone says nothing about the link") and `oEmbedUrl`; a site name or kind alone is enough to count.

`Item.mergeDetails(d, now)`: for each of title/description/creator/thumbnail/length, keep the current
value if the field is in `userEdited` **or** the fetched value is blank; favicon is replaced only when
the fetch has one; **`platform` becomes the fetched site name** (the person cannot edit it; notes never change) and **`kind` is refined only
from `WEBPAGE`** (to VIDEO/ARTICLE/PROFILE/PDF); sets `detailsFetchedAt`, `updatedAt`, bumps `revision`. Never blanks a known field.

`formatLength(seconds, isVideo)` → "12 min", "1 h 5 min", plus " read" for non-video; "" for null/≤0.

`Edits.kt`:

- `Item.withEdits(DetailEdits, now)`: for title/description/creator, normalise whitespace, trim, cap
  (`EditLimits`: title 200, description 1000, creator 80). Unchanged → no-op. Changed non-empty →
  field added to `userEdited`. **Emptied → removed from `userEdited`** (handed back to Caddis; next
  fetch may refill). Returns the same instance if nothing changed.
- `Item.withLink(LinkCheck.Valid, now)`: notes and same-URL are no-ops. If the duplicate key
  is unchanged (cosmetic change) only `url` changes, so a name the page already gave is kept. Otherwise re-detect platform/kind,
  reset title/description/creator/thumbnail/length for fields **not** in `userEdited`, creator becomes
  the host, clear `faviconUrl` and `detailsFetchedAt` (so details refetch).
- `Item.needsDetails()` = link, has URL, `detailsFetchedAt == null`.

`Favicons.forItem(item)`: null for notes; else the stored `faviconUrl` or `https://<host>/favicon.ico`. `rootIcon(url)`.

## Search (`search/Search.kt`)

- `SearchQuery.terms(q)`: lower-case, tokens of letters/digits (`[\p{L}\p{N}]+`), distinct.
- `toFtsMatch(q)`: terms joined by spaces, each as a prefix (`word*`) → implicit AND in FTS4; `null` if no terms.
- `explain(item, q)`: "Matched in the title|description|your note|your reason|the creator|the link|the
  source" for the first field where any term starts with the **first** query term, else "Matched your words".
- `resultBucket(count)` → `0, 1, 2-5, 6-20, 21+` (analytics).

## Notes (`notes/Markdown.kt`)

`toPlainText(md)` strips code fences, links (keeps text), headings, quotes, bullets/numbering,
emphasis markers and HTML, collapses whitespace. `preview(md, max = 110)` truncates with `…`.

## Share text (`share/ShareText.kt`)

`ShareText.of(item)`: `null` if no URL. Otherwise `[title\n]<cleanUrl>\n\nShared via Caddis app`. The
title is the **real** `title` only (never a guessed default), whitespace-collapsed, ≤ 100 chars with
`…`. `subject(item)` is the same title or `null`. `cleanUrl` removes only identity-tracking params
(`utm_*`, `fbclid`, `gclid`, `igsh`, `igshid`, `si`, `mc_cid`, `mc_eid`) and keeps everything else,
including `t` and the fragment. **Never includes reason, note, intent or collections.**

## Export (`export/Exporter.kt`)

- `toJson(items, intents, collections, now)`: pretty JSON `{format:"caddis-export", version:1,
  exportedAt, intents[], collections[], items[]}`. Item fields: id, type, url, platform (label), kind
  (label), title, description, creator, thumbnailUrl, lengthSeconds, note, intent, reason, sourceApp,
  sourceFrom, status, createdAt, doneAt, deletedAt, returnAt, collections (sorted ids). Includes
  deleted items (status `deleted`). Bump `FORMAT_VERSION` if the shape changes.
- `toHtmlBookmarks(items, collections, intents)`: Netscape bookmark file. Links only, deleted
  excluded; one folder per non-empty collection, then "All saves" for items in no collection; `TAGS`
  = intent name (+ "done"); `<DD>` = reason; all text HTML-escaped.

## Analytics (`analytics/Analytics.kt`)

`AnalyticsEvent` is a sealed class — **the only allowed events**, with typed params so URLs, titles,
notes, reasons, creators and collection names cannot be attached:

| Event (`name`) | Params |
| --- | --- |
| `SaveCreated` (`save_created`) | platform (`link` or `note` only, never a site), entry (`share_sheet`/`in_app`), intent_guessed |
| `IntentChanged` (`intent_changed`) | from, to (custom intents reported as `custom`) |
| `ItemOpened` (`item_opened`) | platform (`link` or `note`), entry (`home`/`library`/`search`) |
| `ItemDone` (`item_done`) | intent, age bucket |
| `ItemDeleted` (`item_deleted`) | intent, age bucket, source (`item_page`/`tidy_up`) |
| `HomeViewed` (`home_viewed`) | picks, acted |
| `TidyUpFinished` (`tidy_up_finished`) | kept, deleted |
| `SummaryViewed` (`summary_viewed`) | from_notification |
| `NotificationSetting` (`notification_setting`) | setting, on |
| `SearchRun` (`search_run`) | results bucket |

`ageBucket(createdAt, now)` → `0d, 1-6d, 7-13d, 14-30d, 31-90d, 90d+`. `AnalyticsSink` is a
`fun interface` port; today it only logs in debug builds.
