# Testing

Run commands from the repository root after the [development setup](../CONTRIBUTING.md#development). Commands use the Unix wrapper; on Windows use `.\gradlew.bat`. Python 3 and PowerShell (`pwsh` on CI) are needed for the helper checks below.

## Build and quality checks

```sh
python scripts/install_offline_scanner_sdk.py
./gradlew ktlintCheck detekt testDebugUnitTest :app:verifyRoborazziDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

These Gradle tasks match the [quality workflow](../.github/workflows/quality.yml). Detekt also depends on the repository's Compose design-token check. Release output is unsigned until signed separately. See [screenshot testing](SCREENSHOT_TESTING.md) before changing goldens; recording new images is not a passing verification result.

For a focused change, start with the affected module's unit tests and relevant UI/protocol checks. Documentation-only changes need path/link/command inspection, not an app suite. Release candidates still require the complete quality and emulator gates in [Releasing](RELEASING.md).

## Device tests

Use a disposable emulator or test device with synthetic files and test accounts, not a personal device or production server. The [emulator workflow](../.github/workflows/emulator.yml) uses API 35, Google APIs and x86_64:

```sh
./gradlew :app:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest :core:documentsprovider:connectedDebugAndroidTest :feature:files:connectedDebugAndroidTest
```

Before running individual journeys, inspect their fixtures and server requirements. Emulator tests do not establish physical-camera, biometric, external-editor or every server/provider combination's acceptance.

## Translations

```sh
python scripts/test_locale_validator.py
pwsh -File scripts/validate-locale-resources.ps1 -Locale de
```

These checks match CI. Missing translations may fall back to English. UI changes still need review for long text, large fonts, accessibility and layout direction.

## Report evidence

Record the revision, commands, environment, results and checks not run. Distinguish new results from historical evidence, and unit/screenshot checks from device or server acceptance. Do not include credentials, private URLs or personal files in reports. Current [beta limitations](../README.md#known-beta-limitations) remain relevant even when automated checks pass.
