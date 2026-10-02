# Offline document scanner SDK

The files feature consumes the versioned [Open Android Doc Scanner](https://github.com/phillip9933/open-android-doc-scanner) Compose SDK. It is pinned to prerelease `v0.1.0-rc11` / source commit `5819098aec9387c4f823b7cebfec20ff0c782610` and uses the published coordinate `dev.offlinescan:scanner-ui-compose:0.1.0-rc11`. Gradle resolves the SDK's five `dev.offlinescan` modules and their declared dependencies from the release's Maven repository archive; the repository is scoped to that group in `settings.gradle.kts`.

Before building locally, run this from the project root. It works with Python 3 on Windows, macOS, and Linux:

```shell
python scripts/install_offline_scanner_sdk.py
```

The installer release version, URL, source commit, and SHA-256 are recorded in `scripts/offline-scanner-sdk.lock.json`. The installer downloads the archive, verifies its SHA-256 before extraction, rejects unsafe ZIP paths and symbolic links, and checks an existing Maven cache against every packaged repository file. It extracts the `maven/` directory under the ignored `.gradle/open-android-doc-scanner-0.1.0-rc11/` cache. Both GitHub Actions workflows install this dependency before running Gradle. See the root [README](../README.md) for the overall build and [Testing](TESTING.md) for verification commands. This page owns the scanner-specific setup.

The SDK release documents Android API 26 or newer and arm64-v8a or x86_64. This app's current `minSdk` is 26. The published OpenCV AAR contains `libopencv_java4.so` and `libc++_shared.so` for arm64-v8a and x86_64 only, so 32-bit-only devices are unsupported for this dependency. Keep the release archive's license and third-party notice files available when redistributing the SDK; they are included in the downloaded archive.

The scanner processes documents on-device and its release states that it does not require Play services or a model download. Its setup does not add an Internet permission. The SDK's attribution and third-party notices are available in the [pinned release license](https://github.com/phillip9933/open-android-doc-scanner/blob/v0.1.0-rc11/LICENSE), [NOTICE](https://github.com/phillip9933/open-android-doc-scanner/blob/v0.1.0-rc11/NOTICE), and [third-party notices](https://github.com/phillip9933/open-android-doc-scanner/blob/v0.1.0-rc11/THIRD-PARTY-NOTICES.md). Keep these notices available when redistributing the SDK. Review the final merged manifest and full dependency graph as part of app-level acceptance.

## Upgrading and host behavior

Update `offlineScanner` in `gradle/libs.versions.toml`, the repository version path in `settings.gradle.kts`, and `scripts/offline-scanner-sdk.lock.json` together for a reviewed published release; rerun the installer and host validation. Update the bundled attribution in `app/src/main/assets/third-party/open-android-doc-scanner` if the release changes it. Do not copy the scanner implementation or include its source checkout in this build.

Scan in the shared FAB menu opens the library flow using the app theme. Save chooses PDF or JPEG and the output name; the result enters the normal upload queue for the account/space/folder captured when scanning began. Existing upload conflicts and retry controls apply. OpenCloud journals committed outputs in private no-backup storage until the upload queue has durably staged them. Active unexported capture pages retain the SDK's documented process-death limitation. The user accepted basic Pixel scanning and destination selection for the initial beta. Broader permission-denial, multipage, cancellation and background/lock edge cases are not certified.

RC11 exposes a generic optional saveDestination Compose slot. OpenCloud supplies a clickable Location in OpenCloud button, initialized to the exact account, space and folder captured when Scan is tapped. Its folder picker stays within that account and supports changing between Personal and active Spaces. Cancelling leaves the saved location unchanged; choosing persists it before export while the scanner remains mounted. The control is disabled during export, and committed outputs cannot be retargeted. The standalone library still defaults to On this device. No scanner source is copied into OpenCloud.
