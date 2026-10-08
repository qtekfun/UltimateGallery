# Contributing

- Code, comments, commits and docs are in English. User-facing strings live in `res/values` (English)
  and `res/values-es` (Spanish); never hardcode strings.
- Keep the app offline: no `INTERNET` permission, no Play Services, no analytics, FOSS dependencies only
  (the `licensee` plugin enforces the license allow-list).
- Preview and export must share `render/WatermarkRenderer`. Watermark geometry is stored as fractions
  of the image size, with independent portrait and landscape placements.
- Originals are never modified by watermark export.
- Run `./gradlew ktlintFormat check` before opening a pull request; CI runs `check` and `assembleDebug`.
- Add tests for placement math, orientation buckets, export naming and the render pipeline when you touch them.
- Do not copy code from EasyWatermark or other apps.
- Never commit signing keys or secrets.
