# CLAUDE.md — UltimateGallery

Android gallery app with batch watermarking as its flagship feature. Package `com.qtekfun.ultimategallery`.

## Source of truth
- `docs/requirements.md` — what to build
- `docs/design.md` — how to build it
- `docs/tasks.md` — ordered plan; tick tasks as you finish them

## Working rules
- Code, comments, commits, README and docs in English. UI strings in `res/values` (English) and `res/values-es` (Spanish); never hardcode strings.
- Execute all phases of `docs/tasks.md` in order without stopping for approval between phases. Stop only if blocked by something only the owner can provide (signing keys, license choice, device testing).
- Built from scratch. Do not copy code or design from EasyWatermark or other apps. Credit EasyWatermark as inspiration in the About screen.
- Fully offline: no network permission, no Play Services, no analytics. Only FOSS dependencies (F-Droid compatible).
- Preview and export must share the same rendering code (`render/WatermarkRenderer`). Watermark geometry is always stored as fractions of image size, with independent portrait and landscape placements.
- Originals are never modified by watermark export. Overwriting only happens from the editor and follows the user's save-behavior setting.
- Keep CI green; add tests for placement math, orientation buckets, export naming and the render pipeline.
- UI quality matters: Material 3 Expressive, dynamic color, purposeful motion, haptics, predictive back, accessibility.

## Kickoff prompt
Read `docs/requirements.md`, `docs/design.md` and `docs/tasks.md`. Create the project and implement everything phase by phase, committing as you go and ticking `docs/tasks.md`. Do not pause between phases. Where the docs list an assumption, implement it and mention it in the final summary.
