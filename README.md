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
| Row/stitch counters inside each pattern | Planned |
| Import and view PDFs | Planned |
| Import from a web page (paste a link or share from the browser) | Planned |
| Search, tags, status filters | Planned |
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

## Project layout

```
app/src/main/java/io/github/juliegaunt/chainone/
├── data/
│   ├── db/           Room database: tables (Entities.kt) and queries (DAOs)
│   ├── files/        Copies photos and PDFs into app-private storage
│   ├── repository/   Keeps the database and files in step
│   └── settings/     Small UI preferences
└── ui/               Jetpack Compose screens, one folder per screen
app/src/test/         Unit tests (run on your computer)
app/schemas/          Database schema history, used for future upgrades
```

The app uses Kotlin, Jetpack Compose (Material 3), Room, Coil and (for web import) Jsoup.

## License

Copyright (C) 2026 Julie Gaunt

Chain One is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the [full license](LICENSE) for details.
