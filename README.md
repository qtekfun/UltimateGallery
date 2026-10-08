# UltimateGallery

A refined, folder-based Android gallery whose flagship feature is **batch watermarking**: pick many
photos, place one watermark with your fingers (position, size, rotation), preview it on every photo
and export them all. Built for product photos (for example Wallapop listings), and growing into a
complete gallery.

Part of the Ultimate family of apps. Package: `com.qtekfun.ultimategallery`.

## Highlights

- Folder-based gallery on top of MediaStore, live updates, hidden folders that are app-only
  (nothing is touched on disk), pinch-to-resize grids, drag-to-select.
- Watermark editor: image/logo, text and tiled marks; one finger moves, two fingers scale and rotate;
  snapping guides with haptics; independent portrait and landscape placements stored as fractions of the
  image, so the mark lands in the same relative spot on every photo whatever its resolution.
- Profiles (templates) and batch export in the background, with the originals never modified.
- Viewer with zoom, shared-element transitions, thumbnail strip and video; crop and rotate editor.
- Material 3 Expressive, dynamic color, English and Spanish.
- **Fully offline**: no network permission, no Google Play Services, no analytics. FOSS dependencies only.

## Build

Requirements: JDK 21 and the Android SDK (platform 37).

```
./gradlew assembleDebug      # debug APK in app/build/outputs/apk/debug
./gradlew check              # ktlint, detekt, Android lint and unit tests
```

Run `./gradlew ktlintFormat` before committing.

## Documentation

- [`docs/requirements.md`](docs/requirements.md): what the app does
- [`docs/design.md`](docs/design.md): how it is built
- [`docs/tasks.md`](docs/tasks.md): the implementation plan and its progress
- [`CHANGELOG.md`](CHANGELOG.md) and [`RELEASING.md`](RELEASING.md)

## License

GPL-3.0-or-later, see [`LICENSE`](LICENSE). This choice is provisional until the owner confirms it.

UltimateGallery was functionally inspired by [EasyWatermark](https://github.com/rosuH/EasyWatermark)
(Apache-2.0). It is written from scratch and shares no code or design with it.
