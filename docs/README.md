# Caddis docs (for AI agents)

These docs describe the code **as it is**, written for coding agents (and humans who want the
short version). They were rebuilt from the source at commit `7e54620`. **If a doc and the code
disagree, the code is right; fix the doc in the same change.**

Start with [../AGENTS.md](../AGENTS.md) for the working rules, then pick the doc you need.

## Map

| Doc | Read it when you need to… |
| --- | --- |
| [overview.md](overview.md) | Know what Caddis is, its vocabulary (Item, Intent, return rule…), the item lifecycle, what is built and what is absent. |
| [architecture.md](architecture.md) | Find where code lives, the module/layer rules, DI, scopes, startup, Android entry points, background work. |
| [domain.md](domain.md) | Change or read a product rule: link checks, platform detection, intent guessing, Home picking, scheduling, details parsing, search, export, analytics. |
| [data.md](data.md) | Touch Room, DataStore, the repository API, migrations, or the weekly counters. |
| [flows.md](flows.md) | Trace a user action end to end (share-save, details fetch, edit, Home, later, done/delete, search, tidy-up, notifications, export). |
| [ui.md](ui.md) | Add or change a screen, route, sheet, component, theme token, animation or toast. |
| [development.md](development.md) | Build, test, lint, CI, test helpers, and step-by-step recipes for common changes. |
| [invariants.md](invariants.md) | Check hard rules before you change behaviour, and read the traps and known gaps. |

## Reading order by task

- **Fix a bug in a rule** (e.g. wrong duplicate detection): `domain.md` → the file named there → its test in `domain/src/test`.
- **Change what a screen shows**: `ui.md` → the screen file → its ViewModel → `data.md` if it needs new data.
- **Add a field to a save**: `development.md` recipe "Add a field to Item" (it lists every file that must change).
- **Change a background behaviour** (details fetch, reminders): `flows.md` → `architecture.md` (work section) → `invariants.md`.
- **Anything touching privacy, analytics, fetching or AI**: `invariants.md` first.

## Conventions used in these docs

- Paths are relative to the repo root. `domain/…` is the `:domain` module, `app/…` the `:app` module.
- Kotlin package `app.caddis.domain.*` lives under `domain/src/main/kotlin/app/caddis/domain/`;
  `app.caddis.*` (the app) lives under `app/src/main/java/app/caddis/`.
- "The person" means the end user. Product copy is short, plain and guilt-free.
- Source comments contain short requirement labels such as `CAP-01`, `MET-03`, `BRB-02`,
  `FIN-02`, `INT-04`, `SRC-01`, `WK-01`, `ACC-02`, `NFR`, and "tech arch §…". They point at a
  product spec and a technical-decisions doc that **no longer exist in the repo** (they are in
  git history). Treat them as historical labels, not links. Do not add new ones; describe the
  behaviour in words.
- Prototype screen numbers in comments ("Screen 15") likewise refer to the design prototype, which
  is not in the repo. [ui.md](ui.md) maps them to files.

## Keeping these docs true

| If you change… | Update |
| --- | --- |
| A rule in `:domain` | `domain.md` (and `invariants.md` if it is a hard rule) |
| A table, DAO, repository method, DataStore key or migration | `data.md` |
| A route, screen, sheet, shared component or theme token | `ui.md` |
| A module, package, worker, Hilt module, manifest entry or startup step | `architecture.md` |
| A user-visible flow | `flows.md` |
| Build, tooling, tests or CI | `development.md` |
| What exists / does not exist | `overview.md` ("Built" and "Not built") |
