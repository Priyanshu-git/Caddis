# UI

Jetpack Compose + Material 3, with project components on top. Source: `app/src/main/java/app/caddis/ui/`
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
| `note/{id}` | `NoteScreen` (`item/NoteScreen.kt`) | `ItemViewModel` | also used for links' notes |
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
| 2 | Notifications onboarding | `OnboardingScreen` |
| 3 / 4 / 5 | Save sheet / Duplicate / Add | `ui/capture/CaptureSheets.kt` (`SaveSheet`, `DuplicateSheet`, `AddSheet`) |
| 7, 8, 21 | Home, all clear, This week | `HomeScreen` (`PickCard`, `WeekCard`, empty state) |
| 9 / 10 | Bring back later / Change intent (and Edit details) | `ui/sheets/Sheets.kt` (`LaterSheet`, `IntentSheet`, `EditDetailsSheet`) |
| 11, 13, 17, 18 | Library, filters, search | `LibraryScreen` (+ `FilterSheet`) |
| 12 | Collection shelf | `ShelfScreen` |
| 14 / 19 / 20 | Collections / Tidy-up / Trash | `ui/manage/ManageScreens.kt` |
| 15 / 16 | Item page / Note editor | `ui/item/ItemScreen.kt`, `NoteScreen.kt` |
| 22–29 | Notifications, rules, intents, data, account, privacy, profile | `ui/profile/ProfileScreens.kt` |

## ViewModels

| ViewModel | Exposes | Notes |
| --- | --- | --- |
| `MainViewModel` | `onboarded: StateFlow<Boolean?>`, `pendingRoute`, `messenger` | Activity-scoped. |
| `CaptureViewModel` | `sheet: StateFlow<CaptureSheet>`, `nav: SharedFlow<CaptureNav>`, `intents`, `collections`, `savedItem` | **One instance per activity** (MainActivity and ShareActivity each have their own). Saves run on the app scope. |
| `HomeViewModel` | `HomeState` | `HomePicker` + 5-minute tick. |
| `LibraryViewModel` | `LibraryState` (items, hits, intents, collections+counts, filters, query, searching, showCollections, recent) | Shared by Library and Shelf. |
| `ItemViewModel` | `item`, `intents`, `collections`, `saving` | Note autosave, item actions, `saveDetails`. |
| `TidyViewModel`, `TrashViewModel`, `CollectionsViewModel` | see `ManageViewModels.kt` | |
| `ProfileViewModel` | `settings`, `rules`, `intents` (`IntentRow`), `cacheBytes`, intent errors | Notifications, background-work toggles, intents/rules, export, cache, delete all. |

`ItemActions` (`ui/ItemActions.kt`) is a singleton used by Home, Library, Item and Tidy ViewModels: `done`, `markActive`, `delete`,
`later`, `open`, `share`, `today()`. It also hosts display helpers: `Item.meta()`, `Item.savedLabel()`, `Item.rightTag(intents)`,
`intentName(intents, id)`, `Platform.openLabel()`, `LocalDate.dayMonth()`.

`Messenger.show(message, undo?)` emits a `Toast`; `ToastHost` shows one at a time for 5 s with an **Undo** link when `undo` is set.

## Capture sheets (`CaptureSheet` state machine)

`None` → `Add(mode, url, error, clipboard, offerNote)` → `Saved(itemId, entry, sharedFrom)` | `Duplicate(existing, link, entry, sourceApp, titleHint)`.
`CaptureSheetHost` renders whichever is active. `saveTypedAsNote` and `submitNote` produce notes. See [flows.md](flows.md).

## Shared components (`ui/components/`)

- `Components.kt`: `CButton` (`BtnStyle` Primary/Accent/Default/Danger/Ghost, `small`), `CChip`, `Tag`, `CCard` (`feature`, `onClick`,
  `borderColor`), `Thumb` (thumbnail → site icon → glyph; `share` key for shared-element transition), `ItemRow`, `IconBtn`, `TopBar`,
  `BigTitle`, `SectionTitle`, `Eyebrow`, `Fine`, `Sub`, `Dot`, `Toggle`, `SettingRow`, `Chevron`, `EmptyState`, `AppMark`, `TextFieldBox`.
- `Fields.kt`: `CField` (text field with placeholder, label, `maxLength`, keyboard type, IME action).
- `Sheet.kt`: `CSheet` (bottom sheet), `Chips`, `CollectionPicker`, `CenterBox`.
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
Content is loaded with `setMarkdown` and saved with `toMarkdown`. Toolbar has bold, italic, bullets, numbers, inline code.
