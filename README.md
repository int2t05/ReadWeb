# ReadWeb

[![Verify](https://github.com/int2t05/ReadWeb/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/int2t05/ReadWeb/actions/workflows/verify.yml)
[![Latest release](https://img.shields.io/github/v/release/int2t05/ReadWeb?display_name=tag)](https://github.com/int2t05/ReadWeb/releases/latest)

A lightweight Android reader for ordinary websites and online Markdown. Websites keep their original layout; Markdown is rendered in a bundled reader using `markdown-it`.

**Current version: 1.0.0.** The signed, minified release APK builds and launches on an API 36 emulator; the matching Git tag publishes it with a SHA-256 checksum. A target Vivo/OriginOS phone and device-specific performance measurements have not been verified.

## Features

- Open HTTP(S) websites in a simple tabbed WebView.
- Render `.md` and `.markdown` URLs in a consistent reading view; an explicit action handles Markdown served without a file extension.
- Keep up to 10 tabs while limiting the app to 2 resident WebViews to bound memory use.
- Save up to 100 bookmarks locally; a left-side tools panel also provides Markdown outline jumps, a persistent Markdown font size, and per-page reading progress.
- Bundle the Markdown renderer and styles so Markdown reading does not depend on a runtime CDN.
- Restrict Markdown to UTF-8 text up to 2 MiB, disable raw HTML, and reject unsafe link schemes.

## Requirements

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Node.js 20+ only when changing or testing the bundled Markdown renderer

## Build and test

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS or Linux:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

For a release, use the procedure in [docs/RELEASING.md](docs/RELEASING.md). A release APK must be signed with the maintainer's persistent release key; unsigned local release builds are not distributable.

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`. It uses the standard debug signing key for personal installation and testing; it is not a store release.

To test the renderer and check its dependency audit:

```sh
cd web-assets
npm ci
npm test
npm audit --audit-level=high
```

After changing the pinned Markdown dependency, regenerate the Android assets with `npm run sync-assets`, review the generated notices and license files, then rebuild the APK.

## Data and privacy

ReadWeb does not provide an account, sync, analytics, push notifications, or a background service. It stores open-tab URLs, titles, and scroll positions, saved bookmarks, and the Markdown font size on the device. WebView manages website cookies. Pages load directly from their own hosts and may collect data under their own privacy policies; ReadWeb does not make third-party sites private or trustworthy.

## Limits

- Android 8.0 (API 26) or later; the project currently targets API 36.
- Markdown is online-only. Local-file import and offline page archiving are not included.
- Markdown outline and font controls apply only to the bundled Markdown reader; ordinary websites keep their own layout. Reading progress is an estimate from the page's scrollable height.
- The local bookmark list is capped at 100 entries.
- When a WebView is evicted or the app restarts, the page reloads from its URL. Form contents and browser back/forward history are not preserved; scroll restoration is best-effort.
- The API 36 emulator flow has been smoke-tested; target Vivo/OriginOS compatibility, long-duration stability, and device performance have not been measured.

## Project documents

- [Documentation](docs/README.md)
- [Changelog](CHANGELOG.md)
- [Security policy](SECURITY.md)
- [Bundled third-party licenses](app/src/main/assets/THIRD-PARTY-NOTICES.txt)

## Contributing

Focused bug fixes and improvements are welcome. Before a large feature change, open a discussion in the repository issue tracker. Bug reports should include the Android version, WebView version, and steps to reproduce; remove private URLs, page contents, and personal data from logs. Run the checks above before submitting a change.

The GitHub Actions workflow in `.github/workflows/verify.yml` runs renderer tests and dependency audit, checks generated assets, then runs Android unit tests, lint, and a debug build on pushes and pull requests. Pushing a matching `v*` tag runs the signed release workflow in `.github/workflows/release.yml`.

## License

Project source is licensed under the [MIT License](LICENSE). Bundled third-party components remain under their respective licenses, listed in the [third-party notices](app/src/main/assets/THIRD-PARTY-NOTICES.txt).
