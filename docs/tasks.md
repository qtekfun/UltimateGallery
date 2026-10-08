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
- [x] Room entities and DAO for profiles; default profile.
- [x] Profile picker: save, rename, duplicate, delete, last-used preselected.

## Phase 6 — Viewer
- [x] Pager with pinch/double-tap zoom and pan.
- [x] Shared-element transition from grid, swipe-down to dismiss.
- [x] Thumbnail strip, video playback (Media3), info/EXIF panel.
- [x] Viewer actions bar.

## Phase 7 — Gallery completion
- [x] Pinch-to-zoom grid resize (folder grid and photo grid), snapping to column counts, persisted.
- [x] Hidden folders: long-press Hide with undo, Settings → Folders list with switches.
- [x] App folders catalog and Settings → App folders; first-run suggestion sheet (WhatsApp pre-checked).
- [ ] File operations: share, move, copy, rename, delete (system trash) with consent flows.

## Phase 8 — Crop and rotate
- [x] Crop UI with aspect ratios, 90° rotate, flip, straighten dial.
- [x] Animated spring rotation and flip.
- [x] Save behavior setting (overwrite / copy / ask) and implementation, including lossless JPEG 90° rotation via EXIF.

## Phase 9 — Settings and polish
- [x] Settings screens: appearance, folders, app folders, edit, export defaults, about (EasyWatermark credit, licenses).
- [x] Motion/haptics pass, predictive back, edge-to-edge, tablet/foldable layouts, accessibility pass.
- [x] Performance pass with large libraries (macrobenchmark or manual profiling).

## Phase 10 — Release
- [x] Adaptive + monochrome launcher icon.
- [x] F-Droid metadata (`fastlane/`), reproducible build check, no proprietary dependencies.
- [ ] Screenshots and store listing text in English and Spanish. (Listing text is done; screenshots must be taken on a device and added under `fastlane/metadata/android/<locale>/images/phoneScreenshots/`.)
- [x] Tag pre-releases on GitHub (0.1.0 to 0.9.0 and 1.0.0-rc1); 1.0 after on-device testing; F-Droid submission after 1.0.
