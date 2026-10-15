# ReadWeb technical design

Status: Version `1.0.0` is published in the [GitHub Release](https://github.com/int2t05/ReadWeb/releases/tag/v1.0.0). Branch and tag verification plus the signed Release workflow pass; the downloaded APK and checksum were verified, and the APK installs and launches on Android 16/API 36. Vivo/OriginOS and performance checks remain outstanding.

The product scope and acceptance criteria are defined in [PRD.md](PRD.md). This document is the source for implementation and verification details; the evidence behind the original competitor claims is in [the competitor review](research/competitor-review.md).

## Product boundary

ReadWeb is a compact Android reader for arbitrary HTTP(S) pages and online Markdown. Ordinary pages remain in their original site layout. Markdown is fetched by the app and rendered in a bundled local reader; the source is not cached for offline reading. The first release does not include local-file import, offline page archiving, site login, accounts, sync, plugins, downloads, notifications, or background services.

## Decisions

| Area | Decision | Reason and limit |
|---|---|---|
| App identity | Display name `ReadWeb`; application ID `io.github.int2t05.readweb`; version `1.0.0` | The package ID is based on the existing GitHub owner namespace and must be treated as stable once distributed. |
| Platform | Java 17, Android Views, one AndroidX Activity, Android system WebView; min SDK 26, compile/target SDK 36 | The product is a native WebView shell. Views avoid adding a second UI runtime; API 26 is the supported baseline for modern WebView renderer isolation and Safe Browsing. Google Play requires API 36 or higher for new apps and updates beginning 2026-08-31; API 36 meets that published minimum. |
| Build | Android Gradle Plugin 9.4.0, Gradle 9.8.1, JDK 17 | AGP 9.4 supports API 37 and requires at least Gradle 9.6/JDK 17. Gradle 9.8.1 is the current patch release and addresses three high-rated build-tool vulnerabilities. |
| Local reader assets | AndroidX WebKit `WebViewAssetLoader` serves packaged HTML, CSS, and JavaScript at `https://appassets.androidplatform.net/` | Avoids `file://` origins and keeps the renderer independent of CDN availability. |
| Markdown | Bundled `markdown-it` 14.3.1 with locked dependencies; CommonMark plus built-in GFM tables/strikethrough; `html: false`, `linkify: true`, `typographer: false`, `breaks: false`; validate destinations to HTTP(S)/mailto | The renderer stays in the markdown-it family used by the other projects. Version 14.3.1 includes the upstream fix for linkify quadratic paths; the transitive linkify-it version is locked. Input is capped at 2 MiB. |
| Markdown recognition | `.md` and `.markdown` paths are fetched as Markdown. A menu action supports extensionless text endpoints. If a `.md` URL responds with HTML, ReadWeb opens it as a normal page instead of parsing it. | Avoids fetching every ordinary page twice just to inspect its content type. Query strings and fragments do not affect suffix detection. |
| Network | User navigation accepts explicit HTTP and HTTPS; a bare host defaults to HTTPS. Markdown fetches use 10-second connect and 20-second read timeouts, at most five HTTP(S) redirects, UTF-8 only, and a 2 MiB response cap. HTTPS redirects cannot downgrade to HTTP. | No TLS-error bypass. Explicit HTTP remains available for user-selected LAN pages. |
| Tabs and memory | Up to 10 logical tabs; at most 2 resident WebViews. Least-recently-used views are detached and destroyed; a tab reloads from its last URL, while saved scroll position is restored best-effort. User-triggered popup windows open a tab; automatic popups are blocked. | This bounds native renderer memory. Back/forward history and form state are intentionally discarded when a WebView is evicted or the process restarts; actual PSS must still be measured on a device. |
| Persistence | Tab metadata, up to 100 bookmarks, and the Markdown font size in app-private SharedPreferences; no browsing-history service or cloud sync. | Bookmarks are deduplicated by normalized HTTP(S) URL and bounded to 2048 characters each; page scroll is saved per tab. Cookies remain WebView-managed. |
| Reading tools | A single left drawer contains bookmark actions, Markdown-only font controls and heading navigation, and an estimated scroll percentage for the current WebView. | The outline reads up to 300 rendered h1–h6 headings, with 200 characters per title, without executing page-provided HTML in native UI; progress uses scrollable content height and is not a reading-history record. |
| Web security | JavaScript is enabled for site compatibility, but no JavaScript-to-native bridge is exposed. File/content access and mixed content are disabled; Safe Browsing is on; third-party cookies are rejected; SSL errors are cancelled. Markdown raw HTML is disabled and its local page has a restrictive CSP. | Arbitrary websites remain untrusted. JavaScript support is not an assertion that all sites are safe or compatible. |
| Delivery | Debug APK uses the debug key. Release builds enable R8 and resource shrinking and read signing values from environment variables. | The persistent PKCS#12 release key is kept outside the workspace; its encoded key and credentials are configured as GitHub Actions secrets. The published APK passes signature and checksum verification; the release workflow verifies the checksum file before upload. |

## Components and ownership

```mermaid
flowchart TD
    UI[MainActivity: address, navigation, tab picker] --> RP[ReaderToolsPanel: bookmarks, outline, font, progress]
    UI --> TM[TabCollection: max 10 sessions]
    TM --> WV[LRU WebView host: max 2 live views]
    WV --> W[Ordinary HTTP(S) website]
    WV -->|.md or explicit reader action| MF[MarkdownFetcher: bounded HTTP(S), UTF-8]
    MF -->|base64 text payload| MR[Local Markdown reader WebView]
    MR -->|bundled static assets| AL[WebViewAssetLoader]
    AL --> MD[markdown-it 14.3.1 + local CSS]
    TM --> SP[SessionStore: app-private preferences]
    RP --> BS[BookmarkStore: max 100 local bookmarks]
```

`UrlPolicy` owns address normalization and allowed schemes. `MarkdownUrl` owns suffix recognition. `MarkdownFetcher` owns redirects, timeouts, response limits, content-type fallback, and UTF-8 decoding. The bundled `renderer.js` owns Markdown options and `reader-page.js` exposes only the local outline, jump, and font functions. `ReaderToolsPanel` owns the drawer view; `MainActivity` connects user actions to the active tab and owns the two-view LRU policy and Android lifecycle callbacks. `BookmarkCollection` owns URL identity, order, and limits; `BookmarkStore` persists its local list. `SessionStore` persists tab URL/title/scroll metadata.

## Failure and recovery

| Failure | User-visible behavior | Recovery |
|---|---|---|
| Invalid/unsupported URL | Keep the address and show a concise validation message | Edit and retry |
| Markdown timeout, 4xx/5xx, oversized or invalid UTF-8 response | Keep the source URL and show the reader error message | Retry with the Markdown action or switch to the website view; ordinary site HTTP status pages remain site content |
| TLS certificate error | Reject the load; do not proceed through the error | User may return or choose another URL |
| WebView renderer process exits | Preserve tab metadata and recreate its WebView | Reload current URL; Markdown tabs refetch the bounded source |
| Activity/process recreation | Rebuild the tab list and selected URL from app-private preferences | Reload selected page; restore scroll position where possible |
| Tab limit reached | Explain the 10-tab limit and offer the tab list | User closes a tab, then opens another |

## Verification plan

- JVM tests cover URL normalization/scheme policy, Markdown suffix parsing, response bounds, redirect limits, saved-tab ordering/limits, and bookmark deduplication/bounds/restoration.
- Node tests cover renderer configuration, representative CommonMark/GFM, dangerous links, raw HTML, Unicode and adversarial input, plus heading outline navigation and bounded font controls.
- Android build checks: `testDebugUnitTest`, `lintDebug`, `assembleDebug`, release shrink/build checks, APK signature verification, package/min-SDK inspection.
- Runtime checks on an emulator or phone should cover navigation, redirects, tabs, bookmarks across restart, the tools drawer, outline jumps, font persistence, scroll progress, process recreation, WebView renderer loss, light/dark Markdown, 2 MiB limits, and malicious Markdown. No target phone is currently attached, so those checks cannot be claimed from a desktop build alone.

## Verification results

- Android: `testDebugUnitTest`, `lintDebug`, and `assembleDebug` passed with Gradle 9.8.1.
- JVM: 28 tests passed, including bookmark limits/deduplication/restoration, progress bounds, and a local HTTP server for Markdown response limits, redirects, HTML fallback, UTF-8, and unsafe redirect checks.
- Renderer: 7 Node tests passed, including outline extraction/navigation and font bounds. `npm audit --audit-level=high --registry=https://registry.npmjs.org` reported no vulnerabilities; regenerating the checked-in renderer assets produced no changes.
- API 36 emulator: installed and launched the Debug APK; ordinary HTTP page layout and an in-page `.md` link worked; Markdown headings, emphasis, and lists rendered; the tools panel showed outline, font, bookmark, and progress controls; bookmark list, two-tab selection, and app-process restart restored saved URLs. No `AndroidRuntime` crash was present after fixing the background-thread WebView access found during the first run.
- Signed Release runtime: the R8-minified, resource-shrunk APK uses the persistent PKCS#12 release key, passes `apksigner` and package/version checks, installs on Android 16/API 36, and launches without an `AndroidRuntime` fatal exception. The downloaded GitHub artifact also installs and launches. Website-to-Markdown navigation and return from a linked website were separately verified on the same minified source build with a temporary debug signature.
- Android lint completed without errors. It retains advisory warnings for the deliberate API 36 compile/target baseline and the programmatic-only reader panel is explicitly exempt from XML preview constructor checks. The build emits Gradle's generic deprecated-feature notice from the toolchain.
- The release build is R8-minified and resource-shrunk. The persistent signing key is outside the workspace and its encoded contents and credentials are GitHub Actions secrets. The release workflow verifies the APK signature and metadata, then attaches the APK and SHA-256 to a GitHub Release.
- Security audit: the app manifest declares only `INTERNET`; AndroidX adds a signature-protected, non-exported receiver permission. Backups are disabled, file/content access and mixed content are disabled, Safe Browsing is enabled, TLS errors are rejected, and no JavaScript-to-native bridge is exposed. Cleartext HTTP remains enabled so users can open explicit HTTP/LAN URLs.
- Not verified: target Vivo/OriginOS behavior, the PRD's performance and 30-minute stability targets, rotation and renderer-process recovery, and network-specific TLS/redirect cases on a physical device. GitHub Actions passed the initial branch and release-tag checks.

## References

- [Android WebView guide](https://developer.android.com/develop/ui/views/layout/webapps/webview), accessed 2026-10-15: WebView memory is higher than ordinary views; windows require host-app behavior; WebViews must be explicitly destroyed.
- [Android WebView local content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content), accessed 2026-10-15: recommends `WebViewAssetLoader` for in-app HTML/CSS/JS under an HTTP(S) origin.
- [Android WebView memory guide](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory), accessed 2026-10-15: renderer processes use native memory and require explicit lifecycle management.
- [Android Gradle Plugin 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes), accessed 2026-10-15: API 37 maximum, Gradle 9.6.0 and JDK 17 compatibility.
- [Gradle 9.8.1 release notes](https://docs.gradle.org/9.8.1/release-notes.html), accessed 2026-10-15: the current patch release addresses three high-rated vulnerabilities and recommends upgrading.
- [Android 16 SDK setup](https://developer.android.com/about/versions/16/setup-sdk), accessed 2026-10-15: Android 16 is API 36.
- [Google Play target API requirement](https://developer.android.com/google/play/requirements/target-sdk), accessed 2026-10-15: new apps and updates must target API 36 or higher beginning 2026-08-31.
- [AndroidX WebKit](https://github.com/androidx/androidx/tree/androidx-main/webkit), accessed 2026-10-15: `WebViewAssetLoader` API and current dependency guidance.
- [markdown-it safety](https://github.com/markdown-it/markdown-it/blob/master/docs/safety.md), accessed 2026-10-15: raw HTML should remain disabled for untrusted Markdown.
- [GHSA-253c-mchw-3w2r](https://github.com/advisories/GHSA-253c-mchw-3w2r), accessed 2026-10-15: linkify complexity issue affects markdown-it 15.0.0 and versions before 14.3.1; patched in 15.0.1 and 14.3.1.
