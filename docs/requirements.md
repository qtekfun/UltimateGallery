# UltimateGallery — Requirements

Package: `com.qtekfun.ultimategallery` · Kotlin + Jetpack Compose · Material 3 Expressive with dynamic color · minSdk 31 · code, repo and docs in English · UI in Spanish and English.

Part of the Ultimate family (UltimateDeck, UltimateTasks, UltimateCalendar, UltimateKeys). Built from scratch. Functionally inspired by EasyWatermark (Apache-2.0); no code or design copied. Credit it in the About screen.

## 1. Vision

A refined, folder-based gallery whose flagship feature is **batch watermarking**: pick many photos, place one watermark with your fingers (position, size, rotation), preview it on every photo, export them all. Main use case: product photos for Wallapop. The gallery grows around that flow.

Priority: watermark flow first, then the rest of the gallery.

## 2. Functional requirements

### 2.1 Media access
- R1. Uses the system media store (MediaStore) with the media permission (images and videos). Handles partial access ("selected photos only") on Android 14+ gracefully, with a clear prompt to grant full access.
- R2. Live updates when media changes on the device (ContentObserver).
- R3. No Google Play Services, no network permission, no analytics.

### 2.2 Gallery (folders)
- R4. Home screen lists folders (MediaStore buckets) with cover thumbnail, name and item count.
- R5. Home view toggles between **grid of folder cards** and **compact list**. The choice is remembered.
- R6. In grid views, **pinch to zoom changes the grid size** (continuous, iOS Photos style, snapping to column counts). Applies to the folder grid and to the photo grid inside a folder. The list view has no pinch.
- R7. Folder screen shows photos and videos in a grid, newest first, with date headers.
- R8. Multi-selection: long press to start, drag to select ranges, select-all. Selection action bar: **Watermark** (primary), Share, Move, Copy, Delete, Info; Rename when one item is selected.

### 2.3 Hidden folders (app-only)
- R9. Hiding a folder only removes it from this app's views. Nothing is changed on disk, no `.nomedia`.
- R10. Long press a folder → **Hide**. A snackbar offers Undo.
- R11. Settings → **Folders**: complete list of all folders with thumbnail, name, path, count and a switch hidden/visible.
- R12. Settings → **App folders**: list of detected app-generated folders (WhatsApp, Telegram, Screenshots, etc.) where the user chooses which to hide automatically. WhatsApp is suggested. On first run, offer this list once with WhatsApp pre-checked; nothing is hidden without confirmation.
- R13. Hidden folders never appear in the watermark picker either, unless the user opens the folder list explicitly.

### 2.4 Viewer
- R14. Full-screen viewer with pinch and double-tap zoom, pan with inertia and bounds.
- R15. Swipe down to close with a **shared-element transition** back to the grid thumbnail.
- R16. Bottom **thumbnail strip** for quick navigation within the folder.
- R17. Video playback (Media3) with simple controls; videos are viewable but not watermarked in v1.
- R18. Info panel: date, size, resolution, camera data (EXIF), location coordinates if present.
- R19. Actions in the viewer: watermark, edit, share, move, copy, rename, delete, info.

### 2.5 Watermark editor (flagship)
- R20. Entry: from the selection bar or from a dedicated "Watermark" entry on the home screen (opens the picker).
- R21. Watermark types: **image/logo (PNG with transparency, imported once)**, **text** (font, weight, color, outline, shadow, background pill) and **tiled pattern** (repeated mark with angle, spacing, offset).
- R22. Placement by touch on a large canvas showing the current photo: one finger drags; two fingers scale and rotate simultaneously. Handles for precise resize/rotate are also available.
- R23. Opacity slider. Optional margin controls.
- R24. **Snapping guides** to center, edges and configurable margins, with haptic tick when snapping. Can be disabled.
- R25. **Placement is stored in percentages and independently per orientation.** Two buckets: portrait and landscape (square counts as landscape). Position is stored as fractions of the image width and height; size as a fraction of image width; rotation in degrees. Example: a mark at 40% of the horizontal dimension stays at 40% on every photo of that orientation, whatever its resolution.
- R26. Editing the placement on any photo of an orientation updates that orientation's placement for the whole batch. No per-photo overrides in v1.
- R27. **Thumbnail strip** below the canvas shows every photo of the batch with the watermark applied live. Tap to switch the canvas photo.
- R28. Undo/redo for editor changes.
- R29. The editor shows the final result faithfully: preview and export use the same rendering code path.

