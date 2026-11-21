# UI

Jetpack Compose + Material 3, with project components on top. Source: `app/src/main/java/com/nexxlabs/caddis/ui/`
(plus `MainActivity.kt`, `share/ShareActivity.kt`). Style follows the Caddis prototype: warm paper
neutrals, one river-teal accent, Bricolage Grotesque for titles, DM Sans for body. Copy is short, plain,
never guilt-inducing.

## Shell

- `MainActivity` → `CaddisRoot(main, capture)` (`ui/shell/CaddisShell.kt`): `CaddisTheme`, background `C.colors.bg`,
  `AnimatedContent` on `onboarded` (null → nothing, false → `OnboardingScreen`, true → `CaddisShell`) with a fade.
- `CaddisShell`: `rememberNavController`, `Column { NavHost (weight 1) ; BottomBar }`, overlaid `AddButton` (FAB), `ToastHost`,
  `CaptureSheetHost`. Chrome visibility: bottom bar on the three tab routes; FAB only on Home and Library. The bar keeps showing
  the tab you left while it animates away. Toasts sit above the bar (84 dp) or near the bottom when the bar is hidden.
  **Hide on scroll** (`BarVisibility.kt`): on tab screens a `nestedScroll` connection around the NavHost feeds finger drags to
  `BarVisibility`; dragging content up 24 dp hides the bar (and the FAB and toasts glide down to 20 dp), any drag the other way, a
  drag with nothing left to scroll, or a tab change shows it again. Item/note/other non-tab routes never drive it.
- `BottomBar.kt`: `enum Tab(HOME "home", LIBRARY "library", SETTINGS "settings")`. The selected tab is found with `route.startsWith(tab.route)`.
  Tabs have no ripple. The active mark is one `accentTint` pill that glides behind the tabs (`animateFloatAsState` slot index,
  380 ms ease-out, drawn with `graphicsLayer.translationX`), while the icon and label colours cross-fade over `Motion.Short`.
- `ShareActivity` hosts only `CaptureSheetHost(vm)` in a translucent window with `CaddisTheme`.

## Routes (`ui/nav/Navigation.kt`)

`HOME_ROUTE = "home?week={week}"` is the start destination (`week` bool arg, default false).

| Route | Screen (file) | ViewModel | Notes |
| --- | --- | --- | --- |
| `home?week={week}` | `HomeScreen` (`home/HomeScreen.kt`) | `HomeViewModel` | `screen()` (shared-element capable) |
| `library` | `LibraryScreen` (`library/LibraryScreen.kt`) | `LibraryViewModel` | search, filters, collections tab |
| `settings` | `SettingsScreen` (`settings/SettingsScreens.kt`) | `SettingsViewModel` | navigates by route string |
| `item/{id}` | `ItemScreen` (`item/ItemScreen.kt`) | `ItemViewModel` (reads `id` from `SavedStateHandle`) | `screen()` |
| `note/{id}` | `NoteScreen` (`item/NoteScreen.kt`) | `ItemViewModel` | standalone notes only; a link's note is a plain text box on `item/{id}` |
| `shelf/{collectionId}` | `ShelfScreen` (`library/ShelfScreen.kt`) | `LibraryViewModel` (reads `collectionId`) | `screen()` |
| `collections` | `CollectionsScreen` (`manage/ManageScreens.kt`) | `CollectionsViewModel` | |
| `tidy` | `TidyScreen` | `TidyViewModel` | |
| `trash` | `TrashScreen` | `TrashViewModel` | |
| `reminders`, `data`, `export`, `about`, `privacy` | `RemindersScreen`, `YourDataScreen`, `ExportScreen`, `AboutScreen`, `PrivacyScreen` (`settings/SettingsScreens.kt`) | `SettingsViewModel` (except Privacy) | `data` is "Your data"; `export` is "Export and cache" (the old `data` screen); `about` is "About us" |
| `rules`, `intents` | `RulesScreen`, `IntentsScreen` (`settings/IntentScreens.kt`) | `SettingsViewModel` | |

External routes handed to `MainActivity` (`route` extra): `item/<id>`, `note/<id>`, `home?week=true`.

Adding a route: add a `composable(...)` (use `screen(...)` if it shows a thumbnail that should fly between screens) in
`CaddisNavGraph`, and — if it is a tab — extend `Tab` and `TAB_ROUTES` in `Motion.kt`.

## Prototype screen numbers → code

