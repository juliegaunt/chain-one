# Chain One

Offline, ad-free Android app for storing crochet patterns (from URLs and PDFs) with a built-in row/stitch counter. Personal use, published open source on GitHub (GPL-3.0-or-later).

## Tech stack
- Kotlin, Jetpack Compose (Material 3), single-activity architecture
- Room for local storage; MVVM with ViewModels and Kotlin Flow
- Jsoup for HTML parsing; Coil for images
- Min SDK 26, target latest stable SDK
- Ask before adding any dependency not listed here

## Core rules
- Offline-first: the network is used ONLY when importing a pattern from a URL
- No analytics, ads, accounts, or cloud services
- Imported images and PDFs are stored locally in app storage
- Always keep a pattern's original source URL
- Every parsed field must be editable by hand
- Counter changes save automatically

## Features (see milestones)
- Import from URL (paste screen + Android share sheet), with a readable-snapshot fallback when parsing is poor
- Import and view PDFs (Storage Access Framework)
- Library: search, tags, favorites, status (Want to make / In progress / Finished)
- Multiple named counters per pattern: large tap targets, undo, optional volume-button counting, keep screen awake
- Backup: export/import everything as a single zip

## Milestones (build in order; app must compile and run after each)
1. Project setup, data model, manual pattern entry, library screen
2. Counter
3. PDF import and viewer
4. URL import + share sheet
5. Search, tags, status
6. Backup/restore

## Working style
- I don't have any experience with Android development; explain any steps I need to do in Android Studio
- Propose a plan before large changes and wait for approval
- Write unit tests for the parser and database layer
- Run a build after changes and fix errors before saying a step is done
- Keep README.md build instructions up to date

## Conventions
- Package / applicationId: `io.github.juliegaunt.chainone`
- Versions live in `gradle/libs.versions.toml`; AGP 9 uses built-in Kotlin (no `kotlin-android` plugin)
- Manual DI via `AppContainer`; ViewModels use `viewModelFactory { initializer { appContainer ... } }`
- Navigation Compose with string routes (`ui/navigation/ChainOneNavHost.kt`)
- File paths in the DB are relative to `filesDir`, under `patterns/<id>/` (see `FileStore`)
- UI strings go in `res/values/strings.xml`
- DB tests use Robolectric (`./gradlew testDebugUnitTest`)

## Progress
- Milestone 1 done (all tables exist; schema v1 in `app/schemas/`)
- Current milestone: 2 (Counter)
