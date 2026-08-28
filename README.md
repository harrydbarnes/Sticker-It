# Sticker It 🎨

> Turn any photo into a transparent sticker — powered by on-device subject segmentation — then keep it in a private library or add a chosen pack to WhatsApp.

[![Android CI](https://github.com/harrydbarnes/Sticker-It/actions/workflows/build.yml/badge.svg)](https://github.com/harrydbarnes/Sticker-It/actions)
![Min SDK](https://img.shields.io/badge/minSdk-26-green)
![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple)
![Compose](https://img.shields.io/badge/Compose-BOM%202026.08-blueviolet)

---

## Features

### Create Stickers
- **Pick any photo** from your gallery via the image picker
- **Share an image** to Sticker It directly from any other app (Photos, Chrome, WhatsApp, etc.)
- **Auto-detection** — ML Kit Subject Segmentation isolates the foreground subject automatically with a soft, feathered edge; the app checks and prefetches its Google Play services model at startup on supported Android versions, while Android 16+ uses the bundled MediaPipe DeepLab V3 CPU path

### Brush Editor
- **Include / Exclude brush** — switch modes and paint over the image to add or remove areas from your sticker; close a loop to fill the area inside it
- **Variable brush radius** — slider adjusts the stroke width in real time
- **Live preview** — edit against the original image with a temporary brush guide, then toggle to the generated sticker on a transparent background
- **Undo / Redo** brush edits or **Reset** all edits to rerun segmentation
- **Re-editable stickers** — source pixels and the current mask are kept privately so a saved sticker can be refined later from the gallery
- **Finishing studio** — add an outline, transparent/solid/gradient/image background, reposition or resize the cut-out, and add short text or emoji before saving

### Sticker Gallery
- **Infinite grid** of all your saved stickers
- **Rename** any sticker with a long-press context menu
- **Share** individual stickers via Android's share sheet
- **Delete** stickers with confirmation
- **Edit** any saved sticker again without losing its existing selection
- **Select 3–30 stickers** and add or update a named WhatsApp sticker pack
- **Manage multiple packs** with independent names, tray images, sticker ordering, emoji keywords, and accessibility descriptions
- **Batch creation** — select multiple photos, watch each result process independently, retry failures, and fine-tune results later
- **Library backup** — export stickers, editable source/mask data, finishing recipes, and pack definitions to a portable archive, then restore them safely after a reinstall

### WhatsApp Pack Integration
- Select the stickers that belong in a named pack; the app exposes only that pack's assets through WhatsApp's documented `ContentProvider` contract
- Create, rename, or delete packs from the gallery, choose a pack-specific tray image, reorder its stickers, and edit the emoji/accessibility metadata WhatsApp receives
- One-tap **WhatsApp** opens WhatsApp's confirmation screen; selecting a different set later updates the chosen pack with a new image-data version
- Static stickers are encoded as 512 × 512 WebP and capped at WhatsApp's 100 KB limit

---

## Architecture

```
app/
└── src/main/kotlin/com/stickerit/app/
    ├── data/
    │   ├── backup/         Versioned library archive format and recovery repository
    │   ├── local/          Room database, DAO
    │   ├── model/          Sticker, StickerPack, UI state sealed classes
    │   ├── provider/       StickerContentProvider (WhatsApp), WhatsAppHelper
    │   └── repository/     StickerRepository, StickerPackRepository
    ├── di/                 Hilt modules (database, app context)
    ├── domain/             ImageSegmentationHelper + SegmentationModelManager (ML Kit/MediaPipe + brush engine)
    └── ui/
        ├── NavHost.kt      Compose Navigation host
        ├── components/     Reusable composables (BrushOverlay, BrushCursor)
        ├── batch/          Batch image processing queue
        ├── editor/         StickerEditorScreen, FinishStudio, and editor ViewModels
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
| Image segmentation | ML Kit Subject Segmentation + bundled MediaPipe fallback |
| Image loading | Coil |
| Database | Room + Room Gradle Plugin (reviewable migration schemas) |
| Async | Kotlin Coroutines + Flow |
| Settings | Preferences DataStore (migrated from the original editor SharedPreferences) |
| Storage | Internal storage (WebP files) + user-selected backup ZIP |

---

## Getting Started

### Requirements

- Android Studio with JDK 17 support
- JDK 17
- Android device or emulator running Android 8.0+ (API 26)
- WhatsApp installed on the test device for sticker-pack integration testing

### Clone and build

```bash
git clone https://github.com/harrydbarnes/Sticker-It.git
cd Sticker-It

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Analyze release R8 configuration (AGP 9.3+)
./gradlew :app:analyzeReleaseR8Config

# Run Compose UI tests on a connected device or emulator
./gradlew connectedDebugAndroidTest
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

---

## How It Works

### Segmentation Pipeline

1. User picks or shares an image
2. `StickerEditorViewModel.loadAndSegment()` passes the bitmap to `ImageSegmentationHelper`
3. On supported Android versions, `SegmentationModelManager` checks the unbundled ML Kit module with `ModuleInstallClient` and requests a background prefetch when needed; `SubjectSegmenter` then returns a per-pixel confidence float array (0..1). Android 16+ uses the bundled MediaPipe DeepLab V3 category mask through the CPU delegate
4. A preview sticker bitmap is built: pixels with confidence >= 0.5 are kept; those on the boundary (0.5..0.6) get a feathered alpha for a smooth edge
5. The raw confidence mask is kept in memory for brush editing
6. The finishing studio applies a reusable recipe to the cut-out and shows the final 512px composition live
7. Saving stores the flattened WebP, private source/mask data, and finishing recipe so the sticker can be reopened and refined later

### Brush Engine

- Each finger drag fires normalised (0..1) canvas coordinates into the ViewModel
- `BrushStroke` objects accumulate in a list; each stroke is either `INCLUDE` (set mask → 1) or `EXCLUDE` (set mask → 0)
- Drag events are conflated to one preview per frame; the completed stroke is then rendered precisely
- Undo and redo replay the committed stroke history

### WhatsApp Pack Integration

Named pack records and ordered sticker membership are stored in Room. On upgrade, the old `stickerit_library` JSON manifest is imported into the default pack once, preserving the previous selection where possible. The selected pack is then exposed via `StickerContentProvider`:

```
content://com.stickerit.app.stickercontentprovider/metadata                              → all named pack metadata
content://com.stickerit.app.stickercontentprovider/metadata/{pack}                       → one pack's metadata
content://com.stickerit.app.stickercontentprovider/stickers/{pack}                       → ordered stickers and per-sticker metadata
content://com.stickerit.app.stickercontentprovider/stickers_asset/{pack}/{file}          → WebP/PNG asset
```

Tapping **WhatsApp** fires the documented intent:

```kotlin
Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
    .putExtra("sticker_pack_id", selectedPackId)
    .putExtra("sticker_pack_authority", "com.stickerit.app.stickercontentprovider")
```

WhatsApp displays its own confirmation sheet. The user must confirm every add/update; the app cannot silently write into WhatsApp.

### Library Backup and Recovery

Settings provides **Export library** and **Import library** actions using Android's system file picker. The export is a versioned ZIP containing sticker metadata, final WebP assets, private source/mask files, finishing recipes, named pack definitions, pack membership, and custom tray images. Private absolute filesystem paths are removed from the archive and rebuilt when it is imported.

Import is an additive merge: matching final sticker assets and non-empty pack IDs are skipped, existing sticker records are never replaced or deleted, and the migration-created empty default pack can be populated after a reinstall. A failed restore rolls back the records and files created during that attempt. Archive paths and sizes are validated before anything is added to the library.

### Why not Gboard?

There is no public, supported Android API that lets a third-party app add arbitrary image stickers to Gboard's custom-sticker tray. Earlier community integrations stopped working when Gboard removed that facility. Pixel Studio's current Pixel-only Gboard experience is a Google-owned integration, not an API available to third-party apps. Sticker It therefore keeps the user-owned library and uses WhatsApp's documented pack protocol instead.

---

## Sticker Format

| Property | Value |
|---|---|
| Format | WebP (lossy, alpha preserved) |
| Output size | 512 × 512 px |
| Transparency | Full ARGB_8888 alpha preserved (solid/image backgrounds can be opaque) |
| Max file size | 100 KB (WhatsApp static-sticker limit) |

The encoder lowers WebP quality only as needed to keep photo cut-outs below WhatsApp's static-sticker size limit.

---

## Permissions

| Permission | Why |
|---|---|
| `VIBRATE` | Haptic feedback on long-press in the gallery |

No photo-storage or internet permission is requested. Android's system Photo Picker grants access only to the image the user chooses; image processing is fully on-device, with the Android 16+ fallback model bundled in the app.

---

## Roadmap

- [x] Emoji tagging and accessibility labels for each sticker (for WhatsApp search and screen readers)
- [x] Re-select a library set to update the WhatsApp pack in place
- [x] Re-edit saved stickers with their private source image and selection mask
- [x] Multiple named WhatsApp packs (organise by theme)
- [x] Finishing studio with outline, background replacement, positioning, and text/emoji overlays
- [x] Batch import (create stickers from multiple images at once)
- [x] Library export/import and recovery
- [x] Edge-to-edge and accessibility coverage for the core screens
- [ ] Widget for quick sticker access from the home screen
- [ ] Export as animated WebP (support for animated stickers)

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes with clear messages
4. Open a pull request against `main`

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
