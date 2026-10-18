# Changelog

All notable changes to UltimateGallery. Versions follow [SemVer](https://semver.org/); the
same text is kept under `fastlane/metadata/android/*/changelogs` for F-Droid.

## [1.0.0]

First stable release.

- Folder-based gallery with batch watermarking: place one watermark with your fingers, preview it on every photo and export them all. Originals are never modified.
- Viewer, crop and rotate, wallpaper, hidden folders, file operations and settings backup, as in the release candidates.
- Fixed: long-press multi-select picked the photo in the row below the finger.

## [1.0.0-rc11]

Settings backup (pre-release).

- Settings → Backup exports your settings and watermark profiles to a JSON file and imports them back, for a new phone or a reinstall.
- Photos and folders are never included; image watermarks are exported as text.
- Importing replaces your settings and only adds profiles: existing ones are never changed or deleted.

## [1.0.0-rc10]

Wallpaper and device-wide hiding (pre-release).

- Set a photo as the home and/or lock screen wallpaper from the viewer: frame it with your fingers in a screen-shaped frame, with an optional lock-screen clock preview. Only the visible part is decoded, at screen size.
- "Use as…" hands a photo to other apps (wallpaper pickers, contact photos…).
- Hide a folder on the whole device with a `.nomedia` file, after a confirmation. All files access is asked for only then; Settings → Folders lists those folders and can show them again. Hiding inside the app stays the default.

## [1.0.0-rc9]

Dependency updates (pre-release).

- Dependabot watches Gradle and GitHub Actions weekly.
- Gradle 9.8.1, Kotlin Compose plugin 2.4.21.

## [1.0.0-rc8]

Old default profile renamed (pre-release).

- Profiles stored by early builds get the neutral default name, text and folder.
- Your own profiles are left untouched.

## [1.0.0-rc7]

Fixes from phone testing (pre-release).

- The video rotation preview fills the screen.
- The viewer refreshes after overwriting a video.
- Folders without a name get a proper one.

## [1.0.0-rc6]

Neutral defaults (pre-release).

- The default watermark text is the app name.
- The default profile is called "Default" and exports to `Pictures/UltimateGallery`.
- Docs and store listing are service-neutral.

## [1.0.0-rc5]

Font picker (pre-release).

- Pick fonts from a list where each one is shown in its own typeface.
- Six free fonts included; import your own `.ttf`/`.otf`.
- A single Italic button replaces the old font chips.

## [1.0.0-rc4]

Rotate videos (pre-release).

- Rotate button for videos with an animated preview.
- Lossless: only the rotation flag changes (MP4/MOV/3GP).
- Overwrite the original or save a copy.

## [1.0.0-rc3]

Signed with the release key (pre-release).

- Uninstall earlier builds once; from now on updates install over each other.
- Italic works on every font.

## [1.0.0-rc2]

Fixes (pre-release).

- Crop and rotate: the photo and the crop frame now match.
- Watermark text styles work: weight, fonts and a live sample.
- Viewer: the info sheet no longer drops to half height.
- Haptic feedback is off by default (Settings → Appearance).

## [1.0.0-rc1]

First release candidate (pre-release).

- Feature-complete gallery with batch watermarking.
- Themed launcher icon and store listing.
- Reproducible build, fully offline.
- Awaiting on-device testing before 1.0.

## [0.9.0]

Settings and polish (pre-release).

- Settings: appearance, folders, editing, export defaults, about.
- Theme and dynamic color options.
- Two-pane editor and more grid columns on tablets.
- Haptics and larger touch targets.

## [0.6.0]

Crop and rotate (pre-release).

- Crop with free and fixed aspect ratios, rotate 90°, flip and a straighten dial.
- Spring-animated rotation and flip.
- Save behavior: ask, save a copy or overwrite.
- Lossless JPEG rotation through the EXIF orientation.

## [0.5.0]

Gallery completion (pre-release).

- Pinch to resize the folder and photo grids.
- Hide folders in the app (with undo) and an app folders list.
- Move, copy, rename, delete (system trash) and share.
- Share or save a copy without EXIF.

## [0.4.0]

Viewer (pre-release).

- Full-screen viewer: swipe between photos and videos, pinch and double-tap zoom, pan with inertia and bounds.
- Shared-element transition from the grid and swipe down to close.
- Thumbnail strip for quick navigation within the folder.
- Video playback with simple controls (videos are viewable but not watermarked).
- Info panel with date, size, resolution, camera data (EXIF) and location when present.
- Viewer actions: Watermark, Share and Info.

## [0.3.0]

Profiles (pre-release).

- Save watermark profiles (watermark, both orientation placements, opacity, export settings) and reuse them.
- Profile sheet in the editor: choose, save changes, save as new, rename, duplicate, delete.
- The last used profile is preselected; the first-run default profile is called "Default".
- Unsaved changes are marked and confirmed before leaving the editor.

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
