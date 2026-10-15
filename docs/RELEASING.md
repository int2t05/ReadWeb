# Releasing ReadWeb

This document defines the release path for the Android app. A release tag must match `versionName` in `app/build.gradle` and have a matching note file under `docs/releases/`.

## Before release

1. Update `versionCode`, `versionName`, `CHANGELOG.md`, and `docs/releases/<version>.md`.
2. Run the checks in the root [README](../README.md), then install and smoke-test the release candidate on an Android device.
3. Confirm the release signing key is backed up securely. Never commit the keystore or passwords.
4. Configure these repository Actions secrets: `READWEB_RELEASE_KEYSTORE_BASE64`, `READWEB_RELEASE_STORE_PASSWORD`, `READWEB_RELEASE_KEY_ALIAS`, and `READWEB_RELEASE_KEY_PASSWORD`.
5. Enable GitHub private vulnerability reporting so the contact path in [SECURITY.md](../SECURITY.md) is active.

## Publish

Create and push an annotated version tag only after the release commit is reviewed:

```sh
git tag -a v1.0.0 -m "ReadWeb 1.0.0"
git push origin v1.0.0
```

The tag workflow checks the tag/version match, runs renderer tests and dependency audit, verifies generated assets, runs Android unit tests and lint, builds a minified release APK, verifies its signature and package metadata, and publishes the APK with a SHA-256 file as a GitHub Release.

The Gradle release build reads signing values from `READWEB_RELEASE_STORE_FILE`, `READWEB_RELEASE_STORE_PASSWORD`, `READWEB_RELEASE_KEY_ALIAS`, and `READWEB_RELEASE_KEY_PASSWORD`. Local builds without these values remain unsigned and must not be distributed as releases.

## Recovery

If a release fails before publication, fix the source or signing configuration and rerun the same checks. If a bad release has already been published, publish a higher `versionCode` with a corrected version; do not replace a tag or reuse a version code. Keep the previous signed artifact available until the replacement is verified.
