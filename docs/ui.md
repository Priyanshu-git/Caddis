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
- `BottomBar.kt`: `enum Tab(HOME "home", LIBRARY "library", PROFILE "profile")`. The selected tab is found with `route.startsWith(tab.route)`.
- `ShareActivity` hosts only `CaptureSheetHost(vm)` in a translucent window with `CaddisTheme`.

## Routes (`ui/nav/Navigation.kt`)

`HOME_ROUTE = "home?week={week}"` is the start destination (`week` bool arg, default false).

| Route | Screen (file) | ViewModel | Notes |
| --- | --- | --- | --- |
| `home?week={week}` | `HomeScreen` (`home/HomeScreen.kt`) | `HomeViewModel` | `screen()` (shared-element capable) |
| `library` | `LibraryScreen` (`library/LibraryScreen.kt`) | `LibraryViewModel` | search, filters, collections tab |
| `profile` | `ProfileScreen` (`profile/ProfileScreens.kt`) | `ProfileViewModel` | navigates by route string |
| `item/{id}` | `ItemScreen` (`item/ItemScreen.kt`) | `ItemViewModel` (reads `id` from `SavedStateHandle`) | `screen()` |
| `note/{id}` | `NoteScreen` (`item/NoteScreen.kt`) | `ItemViewModel` | standalone notes only; a link's note is a plain text box on `item/{id}` |
| `shelf/{collectionId}` | `ShelfScreen` (`library/ShelfScreen.kt`) | `LibraryViewModel` (reads `collectionId`) | `screen()` |
| `collections` | `CollectionsScreen` (`manage/ManageScreens.kt`) | `CollectionsViewModel` | |
| `tidy` | `TidyScreen` | `TidyViewModel` | |
| `trash` | `TrashScreen` | `TrashViewModel` | |
| `notifications`, `rules`, `intents`, `data`, `account`, `privacy` | `NotificationSettingsScreen`, `RulesScreen`, `IntentsScreen`, `DataScreen`, `AccountScreen`, `PrivacyScreen` (`profile/ProfileScreens.kt`) | `ProfileViewModel` (except Privacy) | |

External routes handed to `MainActivity` (`route` extra): `item/<id>`, `note/<id>`, `home?week=true`.

Adding a route: add a `composable(...)` (use `screen(...)` if it shows a thumbnail that should fly between screens) in
`CaddisNavGraph`, and — if it is a tab — extend `Tab` and `TAB_ROUTES` in `Motion.kt`.

## Prototype screen numbers → code

| # | Screen | Code |
| --- | --- | --- |
| 1 | Google sign-in | **not built** (Account says "On this phone") |
| 2 | Onboarding: five swipeable intro cards, then "Want a nudge now and then?" | `ui/onboarding/OnboardingScreen.kt` (`OnboardingScreen`) |
| 7a | Home, first run ("Nothing saved yet") | `HomeScreen` (`FirstRun`) |
| 3 / 4 / 5 | Save sheet / Duplicate / Add | `ui/capture/CaptureSheets.kt` (`SaveSheet`, `DuplicateSheet`, `AddSheet`) |
| 7, 8, 21 | Home, all clear, This week | `HomeScreen` (`PickCard`, `WeekCard`, empty state) |
| 9 / 10 | Bring back later (Tonight, This weekend, Next week, Pick a date, **Not again**) / Change intent (and Edit details); Just keep reads "For when you need it" | `ui/sheets/Sheets.kt` (`LaterSheet`, `IntentSheet`, `EditDetailsSheet`) |
| 11, 13, 17, 18 | Library, filters, search | `LibraryScreen` (+ `FilterSheet`; its Platform group is built from `LibraryState.platforms`, not a fixed list; the chip row also has a **Kept · n** chip for Just keep) |
| 12 | Collection shelf | `ShelfScreen` |
| 14 / 19 / 20 | Collections / Tidy-up / Trash | `ui/manage/ManageScreens.kt` |
| 15 / 16 | Item page / Note editor | `ui/item/ItemScreen.kt`, `NoteScreen.kt` |
| 22–29 | Notifications, rules, intents, data, account, privacy, profile | `ui/profile/ProfileScreens.kt` |

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
reminder, quiet hours and weekly summary, all off by default, using `rememberNotificationToggle` and `SettingRowNoLine` from `ProfileScreens.kt`.
Copy is in `res/values/strings.xml` (`onboarding_*`), except the reminders page, which keeps its earlier inline text.