### 2.6 Profiles (templates)
- R30. Save named profiles (e.g. "Wallapop") containing: watermark definition, both orientation placements, opacity and export settings.
- R31. Choose, rename, duplicate and delete profiles. Last used profile is preselected. First-run default profile named "Wallapop".

### 2.7 Export
- R32. Originals are never modified. Output goes to a configurable folder in the gallery (default `Pictures/Wallapop`, taken from the profile), visible immediately in the gallery.
- R33. Format JPEG / WebP / PNG, quality slider, optional resize (max long edge), filename pattern (original name + suffix), EXIF handling: keep, or strip location (default), or strip all. Orientation is always applied correctly.
- R34. Batch export with progress (per-photo and overall), cancel, and a result summary with "Open folder" and "Share all". Runs reliably in the background (WorkManager/foreground service) for large batches.
- R35. Handles photos of different sizes and orientations, large images (memory-safe decoding and tiled/downscaled rendering), HEIC/AVIF input where the device supports it.

### 2.8 File operations
- R36. Move, copy, rename and delete using MediaStore write/trash requests (system consent dialogs). Delete uses the system trash (recoverable), not a custom trash.
- R37. Share via the system share sheet (multiple items).
- R38. Info/EXIF view with an option to share or save a copy without EXIF.

### 2.9 Basic editing
- R39. Crop and rotate: free and fixed ratios (1:1, 4:3, 3:4, 16:9), 90° rotate buttons, flip, and a fine straighten dial.
- R40. **Polished, animated rotation** (spring animation of the image and crop frame when rotating).
- R41. **Save behavior is a setting**: always overwrite, always save as copy, or ask every time. Default: ask. Overwrite uses system consent. 90° rotations of JPEG use lossless orientation where possible.

### 2.10 Settings
- Appearance: theme (system/light/dark), dynamic color, default grid size.
- Folders and App folders (R11, R12).
- Edit: save behavior (R41).
- Export defaults.
- About: version, licenses, EasyWatermark credit.

## 3. Non-functional requirements
- N1. Smooth 60/120 fps scrolling with tens of thousands of items. Thumbnails cached; paging from MediaStore.
- N2. Refined UI: Material 3 Expressive, dynamic color, motion with purpose, haptics, edge-to-edge, predictive back, tablet/foldable-aware layouts.
- N3. Accessibility: TalkBack labels, touch targets ≥ 48dp, large font support.
- N4. Privacy: fully offline, no network permission, no trackers, F-Droid compatible dependencies only (FOSS).
- N5. Tests: unit tests for placement math, orientation buckets and export naming; instrumented/Robolectric tests for the render pipeline; screenshot/UI tests for key screens where feasible.
- N6. Localization: English and Spanish from day 1; all strings in resources.

## 4. Out of scope for v1
Camera capture, brightness/contrast/filters, blur or cover-up regions, video watermarking, cloud sync, face or object search, custom trash, virtual albums, per-photo watermark overrides.

## 5. Roadmap (after v1)
Brightness/contrast/saturation, blur/cover regions (plates, faces), integrated camera with live watermark, video watermarking, search.

## 6. Assumptions to confirm during development
- Pinch-to-resize applies to both folder grid and photo grid.
- Square photos use the landscape bucket.
- Export defaults to stripping location but keeping other EXIF.
