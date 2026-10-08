# Changelog

All notable changes to UltimateGallery. Versions follow [SemVer](https://semver.org/); the
same text is kept under `fastlane/metadata/android/*/changelogs` for F-Droid.

## [0.2.0]

Watermark editor and export, end to end (pre-release).

- Watermark editor: text, logo (PNG with transparency) and tiled marks; text font, weight, color, outline, shadow and background pill.
- Place the mark with one finger; two fingers scale and rotate; a corner handle for precise resize and rotate; opacity and margin.
- Snapping guides to center, edges and margins, with haptic ticks and 45-degree rotation detents.
- Placement is stored as fractions, independently for portrait and landscape photos, so every photo of an orientation gets the same relative result.
- Batch strip showing every photo with the watermark applied live; undo and redo.
- Export: JPEG, WebP or PNG, quality, maximum size, file name pattern, EXIF handling (remove location by default).
- Background export with progress and cancel, then a summary with Open folder and Share all. Originals are never modified.

## [0.1.0]

First usable gallery (pre-release).

- Folder-based home with a grid of folder cards or a compact list (the choice is remembered).
- Folder screen with a photo and video grid, newest first, with date headers and a floating date chip.
- Multi-selection: long press, drag to select ranges (with edge auto-scroll), select all.
- Selection bar with a primary Watermark action and Share.
- Watermark picker: choose photos across folders (the editor itself arrives in a later version).
- Permission onboarding, including "selected photos only" on Android 14+ with a prompt to allow all.
- Live updates when the device's media changes.
- English and Spanish; Material 3 Expressive with dynamic color. Fully offline.
