# Obsidian Android Daily Note Viewer

A native Android application built with **Kotlin**, **Jetpack Glance**, and the **Android Storage Access Framework (SAF)** that hosts an interactive home screen widget displaying today's Obsidian daily note.

---

## Features

- **Jetpack Glance Widget**: Modern, battery-friendly home screen widget built with Jetpack Glance & Material 3 styling.
- **Local-First & Scoped Storage (SAF)**: Uses Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with persistable URI permissions to directly read your local Obsidian markdown files without requiring dangerous storage permissions.
- **Dynamic Daily Note Resolution**: Resolves today's note matching `YYYY-MM-DD.md` (or custom date format) inside your configured daily notes subfolder or vault root.
- **Smart Markdown Cleaner**:
  - Automatically strips YAML frontmatter metadata (`--- ... ---`) from the top of the note.
  - Automatically filters out designated private sections such as `# Journaling` (including any subheadings and content beneath it) while preserving tasks and notes.
  - Formats checklists: converts `- [ ]` into neat unicode ballot boxes `☐` and `- [x]` into `☑`.
  - **Hide Completed Tasks Option**: Toggle in settings to hide finished checklist items (`- [x]`) completely, keeping your widget uncluttered with only active action items.
  - Cleans Obsidian wikilinks (`[[Note|Alias]]` → `Alias`, `[[Note]]` → `Note`).
  - Cleans markdown links (`[Text](url)` → `Text`).
  - Strips formatting symbols (`#`, `**`, `*`, `~~`, backticks) while preserving paragraph hierarchy and blockquotes (`│`).
- **Interactive Elements**:
  - **Refresh Button**: On-widget refresh icon that instantly re-reads the note from disk.
  - **Obsidian Deep Linking**: Tapping on the note immediately opens Obsidian via its deep link scheme (`obsidian://open?vault=...&file=...`). If the note has not been created yet, tapping prompts to create it.
- **Configuration Hub (`MainActivity`)**:
  - Select your Obsidian vault directory via the system file picker.
  - Customize the vault name (for deep linking) and daily notes subfolder.
  - Live preview test to verify path resolution and inspect sanitized vs raw markdown.
  - One-tap button to update all active widgets on the home screen.

---

## Project Structure

```
obsidian-android-daily-task-viewer/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── res/
│       │   │   ├── drawable/               # Icons & logos
│       │   │   ├── values/                 # Strings, colors, themes
│       │   │   └── xml/
│       │   │       └── daily_note_widget_info.xml
│       │   └── java/com/obsidian/dailynote/
│       │       ├── MainActivity.kt         # Setup & test preview UI
│       │       ├── data/
│       │       │   ├── VaultPreferences.kt
│       │       │   └── VaultPreferencesRepository.kt
│       │       ├── saf/
│       │       │   └── SafVaultReader.kt    # Storage Access Framework reader
│       │       ├── markdown/
│       │       │   └── MarkdownSanitizer.kt # Markdown parser & cleaner
│       │       ├── deeplink/
│       │       │   └── ObsidianDeepLinkHelper.kt
│       │       ├── widget/
│       │       │   ├── DailyNoteGlanceWidget.kt
│       │       │   ├── DailyNoteGlanceReceiver.kt
│       │       │   └── RefreshDailyNoteAction.kt
│       │       └── ui/theme/               # Jetpack Compose Theme
│       └── test/java/com/obsidian/dailynote/
│           ├── markdown/MarkdownSanitizerTest.kt
│           └── deeplink/ObsidianDeepLinkHelperTest.kt
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew / gradlew.bat
```

---

## How to Build & Run

1. Open this project in **Android Studio Hedgehog (2023.1.1) or newer** (tested with AGP 8.3.x and JDK 17).
2. Let Gradle sync and resolve dependencies.
3. Run the app on an Android device or emulator running **Android 8.0 (API 26) or higher** (Android 14 / API 34 recommended).
4. Launch the **Obsidian Daily Note** app:
   - Tap **Select Vault Folder** and pick your Obsidian vault directory in the system file picker.
   - Enter your **Vault Name** exactly as it appears in Obsidian.
   - If your daily notes are stored in a subfolder (e.g., `Daily Notes` or `Journal/Daily`), enter it in the subfolder field.
   - Tap **Test Read** to verify that today's note is found and previewed properly.
   - Tap **Save & Update Home Widget**.
5. Long press your Android Home Screen, select **Widgets**, find **Obsidian Daily Note**, and drag the widget to your home screen!
