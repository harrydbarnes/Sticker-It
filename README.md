# Sticker It 🎨

> Turn any photo into a transparent sticker — powered by ML Kit Subject Segmentation — and send it straight to GBoard.

[![Android CI](https://github.com/YOUR_USERNAME/StickerIt/actions/workflows/build.yml/badge.svg)](https://github.com/YOUR_USERNAME/StickerIt/actions)
![Min SDK](https://img.shields.io/badge/minSdk-26-green)
![Target SDK](https://img.shields.io/badge/targetSdk-35-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-purple)
![Compose](https://img.shields.io/badge/Compose-BOM%202025.01-blueviolet)

---

## Features

### Create Stickers
- **Pick any photo** from your gallery via the image picker
- **Share an image** to Sticker It directly from any other app (Photos, Chrome, WhatsApp, etc.)
- **Auto-detection** — ML Kit Subject Segmentation isolates the foreground subject automatically with a soft, feathered edge

### Brush Editor
- **Include / Exclude brush** — switch modes and paint over the image to add or remove areas from your sticker
- **Variable brush radius** — slider adjusts the stroke width in real time
- **Live preview** — toggle between edit mode (original + overlay) and preview mode (transparent background)
- **Undo** last stroke or **Reset** all edits to rerun segmentation

### Sticker Gallery
- **Infinite grid** of all your saved stickers
- **Rename** any sticker with a long-press context menu
- **Share** individual stickers via Android's share sheet
- **Delete** stickers with confirmation
- **Add all stickers to GBoard** in one tap (if GBoard is installed)

### GBoard Integration
- Stickers are served via a `ContentProvider` that GBoard reads as a sticker pack
- One-tap "Add to GBoard" button in the gallery opens GBoard's built-in sticker import flow
- Stickers saved as lossless WebP with full transparency — exactly what GBoard expects

---

## Architecture

```
app/
└── src/main/kotlin/com/stickerit/app/
    ├── data/
    │   ├── local/          Room database, DAO
    │   ├── model/          Sticker, StickerPack, UI state sealed classes
    │   ├── provider/       StickerContentProvider (GBoard), GboardHelper
    │   └── repository/     StickerRepository (single source of truth)
    ├── di/                 Hilt modules (database, app context)
    ├── domain/             ImageSegmentationHelper (ML Kit wrapper + brush engine)
    └── ui/
        ├── NavHost.kt      Compose Navigation host
        ├── components/     Reusable composables (BrushOverlay, BrushCursor)
        ├── editor/         StickerEditorScreen + StickerEditorViewModel
        ├── gallery/        StickerGalleryScreen + StickerGalleryViewModel
        ├── home/           HomeScreen
        └── theme/          Material 3 colours, typography, shapes
```

**Stack:**

| Layer | Library |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Image segmentation | ML Kit Subject Segmentation |
| Image loading | Coil |
| Database | Room |
| Async | Kotlin Coroutines + Flow |
| Storage | Internal storage (WebP files) |

---

## Getting Started

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator running Android 8.0+ (API 26)
- GBoard installed on the test device for full GBoard integration testing

### Clone and build

```bash
git clone https://github.com/YOUR_USERNAME/StickerIt.git
cd StickerIt

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## How It Works

### Segmentation Pipeline

1. User picks or shares an image
2. `StickerEditorViewModel.loadAndSegment()` passes the bitmap to `ImageSegmentationHelper`
3. ML Kit's `SubjectSegmenter` returns a per-pixel confidence float array (0..1)
4. A preview sticker bitmap is built: pixels with confidence >= 0.5 are kept; those on the boundary (0.5..0.6) get a feathered alpha for a smooth edge
5. The raw confidence mask is kept in memory for brush editing

### Brush Engine

- Each finger drag fires normalised (0..1) canvas coordinates into the ViewModel
- `BrushStroke` objects accumulate in a list; each stroke is either `INCLUDE` (set mask → 1) or `EXCLUDE` (set mask → 0)
- The mask is re-applied on every drag event so the preview updates in real time
- Undo pops the last committed stroke and replays the rest

### GBoard Integration

Stickers are exposed via `StickerContentProvider`:

```
content://com.stickerit.app.stickercontentprovider/sticker_pack        → pack list
content://com.stickerit.app.stickercontentprovider/sticker_pack/{id}   → pack detail (sticker filenames)
content://com.stickerit.app.stickercontentprovider/sticker_asset/{pack}/{file} → sticker bitmap
```

Tapping **Add to GBoard** fires:

```kotlin
Intent("com.google.android.inputmethod.latin.ADD_STICKER_PACK")
    .putExtra("sticker_pack_id", "stickerit_pack")
    .putExtra("sticker_pack_authority", "com.stickerit.app.stickercontentprovider")
```

GBoard reads the ContentProvider and imports all stickers into its built-in sticker tray.

---

## Sticker Format

| Property | Value |
|---|---|
| Format | WebP (lossless) |
| Output size | 512 × 512 px |
| Transparency | Full ARGB_8888 |
| Max file size | ~200 KB typical |

WebP lossless was chosen because GBoard's sticker tray renders WebP natively and it offers smaller file sizes than PNG with identical quality.

---

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` (API 33+) | Pick photos from the gallery |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | Same, for older Android versions |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28) | Not required on modern Android; included for compatibility |
| `VIBRATE` | Haptic feedback on long-press in the gallery |

No internet permission is requested. All processing is fully on-device.

---

## Roadmap

- [ ] Emoji tagging for each sticker (for better GBoard search)
- [ ] Multiple sticker packs (organise by theme)
- [ ] Background replacement (solid colour, gradient, or custom image)
- [ ] Sticker text overlays (add emoji or short text on top)
- [ ] Batch import (create stickers from multiple images at once)
- [ ] Widget for quick sticker access from the home screen
- [ ] Export as animated WebP (support for animated stickers)

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes with clear messages
4. Open a pull request against `develop`

Please follow the existing code style (Kotlin official) and ensure `./gradlew lint test` passes before opening a PR.

---

## Licence

```
Copyright 2025 Sticker It

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