| # | Screen | Code |
| --- | --- | --- |
| 1 | Google sign-in | **not built** (no account or sync UI anywhere) |
| 2 | Onboarding: five swipeable intro cards, then "Want a nudge now and then?" | `ui/onboarding/OnboardingScreen.kt` (`OnboardingScreen`) |
| 7a | Home, first run ("Nothing saved yet") | `HomeScreen` (`FirstRun`) |
| 3 / 4 / 5 | Save sheet (the details card has one pencil, "Edit details", that opens an inline editor: Title, Platform, By, Who sent it) / Duplicate / Add | `ui/capture/CaptureSheets.kt` (`SaveSheet`, `DuplicateSheet`, `AddSheet`) |
| 7, 8, 21 | Home, all clear, This week | `HomeScreen` (`PickCard`, `WeekCard`, empty state) |
| 9 / 10 | Bring back later (Tonight, This weekend, Next week, Pick a date, **Not again**) / Change intent (and Edit details); Just keep reads "For when you need it" | `ui/sheets/Sheets.kt` (`LaterSheet`, `IntentSheet`, `EditDetailsSheet`) |
| 11, 13, 17, 18 | Library, filters, search | `LibraryScreen` (`SearchHeader`: title + search icon + Trash at rest; the icon animates into a full-width field over `Motion.Medium` (title and Trash fade out, icon turns into a back arrow, keyboard opens); the body fades between the library and `SearchBody`; icon description is "Search your saves", then "Close search"; the field is "Search saves"); the list is the active saves, then `CompletedHeader` ("COMPLETED · n", Show/Hide, collapsed by default, `stateDescription` Expanded/Collapsed) and, when open, the latest 3 completed rows plus `ShowAllCompleted`; completed rows use `ItemRow(completed = true)`, which draws `CompletedTick` on the thumbnail) (+ `FilterSheet`; its Platform group is built from `LibraryState.platforms`, not a fixed list; `showStatus = false` on the Library; there is no Kept chip) |
| 12 | Collection shelf | `ShelfScreen` |
| 14 / 19 / 20 | Collections / Tidy-up / Trash | `ui/manage/ManageScreens.kt` |
| 15 / 16 | Item page / Note editor | `ui/item/ItemScreen.kt`, `NoteScreen.kt` |
| 22–29 | Settings hub, reminders, your data, export, about, privacy, rules, intents | `ui/settings/SettingsScreens.kt`, `IntentScreens.kt` |

## Onboarding (`ui/onboarding/OnboardingScreen.kt`)

A `HorizontalPager` of `Onboarding.PAGE_COUNT` (6) pages, test tag `ONBOARDING_PAGER_TAG`. Swiping moves between cards; the main button
glides to the next one (420 ms, `Motion.Standard`); system back goes one card back and leaves the app from the first. Layout: a Skip link on top
(fades out, and leaves the accessibility tree, on the last page), the pager, then a slot that shows the progress dots on the intro cards and a
"Not now" button on the reminders question, then the main button (only its words cross-fade). Pages stay composed one page out (`beyondViewportPageCount = 1`)
so a swipe never waits for composition.

Movement while swiping: every page fades with its distance from view, and its drawing trails behind its words (`translationX` of 30% of its width, a
slight scale) so the two move at different speeds. The dots stretch towards the page being dragged to (they read `currentPage + currentPageOffsetFraction`).

Cards: Welcome (`AppMark`, headline, privacy line), then `IntroPage` for Save from any app, Say why you kept it, It comes back when it fits and
Finish it, or keep it. Their drawings (`ShareArt`, `WhyArt`, `BackArt`, `FinishArt`) are static pictures made from the app's own pieces with made-up
examples; they are hidden from screen readers and nothing in them is tappable. The reminders page (`NudgesPage`) is the earlier onboarding screen: daily
reminder and weekly summary, both off by default, using `rememberNotificationToggle` and `SettingRowNoLine` from `SettingsComponents.kt`.
Copy is in `res/values/strings.xml` (`onboarding_*`), except the reminders page, which keeps its earlier inline text.

Both exits (Continue/Not now, and Skip) call `SettingsViewModel.finishOnboarding(skippedAtStep)`: logs `OnboardingFinished` and sets `onboarded`.
`CaddisRoot` then fades to the shell.

Home before the first save: `HomeState.firstRun` (library has no saves at all) shows "Nothing saved yet" and three ways to save instead of
"You’re all caught up", and hides the This week card. It also returns whenever the library is empty, for example after "Delete all data".

## ViewModels

