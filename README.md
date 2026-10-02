# Raiun

An independent Android client for OpenCloud.

Raiun is a personal project maintained for the maintainer's own use and shared on a best-effort basis. It is not an official OpenCloud GmbH product, release, or supported client.

## AI-assisted development

AI tools assisted with development. A human maintainer reviews changes and runs tests, but review and testing have limits and do not guarantee that every issue is found.

**Initial beta:** Android 8.0/API 26 or later, on arm64-v8a or x86_64 devices. Expect bugs; keep backups of important files.

## About this project

I build projects to solve problems I run into in my own life. I share them because I believe in open source and hope others can learn from them, adapt them or find them useful.

Making it public does not mean it is a polished production product or suitable for every setup. Please read the documented limitations and decide whether it fits your needs. I'm happy to help where I can, but I can't promise a support schedule.

## Features

- Personal files and project Spaces, search, favorites, recents and offline access.
- Uploads, downloads, copy/move, sharing links and Android file-picker integration.
- Folder/camera backup, local cache controls and optional biometric/device locking.
- English and German, with an independent app-language choice and grouped Appearance settings.
- PDF/JPEG scanning through a pinned, separately versioned [offline scanner SDK](https://github.com/phillip9933/open-android-doc-scanner).

## Install

Download the signed APK from [GitHub Releases](https://github.com/phillip9933/raiun/releases) and verify it against the attached SHA256SUMS file. The previous OpenCloud Android Next release downloads have been retired.

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
- Metadata comes from Android's supplied file representation; Raiun does not guarantee removal or preservation of GPS or other metadata.

The project is provided without warranties; test your server and workflow before relying on it.

## Contribute

Start with [Contributing](CONTRIBUTING.md) for setup, change boundaries and reporting. The [documentation index](docs/README.md) links to the current layout, tests and release procedure.

## AI usage

I use AI tools to help with development, including analysis, code, tests and documentation. I care about security, privacy and protecting people's data, and I try to reflect that in how I build these projects.

I document validation and known limitations so you can assess the evidence for yourself. Contributions should disclose material AI assistance and distinguish checks actually run from checks still needed.

See [beta limitations](#known-beta-limitations), [testing guidance](docs/TESTING.md) and [contribution guidance](CONTRIBUTING.md#ai-usage).

## Source licensing

Licensed under the [GNU Affero General Public License version 3 (AGPL-3.0-only)](LICENSE), matching the license text used by Kura. See [NOTICE](NOTICE). Third-party components retain their own licenses and notices. The license does not grant rights to third-party trademarks.
