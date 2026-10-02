# Translations

English defaults live in each module’s `src/main/res/values` directory. German translations use matching resource names under `values-de`. Use Android string/plural resources rather than branching on language in Kotlin. Preserve numbered format arguments and Android XML escaping. German uses informal “du”; product names such as OpenCloud and Spaces remain unchanged.

Settings → Appearance → Language offers System default, English and Deutsch. AppCompat persists the choice on Android 12 and older; Android 13 and newer use the platform per-app language preference. The supported language list is in `app/src/main/res/xml/locales_config.xml`.

The scanner remains a versioned library. Its German strings are resource overlays in `app/src/main/res/values-de/strings_scanner_sdk.xml`; no scanner implementation or English defaults are copied. The validator reads English resource definitions from the installed, pinned scanner AAR. When upgrading the SDK, review added or changed strings and the capture/review/save UI.

After installing the pinned scanner SDK, run `scripts/validate-locale-resources.ps1 -Locale de -RequireComplete` for a complete German check. CI also checks XML and placeholder safety. Other languages can still fall back to English; introducing a language does not require translating every resource immediately. Add supported languages to the app-language configuration and selector only when ready for users.

Use screenshot and interaction tests to check text fit, enlarged fonts, language selection and returning to the system default. Structural validation alone is not a linguistic or physical-device review.
