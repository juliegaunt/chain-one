<img src="design/chain-one-logo.svg" alt="Chain One logo" width="96">

# Chain One

A personal crochet pattern library for Android, with a built-in row counter.

- **Offline.** Patterns, photos and PDFs live on your phone. The internet is used only when you import a pattern from a web page.
- **No ads, no accounts, no analytics, no cloud.** Android's automatic cloud backup is turned off for this app. Your library leaves the phone only when you export a backup yourself.
- **Free and open source** under the [GNU General Public License, version 3](LICENSE) or later.

## Features

| Feature | Status |
|---|---|
| Library (grid or list) with cover photos and favorites | ✅ |
| Add and edit patterns by hand | ✅ |
| Import from a web page (paste a link or share from the browser), with an offline copy of the page | ✅ |
| Search, tags, status filters | ✅ |
| Row/stitch counters inside each pattern (repeat counters, undo, volume-button counting) | ✅ |
| The instructions highlight the row the counter is on (tap a row to jump there) | ✅ |
| Import and view PDFs, with a movable row marker | ✅ |
| Backup and restore to a single zip file | Planned |

## Building

### What you need

- [Android Studio](https://developer.android.com/studio), current stable version. It includes the Android SDK and a Java runtime (JDK 21).
- An Android phone running Android 8.0 or newer, or the emulator that comes with Android Studio.

### In Android Studio

1. **File → Open** and choose the `chain-one` folder.
2. Wait for the "Gradle sync" bar at the bottom to finish. The first sync downloads Gradle and the libraries, which takes a few minutes.
3. Choose a device in the toolbar dropdown and press the green **Run ▶** button.

### From the command line

Android Studio creates `local.properties` with your SDK path. If you build without ever opening Android Studio, create it yourself:

```
sdk.dir=/home/<you>/Android/Sdk
```

Then run:

```bash
./gradlew assembleDebug        # build the app → app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # run the unit tests
./gradlew installDebug         # install on a connected phone
```

Gradle needs JDK 17 or newer. The JDK bundled with Android Studio works:

```bash
export JAVA_HOME=/path/to/android-studio/jbr
```

## Running on your phone

1. On the phone, open **Settings → About phone** and tap **Build number** seven times. This turns on Developer options.
2. Go to **Settings → System → Developer options** and turn on **USB debugging**. The menu location varies by phone; search Settings for "Developer options" if you can't find it.
3. Connect the phone with a USB cable and accept the "Allow USB debugging?" prompt.
4. In Android Studio, choose the phone in the device dropdown and press **Run ▶**.

**Wireless:** on Android 11+, turn on **Wireless debugging** in Developer options, then in Android Studio use **Device Manager → Pair devices using Wi-Fi**.

## Importing from a website

Tap **Add pattern → From a website** and paste a link, or in your phone's browser tap **Share → Chain One**. Chain One downloads the page, fills in what it can find (title, designer, hook size, yarn weight, materials, instructions, cover photo) and shows it to you to check before anything is saved. It also keeps a cleaned-up copy of the page, with its photos, on the pattern's **Web page** tab, so the original wording stays on your phone even if the website changes or disappears.

Some pages don't contain the pattern itself: video-only tutorials, directory sites that link to the designer's blog, and Ravelry listings for paid or off-site patterns. Chain One then says it couldn't find the instructions, and you still get the page copy.

**Trying the share button on the emulator:** open Chrome on the emulator, go to a pattern page, tap **⋮ → Share…** and choose **Chain One**. The first time, Chain One may be under **More** in the share sheet.

## PDF patterns

Tap **Add pattern → From a PDF** and pick the file, or in another app (Files, Gmail, your browser's downloads) tap **Share** or **Open with → Chain One**. A link to a PDF pasted on the web import screen works too, and the link is kept as the pattern's source. Chain One copies the PDF onto your phone, uses its first page as the cover photo and the file name as the title, and shows you the edit screen to fill in anything else before saving. Chain One can't read the details inside a PDF, so add the hook size, yarn and tags yourself if you want to search by them.

The PDF is on the pattern's **PDF** tab: drag to scroll, pinch or double-tap to zoom, with the counter underneath. It reopens on the page you were reading. To add more PDFs to a pattern (a purchased pattern sometimes comes as several), or to rename or remove one, use the pattern's edit screen.

**Row marker:** the striped-bar button at the top right of a PDF puts a see-through bar over the page, like the magnetic strip on a paper pattern holder. Drag it onto the row you're on, drag the tab on its bottom edge to make it taller or shorter, or long-press any row to move it there. The arrow buttons move it down or up by its own height, onto the next page at the bottom. It stays where you left it.

Password-protected PDFs can't be shown, because Android's built-in PDF reader can't open them.

**Trying it on the emulator:** drag a PDF from your computer onto the emulator window. It lands in the emulator's **Downloads** folder, where **Add pattern → From a PDF** can find it.

## Project layout

```
app/src/main/java/io/github/juliegaunt/chainone/
├── data/
│   ├── db/           Room database: tables (Entities.kt) and queries (DAOs)
│   ├── files/        Copies photos and PDFs into app-private storage
│   ├── importer/     Web and PDF import: downloads a page, reads the pattern out of it, saves an offline copy
│   ├── pdf/          Draws PDF pages with Android's built-in PDF reader
│   ├── repository/   Keeps the database and files in step
│   └── settings/     Small UI preferences
└── ui/               Jetpack Compose screens, one folder per screen
app/src/test/         Unit tests (run on your computer)
app/schemas/          Database schema history, used for future upgrades
design/               Logo source files (the app's icon and title are converted from the SVG)
```

The app uses Kotlin, Jetpack Compose (Material 3), Room, Coil and (for web import) Jsoup.

The page reader's tests run against small sample pages in `app/src/test/resources/pages/`. They're written for the tests rather than copied from real sites, since the patterns on those sites belong to their designers.

## License

Copyright (C) 2026 Julie Gaunt

Chain One is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the [full license](LICENSE) for details.

Parts that come from elsewhere keep their own licenses:

- Some icons are from Google's [Material Symbols](https://fonts.google.com/icons) (Apache License 2.0).
- The "Chain One" lettering on the launch screen is drawn from the [Noto Sans](https://fonts.google.com/noto/specimen/Noto+Sans) font (SIL Open Font License 1.1).
