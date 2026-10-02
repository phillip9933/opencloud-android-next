# Screenshot Golden Testing

OpenCloud Android Next uses [Roborazzi](https://github.com/takahirom/roborazzi) with Robolectric for host-side Compose visual regression tests. This is the required visual-parity gate for every screen and reusable design-system component.

## Golden ownership

- Active app golden images live in `app/src/test/snapshots/rendered`. The September 14 batch deliberately moved the app tests to Robolectric native graphics and reviewed 33 actual rendered images. Most older `images` and extensionless baselines are historical. Active recovery/text-editor baselines include `document_recovery.png`, `document_recovery_active_dark.png`, `text_editor.png` and `text_editor_queued_dark.png`. Image-document fixtures were removed with the feature. Do not treat the entire `images` directory as obsolete.

- Generated comparison reports and actual/diff images live under `build/` and are ignored by Git.
- Golden updates require a deliberate review of the image diff against the OpenCloud mobile-web reference; they must not be refreshed incidentally during unrelated changes.

October 1 feedback adds `file_browser_project_space.png` and `file_browser_add_menu.png` under `rendered`. Browser baselines were deliberately reviewed for the requested three-item bottom bar (Favorites, Personal, Spaces), drawer Recents/Offline entries and FAB availability. These host renders do not validate native camera controls or server uploads.

## Commands

From `C:\src\OpenCloud-Workspace\opencloud-android-next`:

```powershell
# Create or deliberately update baselines.
.\gradlew.bat :app:recordRoborazziDebug

# Compare current output with committed baselines.
.\gradlew.bat :app:verifyRoborazziDebug

# Run the normal unit-test suite, including golden verification.
.\gradlew.bat testDebugUnitTest
```

`verifyRoborazziDebug` fails on missing or mismatched goldens and writes actual/diff images and reports under `app/build/outputs/roborazzi` and `app/build/reports/roborazzi`.

## Required test determinism

Each golden test must set all rendering inputs explicitly:

- fixed device dimensions/qualifiers;
- fixed locale, layout direction, font scale, and API level where relevant;
- no real network, clock, animation, random data, or system dynamic color;
- fixed state supplied through pure UI-state fixtures.

The initial baseline is `FoundationScreenGoldenTest`, rendered at 360×800 dp. New screens require at least compact-phone goldens before they may be considered visually complete. Add 412×915 and font-scale 1.3 variants when the screen contains responsive or dense content.

The app golden classes use `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. The foundation fixture renders the sign-in screen directly, rather than racing asynchronous application startup and recording a loading spinner. Browser account navigation has a callback assertion; account-screen visuals are covered separately by `FeatureGoldenTest`.

The app-shell golden permits a 0.1% changed-pixel threshold solely for known Robolectric host anti-aliasing variance. New goldens should use zero tolerance unless the same host-renderer variance is demonstrated and documented in the test.

## German Settings coverage

`GermanSettingsGoldenTest` adds 360x800 light and 412x915 dark fixtures with font scale 1.3 (`settings_german.png`, `settings_german_large_dark.png`). Both use deterministic German qualifiers and verify the temporary-copy cleanup action remains reachable. The two new images were reviewed without clipped or overlapping content; existing baselines were not replaced. This covers representative translated Settings layout, not camera/system UI or native-device acceptance. `GermanLocaleResourceTest` checks German quantity selection and typed status/password formatting; `scripts/validate-locale-resources.ps1` checks extracted key/placeholder coverage.
Image-document screenshot fixtures were removed with the withdrawn feature on October 1. Historical validation remains in the implementation record.
