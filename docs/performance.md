# Performance notes

How the app stays smooth with large libraries (requirement N1), and what was checked.

## Design choices
- **Thin MediaStore projections.** Folders and items are read with a minimal column set and grouped and
  sorted in Kotlin (MediaStore has no GROUP BY). `MediaStoreRepositoryLargeLibraryTest` loads 50,000 rows,
  groups them into 40 folders and lists one folder in about a second on the JVM.
- **Debounced refresh.** MediaStore change notifications are debounced (400 ms) and conflated, so a burst of
  changes (for example after an export) triggers a single re-query.
- **Thumbnails from the system cache.** Grid cells load through `ContentResolver.loadThumbnail`, which the
  system caches on disk, instead of decoding full photos. Cells request 64..1024 px.
- **Stable keys and content types** in every lazy grid and list, so scrolling reuses items and the
  shared-element transition can find its target.
- **Selection state is a plain `Set<Long>`** applied per visible cell; drag selection only touches the
  items between the anchor and the pointer.
- **Viewer** decodes the photo at 3x the fitted size (capped at 4096 px) and shows the thumbnail
  underneath until it arrives; only the current video page owns a player.
- **Export** runs sequentially for large photos (two permits, large photos take both) and decodes with a
  pixel budget (64 MP, power-of-two sampling, one retry at half the budget on OutOfMemoryError).
- **Release build** uses R8 shrinking and resource shrinking; the build is reproducible (two clean builds
  of the same commit give byte-identical APKs).

## Still to verify on a device
A macrobenchmark or manual profiling session on a real library (frame timing while flinging a 50k-item
folder, pinch resize, viewer swipe) was not possible in the headless build environment.
