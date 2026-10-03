package eu.opencloud.android.next

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import eu.opencloud.android.next.core.datastore.Appearance
import eu.opencloud.android.next.core.datastore.UserSettings
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudTheme
import eu.opencloud.android.next.feature.settings.SettingsDiagnostics
import eu.opencloud.android.next.feature.settings.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingPreferencesChangeOnlyTheSelectedFileType() {
        val state = mutableStateOf(UserSettings())
        compose.setContent {
            OpenCloudTheme {
                SettingsScreen(state.value, {}, {}, {}, onFileOpening = {
                    state.value =
                        state.value.copy(fileOpening = it)
                })
            }
        }
        compose.onAllNodesWithText("PDF documents").assertCountEquals(0)
        compose.onNodeWithText("Opening files").performScrollTo().performClick()
        compose.onNodeWithContentDescription("PDF documents").performScrollTo().performClick()
        compose.onNodeWithText("External app").performClick()
        assertEquals(true, state.value.fileOpening.externalPdf)
        assertEquals(false, state.value.fileOpening.externalText)
        assertEquals(false, state.value.fileOpening.externalImages)
    }

    @Test fun appearanceChoicesUpdateTheSelectedMode() {
        val state = mutableStateOf(UserSettings())
        compose.setContent {
            OpenCloudTheme(darkTheme = state.value.appearance == Appearance.DARK) {
                SettingsScreen(state.value, {}, {}, {}, onSetAppearance = {
                    state.value =
                        state.value.copy(appearance = it)
                })
            }
        }
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Change appearance").performClick()
        compose.onNode(hasText("System") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Change appearance").performClick()
        compose.onNode(hasText("Dark") and hasAnyAncestor(isPopup())).assertIsSelected()
        assertEquals(Appearance.DARK, state.value.appearance)
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithContentDescription("Change appearance").performClick()
        compose.onNode(hasText("Light") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("System").performClick()
        assertEquals(Appearance.SYSTEM, state.value.appearance)
    }

    @Test fun displayOptionsAndTemporaryCleanupAreExplicit() {
        val state = mutableStateOf(UserSettings())
        var cleared = 0
        compose.setContent {
            OpenCloudTheme {
                SettingsScreen(state.value, {}, {}, {}, onFileDisplay = {
                    state.value = state.value.copy(fileDisplay = it)
                }, onClearTemporary = { cleared++ })
            }
        }
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Hidden files").performScrollTo().performClick()
        assertEquals(true, state.value.fileDisplay.showHidden)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Clear temporary copies now").performScrollTo().performClick()
        assertEquals(0, cleared)
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Clear temporary copies now").performClick()
        compose.onNodeWithText("Clear temporary copies").performClick()
        assertEquals(1, cleared)
    }

    @Test fun diagnosticsRequireOptIn() {
        val state = mutableStateOf(UserSettings())
        var reads = 0
        compose.setContent {
            OpenCloudTheme {
                SettingsScreen(
                    state.value,
                    {},
                    {},
                    {},
                    diagnostics =
                        SettingsDiagnostics(
                            onSetEnabled = { state.value = state.value.copy(localDiagnosticsEnabled = it) },
                            onRead = { reads++ },
                        ),
                )
            }
        }
        compose.onAllNodesWithText("View local diagnostics").assertCountEquals(0)
        compose.onNodeWithContentDescription("Local diagnostics").performScrollTo().performClick()
        compose.onNodeWithText("View local diagnostics").performScrollTo().performClick()
        assertEquals(1, reads)
        compose.onNodeWithContentDescription("Local diagnostics").performScrollTo().performClick()
        compose.onAllNodesWithText("View local diagnostics").assertCountEquals(0)
    }

    @Test fun languageMenuOffersSystemEnglishAndGerman() {
        val language = mutableStateOf("")
        compose.setContent {
            OpenCloudTheme {
                SettingsScreen(
                    UserSettings(),
                    {},
                    {},
                    {},
                    languageTag = language.value,
                    onLanguage = { language.value = it },
                )
            }
        }
        compose.onNodeWithText("Appearance").performClick()
        compose.onNodeWithContentDescription("Change language").performClick()
        compose.onNode(hasText("System default") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("Deutsch").performClick()
        assertEquals("de", language.value)
        compose.onNodeWithContentDescription("Change language").performClick()
        compose.onNode(hasText("Deutsch") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("English").performClick()
        assertEquals("en", language.value)
        compose.onNodeWithContentDescription("Change language").performClick()
        compose.onNodeWithText("System default").performClick()
        assertEquals("", language.value)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Clear temporary copies now").performScrollTo()
    }

    @Test fun cacheRetentionMenuMarksCurrentChoiceAndUpdatesValue() {
        val state = mutableStateOf(UserSettings(temporaryCopyRetentionHours = 0))
        compose.setContent {
            OpenCloudTheme {
                SettingsScreen(
                    state.value,
                    {},
                    {},
                    { state.value = state.value.copy(temporaryCopyRetentionHours = it) },
                )
            }
        }
        compose.onNodeWithContentDescription("Change temporary copy retention").performScrollTo().performClick()
        compose.onNode(hasText("Never") and hasAnyAncestor(isPopup())).assertIsSelected()
        compose.onNodeWithText("1 hour").performClick()
        assertEquals(1, state.value.temporaryCopyRetentionHours)
        compose.onNodeWithContentDescription("Change temporary copy retention").performScrollTo().performClick()
        compose.onNode(hasText("1 hour") and hasAnyAncestor(isPopup())).assertIsSelected()
    }
}