Both exits (Continue/Not now, and Skip) call `ProfileViewModel.finishOnboarding(skippedAtStep)`: logs `OnboardingFinished` and sets `onboarded`.
`CaddisRoot` then fades to the shell.

Home before the first save: `HomeState.firstRun` (library has no saves at all) shows "Nothing saved yet" and three ways to save instead of
"You’re all caught up", and hides the This week card. It also returns whenever the library is empty, for example after "Delete all data".

## ViewModels

| ViewModel | Exposes | Notes |
| --- | --- | --- |
| `MainViewModel` | `onboarded: StateFlow<Boolean?>`, `pendingRoute`, `messenger` | Activity-scoped. |
| `CaptureViewModel` | `sheet: StateFlow<CaptureSheet>`, `nav: SharedFlow<CaptureNav>`, `intents`, `collections` (newest first), `savedItem` | **One instance per activity** (MainActivity and ShareActivity each have their own). Saves run on the app scope. |
| `HomeViewModel` | `HomeState` | `HomePicker` + 5-minute tick. |
| `LibraryViewModel` | `LibraryState` (items, hits, intents, collections+counts, filters, query, searching, showCollections, recent) | Shared by Library and Shelf. |
| `ItemViewModel` | `item`, `intents`, `collections` (newest first), `saving` | Note autosave, item actions, `saveDetails`, `toggleCollection`, `createCollection` (toast with Undo). |
| `TidyViewModel`, `TrashViewModel`, `CollectionsViewModel` | see `ManageViewModels.kt` | |
| `ProfileViewModel` | `settings`, `rules`, `intents` (`IntentRow`), `cacheBytes`, intent errors, `update` | Notifications, background-work toggles, intents/rules, export, cache, delete all, start/finish an app update. |

`ItemActions` (`ui/ItemActions.kt`) is a singleton used by Home, Library, Item and Tidy ViewModels: `done`, `markActive`, `delete`,
`later`, `open`, `share`, `today()`. It also hosts display helpers: `Item.meta()`, `Item.savedLabel()`, `Item.rightTag(intents)`,
`intentName(intents, id)`, `LocalDate.dayMonth()`.

`Messenger.show(message, actionLabel = "Undo", undo?)` emits a `Toast`; `ToastHost` shows one at a time for 5 s with an action link (**Undo**, or `actionLabel`, e.g. **Restart**) when `undo` is set.

**Update row (Profile).** When `ProfileViewModel.update` (`AppUpdater.state`) is not `None`, Profile shows an **Update** section above Preferences: `Available` → "Update Caddis" (opens Play's dialog),
`Downloading` → "Updating Caddis" (no action), `Ready` → "Restart to update" (installs). `MainActivity` registers the Play dialog launcher and runs `UpdatePrompter` while started.

## Capture sheets (`CaptureSheet` state machine)

`None` → `Add(mode, url, error, clipboard, offerNote)` → `Saved(itemId, entry, sharedFrom)` | `Duplicate(existing, link, entry, sourceApp, titleHint)`.
`CaptureSheetHost` renders whichever is active. `saveTypedAsNote` and `submitNote` produce notes. See [flows.md](flows.md).

## Shared components (`ui/components/`)

- `Components.kt`: `CButton` (`BtnStyle` Primary/Accent/Default/Danger/Ghost, `small`), `CChip`, `Tag`, `CCard` (`feature`, `onClick`,
  `borderColor`), `Thumb` (thumbnail → site icon → glyph; `share` key for shared-element transition), `ItemRow`, `IconBtn`, `TopBar`,
  `BigTitle`, `SectionTitle`, `Eyebrow`, `Fine`, `Sub`, `Dot`, `Toggle`, `SettingRow`, `Chevron`, `EmptyState`, `AppMark`, `TextFieldBox`.
- `Fields.kt`: `CField` (text field with placeholder, label, `maxLength`, keyboard type, IME action).
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
(they do not escape or honour escapes; see [domain.md](domain.md#notes-notesmarkdownkt-notesnotemarkdownkt)). Toolbar has bold, italic, bullets, numbers, inline code.
Each toolbar button **lights up** (`accentTint` background, accent text, `selected` semantics) when its style applies at the caret or to the
selection: bold/italic from `RichTextState.currentSpanStyle`, lists from `isUnorderedList`/`isOrderedList`, code from `isCodeSpan`. A style just
switched on with no text yet counts, and it follows the caret as it moves. The editor opens on `ItemViewModel.currentNote()` (typed but unsaved
words win over the saved note) so a screen turn right after typing keeps the text.
