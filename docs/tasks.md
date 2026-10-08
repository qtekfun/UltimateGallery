# UltimateGallery — Tasks

Execute all phases in order without pausing for review between them. Commit per task with clear messages. Keep CI green. Update this file ticking tasks as they finish.

## Phase 0 — Project setup
- [x] Create Android project `com.qtekfun.ultimategallery` (Kotlin, Compose, Material 3, minSdk 31), version catalog, R8, lint, ktlint/detekt.
- [x] Theme: dynamic color, light/dark, Material 3 Expressive typography/shapes/motion tokens.
- [x] Hilt, Navigation Compose, Room, DataStore, Coil 3, Media3, WorkManager wired in.
- [x] GitHub Actions: build, lint, test on PRs; signed release on tags.
- [x] README, LICENSE (confirm choice), CONTRIBUTING, strings in `en` and `es`.

## Phase 1 — Media access
- [x] Permission flow (full and partial access on Android 14+), onboarding screen explaining why.
- [x] MediaStore repository: folders by bucket, paged items per folder, ContentObserver refresh.
- [x] Unit/instrumented tests with fake MediaStore data.

## Phase 2 — Minimal gallery for the watermark flow
- [x] Home with folder grid and list toggle (persisted).
- [x] Folder screen with photo grid and date headers.
- [x] Multi-selection with drag-select, selection bar with primary "Watermark" action.

## Phase 3 — Watermark editor
- [x] Placement model and percentage-based math with orientation buckets; unit tests.
- [x] Shared `WatermarkRenderer` (image, text, tiled) used by preview and export.
- [x] Editor canvas: drag, two-finger scale+rotate, handles, opacity, snapping guides and haptics.
- [x] Image watermark import (PNG with transparency), text styling UI, tiled pattern UI.
- [x] Batch thumbnail strip with live watermark; tap to switch the canvas photo.
- [x] Undo/redo.

## Phase 4 — Export
- [x] Export settings UI: destination folder, format, quality, resize, filename pattern, EXIF mode.
- [x] Export pipeline (decode, render, encode, write via MediaStore with IS_PENDING, EXIF handling).
- [x] WorkManager foreground batch, progress, cancel, result summary (open folder, share all).
- [x] Tests: pixel checks of rendered output, orientation handling, big images, mixed batches.

## Phase 5 — Profiles
- [ ] Room entities and DAO for profiles; default "Wallapop" profile.
- [ ] Profile picker: save, rename, duplicate, delete, last-used preselected.

## Phase 6 — Viewer
- [ ] Pager with pinch/double-tap zoom and pan.
- [ ] Shared-element transition from grid, swipe-down to dismiss.
- [ ] Thumbnail strip, video playback (Media3), info/EXIF panel.
- [ ] Viewer actions bar.

## Phase 7 — Gallery completion
- [ ] Pinch-to-zoom grid resize (folder grid and photo grid), snapping to column counts, persisted.
- [ ] Hidden folders: long-press Hide with undo, Settings → Folders list with switches.
- [ ] App folders catalog and Settings → App folders; first-run suggestion sheet (WhatsApp pre-checked).
- [ ] File operations: share, move, copy, rename, delete (system trash) with consent flows.

## Phase 8 — Crop and rotate
- [ ] Crop UI with aspect ratios, 90° rotate, flip, straighten dial.
- [ ] Animated spring rotation and flip.
- [ ] Save behavior setting (overwrite / copy / ask) and implementation, including lossless JPEG 90° rotation via EXIF.

## Phase 9 — Settings and polish
- [ ] Settings screens: appearance, folders, app folders, edit, export defaults, about (EasyWatermark credit, licenses).
- [ ] Motion/haptics pass, predictive back, edge-to-edge, tablet/foldable layouts, accessibility pass.
- [ ] Performance pass with large libraries (macrobenchmark or manual profiling).

## Phase 10 — Release
- [ ] Adaptive + monochrome launcher icon.
- [ ] F-Droid metadata (`fastlane/`), reproducible build check, no proprietary dependencies.
- [ ] Screenshots and store listing text in English and Spanish.
- [ ] Tag 0.1.0 pre-release on GitHub; 1.0 after on-device testing; F-Droid submission after 1.0.
