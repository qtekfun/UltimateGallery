# UltimateGallery — Design

## 1. Stack
- Kotlin, Jetpack Compose, Material 3 (Expressive APIs, dynamic color), minSdk 31, targetSdk latest stable.
- Navigation: Navigation Compose with `SharedTransitionLayout` for grid → viewer.
- DI: Hilt. Async: coroutines + Flow.
- Data: MediaStore (media), Room (profiles, hidden folders, settings that need queries), DataStore (simple preferences).
- Images: Coil 3 for thumbnails and previews. Video: Media3 ExoPlayer.
- Background work: WorkManager (export batches) with foreground notification.
- Build: Gradle version catalog, KSP, R8 enabled, reproducible-build friendly (F-Droid). No proprietary dependencies, no Play Services.
- Tests: JUnit, Turbine, Robolectric, Compose UI tests.

## 2. Module layout
Single Gradle module `app`, clean package boundaries (split into modules later only if build time requires it):

```
com.qtekfun.ultimategallery
├─ core/        design system, theme, motion, haptics, utils
├─ data/
│  ├─ media/    MediaStore repository, paging, observers, file ops
│  ├─ db/       Room entities and DAOs
│  └─ prefs/    DataStore
├─ domain/      models (Folder, MediaItem, WatermarkProfile, Placement), use cases
├─ feature/
│  ├─ gallery/  folders home, folder grid, selection
│  ├─ viewer/   pager, zoom, strip, video, info
│  ├─ watermark/ editor canvas, strip, profiles, export UI
│  ├─ edit/     crop and rotate
│  └─ settings/
└─ render/      shared watermark renderer + export pipeline
```

## 3. Key models

```kotlin
enum class Orientation { PORTRAIT, LANDSCAPE } // square -> LANDSCAPE

data class Placement(
    val centerX: Float,   // 0..1 of image width
    val centerY: Float,   // 0..1 of image height
    val sizeFraction: Float, // watermark width as fraction of image width
    val rotationDeg: Float,
)

sealed interface WatermarkSource {
    data class Image(val uri: Uri) : WatermarkSource
    data class Text(val text: String, val style: TextStyleSpec) : WatermarkSource
    data class Tiled(val base: WatermarkSource, val angleDeg: Float, val spacing: Float, val offset: Offset) : WatermarkSource
}

data class WatermarkProfile(
    val id: Long, val name: String,
    val source: WatermarkSource, val opacity: Float,
    val portrait: Placement, val landscape: Placement,
    val export: ExportSettings,
)
```

Tiled marks are defined by pattern parameters relative to image width, so they scale with resolution.

## 4. Rendering (single code path)
`WatermarkRenderer.draw(canvas, imageSize, profile, orientation)` is used by:
- the live editor canvas (Compose `drawIntoCanvas`),
- the batch thumbnail strip (downscaled bitmaps),
- the export pipeline (full resolution `Bitmap`/`Canvas`).

All geometry is derived from fractions × image size, so preview and export match exactly regardless of resolution. Rotation and scale pivot around the watermark center.

## 5. Gesture handling (editor)
- One pointer: translate. Two pointers: simultaneous scale + rotate via `detectTransformGestures`, applied to the active orientation's `Placement`.
- Convert between screen px and image fractions with the canvas-to-image matrix (accounts for letterboxing).
- Snapping: compare center and edges against targets (center, edge margins, configured margin); snap within a threshold, show guide lines, play a haptic tick once per snap entry.
- Handles for precise scale/rotate are optional overlays on the selected mark.
- Undo/redo via a bounded stack of `Placement`/opacity/source snapshots.

## 6. Export pipeline
1. For each selected item: decode with `ImageDecoder` (honoring EXIF orientation, downsampling if resize is set; avoid OOM using sampled/tiled decoding for very large images).
2. Determine orientation from the displayed (post-EXIF) size; pick that bucket's `Placement`.
3. Draw watermark with the shared renderer.
4. Encode to the chosen format/quality; write via `MediaStore.Images` insert with `RELATIVE_PATH` to the destination folder, using `IS_PENDING` while writing.
5. Copy EXIF according to setting (keep / strip location / strip all) using `ExifInterface`.
6. Report progress via `WorkManager` progress data; final summary with results and failures.

A `CoroutineWorker` runs the batch as a foreground service with a progress notification and cancel action. Items run sequentially (memory) with a small parallelism option for small images.

## 7. Media layer
- Folders: query `MediaStore.Files` for images and videos grouped by `BUCKET_ID`; cover = newest item; counts cached and refreshed on `ContentObserver` events.
- Items: paged query per bucket ordered by `DATE_TAKEN` desc (fallback `DATE_ADDED`). Date headers computed in the UI layer.
- File operations: `createWriteRequest` (overwrite, move), `createTrashRequest` (delete), `createDeleteRequest` (permanent delete only if the user explicitly asks), `RELATIVE_PATH` updates for move, insert+stream copy for copy, `DISPLAY_NAME` update for rename. Each request surfaces the system consent dialog and handles the result.

## 8. Hidden folders
- Room table `hidden_folder(bucketId PK, name, hiddenAt)`.
- Combined into the folder flow: `folders = allFolders.filter { it.bucketId !in hidden }`.
- App folder catalog: a local, editable list of known app folder names/paths (WhatsApp Images/Video/Animated GIFs/Stickers, Telegram, Screenshots, Instagram, etc.), matched against real buckets. The settings screen shows only the ones detected on the device.
- First-run sheet offers detected app folders, WhatsApp pre-checked, nothing applied without confirmation.

## 9. Gallery UI details
- Home: toggle grid of folder cards ↔ list. Grid is `LazyVerticalGrid` with a pinch-driven column count: track pinch scale, animate item size continuously, snap to the nearest column count on release, persist per grid type.
- Folder grid: same pinch behavior, sticky date headers, drag-to-select with auto-scroll.
- Selection bar: animated contextual top bar + bottom actions. The Watermark action is the prominent button.
- Viewer: `HorizontalPager` + transformable zoom layer, shared element from grid, drag-down-to-dismiss with scale/alpha, bottom thumbnail strip, Media3 for videos.

## 10. Crop and rotate
- Custom composable with draggable crop frame, aspect ratio chips, rotate 90° button and straighten dial.
- Rotation animates with a spring on the image and frame together (scale to fit during the turn). Flip animates on the axis.
- Save: on confirm, apply per the save-behavior setting (overwrite / copy / ask). For JPEG with pure 90° steps and no crop, write the EXIF orientation flag instead of re-encoding when possible.

## 11. Motion and polish
- Material 3 Expressive shapes and motion springs, subtle haptics (snap, selection, rotation detent).
- Predictive back, edge-to-edge, per-screen insets handling, foldable/tablet two-pane layouts where useful.
- Custom launcher icon (adaptive + monochrome themed icon).

## 12. Release and CI
- GitHub Actions: build, lint, unit tests on PRs; signed APK/AAB attached to GitHub Releases on version tags (no release-please; manual tagging with a changelog).
- F-Droid ready from day 1: `fastlane/metadata/android/{en-US,es-ES}` with descriptions, changelogs, screenshots; reproducible builds; all dependencies FOSS. F-Droid submission after 1.0.
- License: decide with the owner before first release (default proposal: GPL-3.0 or Apache-2.0; confirm).
- At the end of the plan: icon, screenshots and store listing material.
