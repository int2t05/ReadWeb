# Security policy

## Supported releases

Only the latest stable ReadWeb release receives security fixes.

## Reporting a vulnerability

Do not publish security reports as public issues. Use [GitHub private vulnerability reporting](https://github.com/int2t05/ReadWeb/security/advisories/new) when it is available for this repository. Include the affected version, Android and WebView versions, reproduction steps, and impact; remove private URLs and page contents.

ReadWeb loads websites directly in Android System WebView and enables JavaScript for website compatibility. It exposes no JavaScript-to-native bridge. Explicit HTTP and LAN URLs are supported and are not encrypted in transit. Treat website content and network destinations as untrusted.
