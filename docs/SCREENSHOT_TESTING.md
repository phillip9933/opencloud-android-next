# Screenshot Golden Testing

OpenCloud Android Next uses [Roborazzi](https://github.com/takahirom/roborazzi) with Robolectric for host-side Compose visual regression tests. This is the required visual-parity gate for every screen and reusable design-system component.

## Golden ownership

- Checked-in golden images live in `src/test/snapshots/images` beside the tests that own them. The initial app-shell golden is under `app/src/test/snapshots/images`.
- Generated comparison reports and actual/diff images live under `build/` and are ignored by Git.
- Golden updates require a deliberate review of the image diff against the OpenCloud mobile-web reference; they must not be refreshed incidentally during unrelated changes.

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

The app-shell golden permits a 0.1% changed-pixel threshold solely for known Robolectric host anti-aliasing variance. New goldens should use zero tolerance unless the same host-renderer variance is demonstrated and documented in the test.