| ViewModel | Exposes | Notes |
| --- | --- | --- |
| `MainViewModel` | `onboarded: StateFlow<Boolean?>`, `pendingRoute`, `messenger` | Activity-scoped. |
| `CaptureViewModel` | `sheet: StateFlow<CaptureSheet>`, `nav: SharedFlow<CaptureNav>`, `intents`, `collections` (newest first), `savedItem` | **One instance per activity** (MainActivity and ShareActivity each have their own). Saves run on the app scope. |
| `HomeViewModel` | `HomeState` | `HomePicker` + 5-minute tick. |
| `LibraryViewModel` | `LibraryState` (items, sections (`LibrarySections`: active, completed), hits, intents, collections+counts, filters, query, searching, showCollections, recent) | Shared by Library and Shelf. |
| `ItemViewModel` | `item`, `intents`, `collections` (newest first), `saving` | Note autosave, item actions, `saveDetails`, `toggleCollection`, `createCollection` (toast with Undo). |
| `TidyViewModel`, `TrashViewModel`, `CollectionsViewModel` | see `ManageViewModels.kt` | |
| `SettingsViewModel` | `settings`, `rules`, `intents` (`IntentRow`), `cacheBytes`, intent errors, `update`, `checking` | Notifications, background-work toggles, intents/rules, export, cache, delete all, check for / start / finish an app update, `tell(message)` toast. |

`ItemActions` (`ui/ItemActions.kt`) is a singleton used by Home, Library, Item and Tidy ViewModels: `done`, `markActive`, `delete`,
`later`, `open`, `share`, `today()`. It also hosts display helpers: `Item.meta()`, `Item.savedLabel()`, `Item.rightTag(intents)`, `Item.completedMeta(intents)`,
`intentName(intents, id)`, `LocalDate.dayMonth()`.

`Messenger.show(message, actionLabel = "Undo", undo?)` emits a `Toast`; `ToastHost` shows one at a time for 5 s with an action link (**Undo**, or `actionLabel`, e.g. **Restart**) when `undo` is set.

**Settings (`settings/`).** The third tab (gear icon, route `settings`; it was "Profile" until there was nothing profile-like on it: no account, no sync). It is a short hub that opens sub screens;
every card is a `SettingsGroup` (a `CCard` with no padding) of rows separated by `GroupDivider`, built from `NavRow` (icon tile, title, optional line and value, chevron or the external-link arrow) and `ToggleRow`
(`SettingsComponents.kt`). Icon tiles are neutral (`tint` on `fg2`) so the accent stays for what is on or active.

- **Hub (`SettingsScreen`)**, top to bottom: the update banner (only while Play has something), then Intents (count) / Return rules / Reminders (summary: Off, Daily on, Weekly on, Daily and weekly),
  then an "Enjoying Caddis?" card with three one-tap buttons (**Rate**, **Share**, **Feedback**), then Your data (live "N MB cached · N in Trash") / About us (version).
