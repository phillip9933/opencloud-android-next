package eu.opencloud.android.next

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class FoundationScreenGoldenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun foundationScreen_matchesGolden() {
        // Robolectric anti-aliasing may vary by a few host-rendered pixels; meaningful visual changes still fail.
        composeRule.onRoot().captureRoboImage(
            filePath = "foundation_screen",
            roborazziOptions =
                RoborazziOptions(
                    compareOptions =
                        RoborazziOptions.CompareOptions(
                            resultValidator = { result ->
                                result.pixelDifferences.toFloat() / result.pixelCount <= 0.001f
                            },
                        ),
                ),
        )
    }
}
