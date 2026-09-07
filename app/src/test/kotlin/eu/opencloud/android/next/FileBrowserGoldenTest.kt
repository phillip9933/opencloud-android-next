package eu.opencloud.android.next

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudTheme
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.feature.files.BrowserLayout
import eu.opencloud.android.next.feature.files.FileBrowserScreen
import eu.opencloud.android.next.feature.files.FileBrowserUiState
import eu.opencloud.android.next.feature.files.FolderCrumb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class FileBrowserGoldenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fileBrowserList_matchesGolden() = capture(browserState())

    @Test
    fun fileBrowserGridSelection_matchesGolden() =
        capture(browserState(layout = BrowserLayout.TILES, selectedIds = setOf("documents", "welcome")))

    @Test
    fun fileBrowserListSelection_matchesGolden() =
        capture(
            browserState(selectedIds = setOf("documents")),
            fileName = "file_browser_DEFAULT_TABLE_selection",
        )

    @Test
    fun fileBrowserCondensedTable_matchesGolden() = capture(browserState(layout = BrowserLayout.CONDENSED_TABLE))

    @Test
    fun fileBrowserNestedFolder_matchesGolden() =
        capture(
            browserState(
                folderTrail =
                    listOf(
                        FolderCrumb("documents", "Documents"),
                        FolderCrumb("reports", "Reports"),
                    ),
            ),
            fileName = "file_browser_DEFAULT_TABLE_nested_folder",
        )

    @Test
    fun fileBrowserActions_matchesGolden() = capture(browserState(actionResource = sampleResources().last()))

    @Test
    fun fileBrowserBottomNavigation_matchesGolden() =
        capture(browserState(), "file_browser_bottom_navigation") {
            listOf("Personal", "Favorites", "Shares", "Spaces").forEach { label ->
                composeRule.onNodeWithContentDescription("Navigate to $label").fetchSemanticsNode()
            }
        }

    @Test
    fun fileBrowserNavigationDrawer_matchesGolden() =
        capture(browserState(), "file_browser_navigation_drawer") {
            composeRule.onNodeWithContentDescription("Open navigation drawer").performClick()
            composeRule.onNodeWithContentDescription("Navigate to Deleted files").fetchSemanticsNode()
            composeRule.onNodeWithContentDescription("Open Settings").fetchSemanticsNode()
            composeRule.onNodeWithText("Version 0.1.0").fetchSemanticsNode()
        }

    @Test
    fun fileBrowserSortOptions_matchesGolden() =
        capture(browserState(), "file_browser_sort_options") {
            composeRule.onNodeWithText("Name").performClick()
            listOf("Date modified", "Date opened", "Size").forEach { label ->
                composeRule.onNodeWithText(label).fetchSemanticsNode()
            }
        }

    @Test
    fun fileBrowserAccountInformation_matchesGolden() =
        capture(browserState(), "file_browser_account_information") {
            composeRule.onNodeWithContentDescription("Open account information").performClick()
            composeRule.onNodeWithText("account").fetchSemanticsNode()
        }

    @Test
    fun fileBrowserViewToggle_setsGridLayout() {
        var selectedLayout: BrowserLayout? = null
        capture(
            state = browserState(),
            fileName = "file_browser_view_toggle",
            onSetLayout = { selectedLayout = it },
        ) {
            composeRule.onNodeWithContentDescription("Switch to grid view").performClick()
        }
        check(selectedLayout == BrowserLayout.TILES)
    }

    private fun capture(
        state: FileBrowserUiState,
        fileName: String = "file_browser_${state.layout}_${state.selectedIds.size}_${state.actionResource != null}",
        onSetLayout: (BrowserLayout) -> Unit = {},
        interaction: () -> Unit = {},
    ) {
        composeRule.activity.setContent {
            OpenCloudTheme {
                FileBrowserScreen(
                    accountId = "account",
                    releaseVersion = "0.1.0",
                    state = state,
                    onSelectSpace = {},
                    onOpen = {},
                    onNavigateUp = {},
                    onSetLayout = onSetLayout,
                    onToggleSelection = {},
                    onClearSelection = {},
                    onShowActions = {},
                    onDismissActions = {},
                    onCreateFolder = {},
                    onCreateSpace = {},
                    onRename = { _, _ -> },
                    onMove = {},
                    onCopy = {},
                    onDelete = {},
                    onUpload = {},
                    onDownload = {},
                    onMakeAvailableOffline = {},
                    onClearMessage = {},
                    onGlobalAction = {},
                )
            }
        }
        interaction()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = fileName,
            // Robolectric anti-aliasing varies by a few host-rendered pixels; layout and color changes still fail.
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

    private fun browserState(
        layout: BrowserLayout = BrowserLayout.DEFAULT_TABLE,
        selectedIds: Set<String> = emptySet(),
        actionResource: ResourceEntity? = null,
        folderTrail: List<FolderCrumb> = emptyList(),
    ) = FileBrowserUiState(
        spaces =
            listOf(
                SpaceEntity(
                    "account",
                    "personal",
                    "Personal",
                    "personal",
                    "Your private files",
                    null,
                    "root",
                    null,
                    null,
                    null,
                ),
            ),
        spaceId = "personal",
        resources = sampleResources(),
        layout = layout,
        selectedIds = selectedIds,
        actionResource = actionResource,
        folderTrail = folderTrail,
    )

    private fun sampleResources() =
        listOf(
            resource("documents", "Documents", ResourceKind.FOLDER),
            resource("photos", "Photos", ResourceKind.FOLDER),
            resource("welcome", "Welcome to OpenCloud.pdf", ResourceKind.FILE, 1_258_291),
        )

    private fun resource(
        id: String,
        name: String,
        kind: ResourceKind,
        size: Long = 0,
    ) = ResourceEntity("account", "personal", id, null, "/$name", name, kind, null, size, null, 0, 0)
}