- **Update banner.** When `SettingsViewModel.update` (`AppUpdater.state`) is not `None`: `Available` → "Update Caddis" (the card and its **Update** button open Play's dialog), `Downloading` → "Updating Caddis" (no action),
  `Ready` → "Restart to update" (**Restart** installs). `MainActivity` registers the Play dialog launcher and runs `UpdatePrompter` while started.
- **Reminders (`reminders`)**: Daily reminder and Weekly summary switches (permission asked on API 33+ only when one is turned on), a preview card that dims when the daily reminder is off, and a warning card with
  **Open Android settings** (`AppLinks.notificationSettings`) when a reminder is on but Android blocks notifications (`rememberNotificationsEnabled`, re-read on resume). There are **no quiet hours**.
- **Your data (`data`)**: Export and cache (size) → `export`, Trash (count) → `trash`, the Pause on low battery switch, and **Delete all data**, a separate danger button at the bottom that opens the confirm dialog
  ("Delete all your data?" → **Delete everything**) and returns to Home with the back stack cleared.
- **Export and cache (`export`)**: JSON / HTML bookmarks buttons, the thumbnail cache meter, **Clear cache**.
- **About us (`about`)** (the hub row says "Version, updates, privacy"): a centred app mark, "Caddis" and "Version name (code)", one card with **Check for updates** and **Privacy policy** → `privacy`, and a footer pinned to the bottom ("Made by Nexxlabs" and the feedback address). *Check for updates* → `SettingsViewModel.checkForUpdate(launcher)`:
  `AppUpdater.refresh()` now, then `Available` opens Play's dialog, `Ready` restarts, and otherwise `checkStatus` (`Idle`, `Checking`, `UpToDate`, `Unreachable`) fills a status pill on the row. "Up to date" appears
  only after Play answered; a failed check shows "Couldn't check"; nothing is shown before the first tap. Update states take precedence ("Update ready", "Updating…", "Restart to update").
- **Rate / Share / Feedback** start the intents built by `AppLinks` (`settings/AppLinks.kt`): `ACTION_VIEW` on the Play listing URL, a chooser for `ACTION_SEND` text (one-line pitch + the Play link, nothing from the library),
  and `ACTION_SENDTO` `mailto:` to the feedback inbox with the subject "Caddis feedback" (no body, no device or library data). If nothing can handle one, a toast says so (the feedback toast prints the address).
  The Play URL and inbox address live only in `AppLinks`.
- **Return rules / Intents** (`IntentScreens.kt`): Return rules is one card of rows, the open one with its editor below it; Intents keeps its own row/edit-card layout.

## Capture sheets (`CaptureSheet` state machine)

`None` → `Add(mode, url, error, clipboard, offerNote)` → `Saved(itemId, entry, sharedFrom)` | `Duplicate(existing, link, entry, sourceApp, titleHint)`.
`CaptureSheetHost` renders whichever is active. `saveTypedAsNote` and `submitNote` produce notes. See [flows.md](flows.md).

## Shared components (`ui/components/`)

- `Components.kt`: `CButton` (`BtnStyle` Primary/Accent/Default/Danger/Ghost, `small`), `CChip`, `Tag`, `CCard` (`feature`, `onClick`,
  `borderColor`), `Thumb` (thumbnail → site icon → glyph; `share` key for shared-element transition), `ItemRow`, `IconBtn`, `TopBar`,
  `BigTitle`, `SectionTitle`, `Eyebrow`, `Fine`, `Sub`, `Dot`, `Toggle`, `SettingRow`, `Chevron`, `EmptyState`, `AppMark`, `TextFieldBox`.
- `Placeholders.kt`: `PlaceholderRows` (greyed rows shaped like `ItemRow`, shown while a list is loading; appear after `PLACEHOLDER_GRACE_MS`, pulse
  softly, hidden from screen readers). Use these, not `EmptyState`, for "not loaded yet".
- `Fields.kt`: `CField` (text field with placeholder, label, `maxLength`, keyboard type, IME action).
- `DetailField.kt`: `DetailField` (a labelled `CField` for the details editor, with an optional reset arrow `Reset <label>` and
  "Edited by you. Reset to let Caddis fill it in.") and `canReset`. Shared by the save sheet's inline editor and `EditDetailsSheet`
  (Link, Platform, Title, By, Description; Platform and Link are hidden for notes).
- `Sheet.kt`: `CSheet` (bottom sheet), `Chips`, `CollectionChips` (toggle chips + the dashed "New" chip that becomes a name field; the owner holds the draft name), `CollectionsSection` (`CollectionsSection.kt`: the item-page and note "Collections" block, a read-only list with Edit/Add/Done that expands `CollectionChips` in place), `CenterBox`. `CChip` has a `dashed` style for "make a new one" chips.
- `Icons.kt`: `CIcons` — the prototype's 24×24 stroked icon set as `ImageVector`s. Add icons here, not as drawables.
- `sheets/Sheets.kt`: `rememberConfirm()` → `MutableConfirm` (two-tap confirm: first tap arms, second tap acts).
- Always give icon-only buttons a `contentDescription`/`label`; use `Role.Button`/`Role.Tab` on clickable rows (existing code does).

## Theme (`ui/theme/Theme.kt`)

- Colours: `CaddisColors` tokens `bg, surface, tint, line, fg, fg2, accent, accentTint, onAccent, good, warn, crit, scrim, toastLink`;
  `LightColors`/`DarkColors`; read via `C.colors` in composables. **Do not hard-code colours**; add a token (and both palettes) instead.
  Light: bg `#FAF8F4`, accent `#1F6F68`. Dark: bg `#121614`, accent `#5CC2B5`.
- Type: `CaddisType` (`h1, h2, section, sheetTitle, itemTitle, stat` in Bricolage Grotesque; `body, bodyLarge, strong, cardTitle, sub, fine, eyebrow,
  button, buttonSm, tag` in DM Sans). Fonts are variable TTFs in `res/font/` (`bricolage_grotesque.ttf`, `dm_sans.ttf`), both SIL OFL.
- `CaddisTheme(dark = isSystemInDarkTheme())` supplies Material `colorScheme` + `Typography` and `LocalCaddisColors`.
- `res/values/colors.xml`, `values-night/colors.xml`, `themes.xml` hold the splash/share window themes (`Theme.Caddis`, `Theme.Caddis.Splash`, `Theme.Caddis.Share`).
- Static user-facing strings that live in resources: only `app_name`, `share_label`, `share_link_chooser`, notification channel names/descriptions
  (`res/values/strings.xml`). Most screen copy is inline in Compose today; new static strings should go in `strings.xml`.

## Motion (`ui/Motion.kt`)

Calm: short, decelerating, never bouncy. Compose honours the system animator scale, so disabling animations makes everything instant.

- `Motion.Quick 120`, `Short 210`, `Medium 300`, `Out 90` ms; easings `Decelerate`, `Standard`.
- `navMotion(from, to, pop)`: moving between tabs, or *arriving* at a tab from deeper, **fades through**; going deeper slides in (a tenth of the width);
  going back slides out. Used by the `NavHost` transitions (`Motion.enter/exit`). Tested by `MotionTest`.
- `calmItem()` (list rows animate in/out/place), `pressScale(source)` (3% press-in), `sharedThumb(key)` + `OpenedItem` +
  `LocalSharedTransitionScope`/`LocalDestinationScope` (thumbnail grows from a list row into the item header; the origin list passes the item so the
  item screen can draw on its first frame).
- Cold start holds the AndroidX splash until settings are known.

## Rich-text notes

`NoteScreen` uses `com.mohamedrejeb.richeditor` (`rememberRichTextState`, `BasicRichTextEditor`), at version `1.0.0-rc13` (`gradle/libs.versions.toml`; check Kotlin compatibility before bumping).
Content is loaded with `setHtml(NoteMarkdown.toHtml(stored))` and saved as `NoteMarkdown.fromHtml(rich.toHtml())`, never with the library's `setMarkdown`/`toMarkdown`
(they do not escape or honour escapes; see [domain.md](domain.md#notes-notesmarkdownkt-notesnotemarkdownkt)).

Layout, top to bottom: `TopBar` (back, save status, ⋮ `More` menu) → title (`CField`, unboxed, h1 × 0.86) → chip row (intent chip with `Down`, one
selected chip per collection described "Collection: <name>", then "+ Collection"/"+ Add" described "Add to a collection") → divider → the body, whose
minimum height reaches the bottom of the screen so a tap anywhere under the text writes in it. There is no helper copy under the body.
- **Save status** shows only after something was changed on this visit: "Saving…" while the autosave pause runs, then a check and "Saved" (`fg2`, no green dot).
- **⋮ menu** (`DropdownMenu` on `surface`): the intent's done label (or "Mark incomplete" when done), "Bring back later" (`LaterSheet`), "Delete" (`crit`; moves it to Trash and goes back).
- **Collections sheet**: tapping any collection chip opens a `CSheet` with `CollectionChips` (tick chips, dashed "New" first) and Done; closing with a name typed creates it.
- **Formatting bar** (`FormatBar`): flat, full width, top hairline, 48 dp, docked above the keyboard (the screen uses `imePadding`), shown **only while the body has focus**.
  Icon buttons (`CIcons.Bold`, `Italic`, `BulletList`, `NumberList`, `Code`, `Link`), a divider, then `Undo` (dimmed when there is nothing to undo).
  Each button **lights up** (`accentTint` pill, accent icon, `selected` semantics) when its style applies at the caret or to the selection: bold/italic
  from `RichTextState.currentSpanStyle`, lists from `isUnorderedList`/`isOrderedList`, code from `isCodeSpan`, link from `isLink`. A style just
  switched on with no text yet counts, and it follows the caret as it moves.
- **Link sheet**: "Add a link" with Text (optional) and Address fields when the caret has no selection (`addLink`), Address only for a selection
  (`addLinkToSelection`), "Edit link" with Remove link when the caret is in one (`updateLink`/`removeLink`). Addresses go through `Links.check`; links
  are drawn in `accent`. Closing the sheet puts the focus back in the body.
- **Undo** keeps `EditHistory<NoteState>` (domain) of the stored Markdown plus caret, recorded from the same `snapshotFlow` that autosaves; undo reloads
  the state with `setHtml(NoteMarkdown.toHtml(…))` and is itself saved like any edit. The history lasts for the visit.
- An **empty body gets focus on open**, so a new note is ready to write in (keyboard and bar up).

The editor opens on `ItemViewModel.currentNote()` (typed but unsaved words win over the saved note) so a screen turn right after typing keeps the text.
