# OpenCloud Android Next

An independent Android client for OpenCloud, rebuilt with Kotlin and Jetpack Compose. This is a community project and is not an official OpenCloud GmbH release.

**Initial beta:** Android 8.0/API 26 or later, on arm64-v8a or x86_64 devices. Expect bugs; keep backups of important files.

## Features

- Personal files and project Spaces, search, favorites, recents and offline access.
- Uploads, downloads, copy/move, sharing links and Android file-picker integration.
- Folder/camera backup, local cache controls and optional biometric/device locking.
- PDF/JPEG scanning through a pinned, separately versioned [offline scanner SDK](https://github.com/phillip9933/open-android-doc-scanner).

## Install

Use the APK attached to [GitHub Releases](https://github.com/phillip9933/opencloud-android-next/releases). Check its SHA-256 against the attached SHA256SUMS file.

Pre-release testing APKs used a different, debug signing key. They cannot be updated in place by this release. Before uninstalling a test build, finish uploads and export any local-only files or drafts you need. Uninstalling removes private app data. Cloud files remain on your server. Log in and configure offline files/backups again after installing the release. Future official builds from this repository will retain the release signing identity.

## Build and test

Use JDK 21, Android SDK 36 and the included Gradle wrapper. First install the checksum-pinned scanner artifacts:

```sh
python scripts/install_offline_scanner_sdk.py
./gradlew ktlintCheck detekt testDebugUnitTest :app:verifyRoborazziDebug :app:lintRelease :app:assembleRelease
```

On Windows use `gradlew.bat`. Release builds contain no development credentials and are unsigned until the maintainer signs them. See [release procedure](docs/RELEASING.md), [privacy](PRIVACY.md), [security reporting](SECURITY.md) and [third-party notices](THIRD-PARTY-NOTICES.md).

Translations use Android resource files. Missing translations fall back to English; new languages are deferred during the beta freeze.

## Known beta limitations

- Large transfers across mobile/Wi-Fi changes can restart or require retry; HTTP 502/timeouts have been observed on some server/network paths.
- External editor, Office/server integrations and broader device/provider combinations have incomplete acceptance coverage.
- Scanner pages not yet exported may be lost if Android kills the capture process.
- Folder backup queues uploads; it is not a bidirectional mirror and does not delete source files. Changed destination names can require conflict resolution.
- Metadata comes from Android's supplied file representation; OpenCloud does not guarantee removal or preservation of GPS or other metadata.

The project is provided without warranties; test your server and workflow before relying on it.

## Source licensing

Licensed under the [GNU Affero General Public License version 3 (AGPL-3.0-only)](LICENSE), matching the license text used by Kura. See [NOTICE](NOTICE). Third-party components retain their own licenses and notices. The license does not grant rights to third-party trademarks.
