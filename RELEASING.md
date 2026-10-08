# Releasing

Releases are tagged by hand; there is no release automation beyond the tag workflow.

1. Set `appVersion` in `gradle.properties` (for example `0.2.0`, or `1.0.0-rc1`).
2. Add a `## [x.y.z]` section to `CHANGELOG.md` and save the same text (500 bytes max) as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and `es-ES/...`.
   The version code is `(MAJOR*10000 + MINOR*100 + PATCH) * 100 + 99` (or `+ N` for `-rcN`).
3. Commit, push to `main`, wait for CI to be green.
4. `git tag vX.Y.Z && git push origin vX.Y.Z`. The *Release* workflow builds the APK and
   creates the GitHub pre-release with it attached.

## Signing

Set these repository secrets for a release-signed APK:
`UG_KEYSTORE_BASE64` (the keystore, base64), `UG_KEYSTORE_PASSWORD`, `UG_KEY_ALIAS`, `UG_KEY_PASSWORD`.
Without them the workflow signs with the debug key and says so in the release notes.
Never commit a keystore.

## Signing key and F-Droid

Create the project key once and keep it (and its passwords) safe and backed up:

```sh
keytool -genkeypair -v -keystore ultimategallery-release.jks -alias ultimategallery \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then add the four `UG_*` secrets described above. F-Droid builds the same source, compares its APK with
the one published on GitHub (the build is reproducible) and ships ours; give them the certificate
fingerprint (`keytool -list -v -keystore ultimategallery-release.jks | grep SHA256`) as
`AllowedAPKSigningKeys`. A draft of the F-Droid metadata is in `fdroid/`. Submit after 1.0.

Store screenshots go in `fastlane/metadata/android/<locale>/images/phoneScreenshots/` (taken on a device).
