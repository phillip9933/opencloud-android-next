package eu.opencloud.android.next

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.database.TransferDirection
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudTheme
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.feature.files.BackupFolderCrumb
import eu.opencloud.android.next.feature.files.BrowserLayout
import eu.opencloud.android.next.feature.files.FavoritesUiState
import eu.opencloud.android.next.feature.files.FileBrowserDestination
import eu.opencloud.android.next.feature.files.FileBrowserScreen
import eu.opencloud.android.next.feature.files.FileBrowserUiState
import eu.opencloud.android.next.feature.files.FolderBackupSettingsContent
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
    fun favoritesTopLevel_matchesGolden() {
        render(
            state = browserState(),
            favoritesState =
                FavoritesUiState(
                    listOf(
                        sampleResources().first().copy(
                            name = "Quarterly plan.pdf",
                            path = "/Documents/Quarterly plan.pdf",
                            kind = ResourceKind.FILE,
                            isFavorite = true,
                            hasLocalCopy = true,
                        ),
                    ),
                ),
        )
        composeRule.onNodeWithContentDescription("Navigate to Favorites").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Open navigation drawer").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Navigate to Favorites").fetchSemanticsNode()
        composeRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/images/favorites.png",
            roborazziOptions = browserRoborazziOptions(),
        )
    }

    @Test
    fun fileBrowserFixedSearch_acceptsQueryWithoutChangingLayout() {
        var query = ""
        render(
            state = browserState(),
            onSearchQueryChange = { query = it },
        )

        composeRule.onNodeWithContentDescription("Filter files").performTextInput("plan")
        check(query == "plan")
    }

    @Test
    fun fileBrowserActionSheet_hasSingleOfflineDownloadAction() {
        render(state = browserState(actionResource = sampleResources().last()))
        composeRule.onNodeWithText("Download for offline use").fetchSemanticsNode()
        composeRule.onAllNodesWithText("Download").assertCountEquals(0)
        composeRule.onAllNodesWithText("Make available offline").assertCountEquals(0)
    }

    @Test
    fun fileBrowserAvailabilityIndicators_areIconOnly() {
        render(state = browserState())
        composeRule.onAllNodesWithContentDescription("Cloud only").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Available offline").assertCountEquals(1)
        composeRule.onAllNodesWithText("Cloud only").assertCountEquals(0)
        composeRule.onAllNodesWithText("Available offline").assertCountEquals(0)
    }

    @Test
    fun fileBrowserSearchResults_matchesGolden() =
        capture(
            browserState(
                searchQuery = "plan",
                searchResults =
                    listOf(
                        resource("plans", "Plans", ResourceKind.FOLDER),
                        resource("plan", "Quarterly plan.pdf", ResourceKind.FILE, 42),
                    ),
                remoteSearchSupported = true,
            ),
            fileName = "file_browser_search_results",
        )

    @Test
    fun fileBrowserSearchFailureHeader_matchesGolden() =
        capture(
            searchFailureState(),
            fileName = "file_browser_search_failure_header",
        ) {
            val bannerBottom =
                composeRule
                    .onNodeWithText("Transfer failed: Broken.pdf")
                    .fetchSemanticsNode()
                    .boundsInRoot.bottom
            val firstResultTop =
                composeRule
                    .onNodeWithText("Plans")
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            check(firstResultTop >= bannerBottom)
        }

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
    fun fileBrowserBackupSettings_matchesGolden() {
        composeRule.activity.setContent {
            OpenCloudTheme {
                FolderBackupSettingsContent(
                    backups =
                        listOf(
                            FolderBackupEntity(
                                id = "camera-backup",
                                accountId = "account",
                                spaceId = "personal",
                                sourceTreeUri = "content://provider/tree/primary%3ADCIM",
                                sourceDisplayName = "DCIM",
                                destinationPath = "/Camera Uploads",
                                mediaType = "IMAGE",
                                wifiOnly = true,
                                chargingOnly = true,
                                deleteAfterUpload = false,
                            ),
                        ),
                    onDismiss = {},
                    onAdd = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("Folder & camera backup").fetchSemanticsNode()
        composeRule.onRoot().captureRoboImage(
            filePath = "file_browser_backup_settings",
            roborazziOptions = browserRoborazziOptions(),
        )
    }

    @Test
    fun fileBrowserBackupSettings_scrollsToLastActivePair() {
        composeRule.activity.setContent {
            OpenCloudTheme {
                FolderBackupSettingsContent(
                    backups =
                        (1..6).map { index ->
                            FolderBackupEntity(
                                id = "backup-$index",
                                accountId = "account",
                                spaceId = "personal",
                                sourceTreeUri = "content://provider/tree/primary%3AFolder$index",
                                sourceDisplayName = "Folder $index",
                                destinationPath = "/Backups/Folder $index",
                                mediaType = "ALL",
                                wifiOnly = false,
                                chargingOnly = false,
                                deleteAfterUpload = false,
                            )
                        },
                    onDismiss = {},
                    onAdd = {},
                    onDelete = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Active backup configurations").performScrollToIndex(5)
        composeRule.onNodeWithText("Folder 6").fetchSemanticsNode()
        composeRule.onNodeWithText("/Backups/Folder 6").fetchSemanticsNode()
        composeRule.onNodeWithText("Remote destination").fetchSemanticsNode()
        composeRule.onNodeWithText("Choose source folder").fetchSemanticsNode()
    }

    @Test
    fun fileBrowserBackupFolderPicker_matchesGolden() {
        composeRule.activity.setContent {
            OpenCloudTheme {
                FolderBackupSettingsContent(
                    backups = emptyList(),
                    pickerTrail = listOf(BackupFolderCrumb("photos", "Photos", "/Photos")),
                    pickerFolders = listOf(resource("camera", "Camera", ResourceKind.FOLDER, parentId = "photos")),
                    onDismiss = {},
                    onAdd = {},
                    onDelete = {},
                )
            }
        }
        composeRule.onNodeWithText("Select folder").performClick()
        composeRule.onNodeWithText("Select remote folder").fetchSemanticsNode()
        composeRule.onRoot().captureRoboImage(
            filePath = "file_browser_backup_folder_picker",
            roborazziOptions = browserRoborazziOptions(),
        )
    }

    @Test
    fun fileBrowserConflict_matchesGolden() =
        capture(
            browserState(
                transfers =
                    listOf(
                        TransferEntity(
                            id = "conflict",
                            accountId = "account",
                            spaceId = "personal",
                            resourceId = null,
                            direction = TransferDirection.UPLOAD.name,
                            sourceUri = "content://example/photo.jpg",
                            destinationPath = "/Photo.jpg",
                            displayName = "Photo.jpg",
                            mimeType = "image/jpeg",
                            bytesTotal = 42,
                            state = TransferState.CONFLICT.name,
                            createdAtEpochMillis = 0,
                            updatedAtEpochMillis = 0,
                        ),
                    ),
            ),
            "file_browser_upload_conflict",
        )

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
    fun fileBrowserViewToggle_cyclesThroughAllLayouts() {
        var selectedLayout: BrowserLayout? = null
        render(browserState(), onSetLayout = { selectedLayout = it })

        composeRule.onNodeWithContentDescription("Switch to compact list view").performClick()
        check(selectedLayout == BrowserLayout.CONDENSED_TABLE)

        render(browserState(layout = BrowserLayout.CONDENSED_TABLE), onSetLayout = { selectedLayout = it })
        composeRule.onNodeWithContentDescription("Switch to grid view").performClick()
        check(selectedLayout == BrowserLayout.TILES)

        render(browserState(layout = BrowserLayout.TILES), onSetLayout = { selectedLayout = it })
        composeRule.onNodeWithContentDescription("Switch to regular list view").performClick()
        check(selectedLayout == BrowserLayout.DEFAULT_TABLE)
    }

    @Suppress("LongParameterList")
    private fun capture(
        state: FileBrowserUiState,
        fileName: String = "file_browser_${state.layout}_${state.selectedIds.size}_${state.actionResource != null}",
        onSetLayout: (BrowserLayout) -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        interaction: () -> Unit = {},
    ) {
        render(state, onSetLayout, onSearchQueryChange)
        interaction()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = fileName,
            // Robolectric anti-aliasing varies by a few host-rendered pixels; layout and color changes still fail.
            roborazziOptions = browserRoborazziOptions(),
        )
    }

    private fun render(
        state: FileBrowserUiState,
        onSetLayout: (BrowserLayout) -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        favoritesState: FavoritesUiState = FavoritesUiState(),
        initialDestination: FileBrowserDestination = FileBrowserDestination.Personal,
    ) {
        composeRule.activity.setContent {
            OpenCloudTheme {
                FileBrowserScreen(
                    accountId = "account",
                    releaseVersion = "0.1.0",
                    state = state,
                    favoritesState = favoritesState,
                    onSelectSpace = {},
                    onOpen = {},
                    onNavigateUp = {},
                    onSetLayout = onSetLayout,
                    onToggleSelection = {},
                    onClearSelection = {},
                    onDownloadSelection = {},
                    onDeleteSelection = {},
                    onShowActions = {},
                    onDismissActions = {},
                    onCreateFolder = {},
                    onCreateSpace = {},
                    onRename = { _, _ -> },
                    onMove = {},
                    onCopy = {},
                    onDelete = {},
                    onUpload = {},
                    onDownloadForOffline = {},
                    onToggleFavorite = {},
                    onResolveConflict = { _, _ -> },
                    onClearMessage = {},
                    onGlobalAction = {},
                    onSearchQueryChange = onSearchQueryChange,
                    onOpenTransfers = {},
                    onOpenDeletedFiles = {},
                    onOpenSettings = {},
                    onOpenAccount = {},
                    onShareResource = {},
                    initialDestination = initialDestination,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun browserRoborazziOptions() =
        RoborazziOptions(
            compareOptions =
                RoborazziOptions.CompareOptions(
                    resultValidator = { result ->
                        result.pixelDifferences.toFloat() / result.pixelCount <= 0.001f
                    },
                ),
        )

    @Suppress("LongParameterList")
    private fun browserState(
        layout: BrowserLayout = BrowserLayout.DEFAULT_TABLE,
        selectedIds: Set<String> = emptySet(),
        actionResource: ResourceEntity? = null,
        folderTrail: List<FolderCrumb> = emptyList(),
        transfers: List<TransferEntity> = emptyList(),
        searchQuery: String = "",
        searchResults: List<ResourceEntity> = emptyList(),
        remoteSearchSupported: Boolean = false,
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
        transfers = transfers,
        searchQuery = searchQuery,
        searchResults = searchResults,
        remoteSearchSupported = remoteSearchSupported,
    )

    private fun sampleResources() =
        listOf(
            resource("documents", "Documents", ResourceKind.FOLDER),
            resource("photos", "Photos", ResourceKind.FOLDER),
            resource("welcome", "Welcome to OpenCloud.pdf", ResourceKind.FILE, 1_258_291).copy(hasLocalCopy = true),
        )

    private fun searchFailureState() =
        browserState(
            searchQuery = "plan",
            searchResults =
                listOf(
                    resource("plans", "Plans", ResourceKind.FOLDER),
                    resource("plan", "Quarterly plan.pdf", ResourceKind.FILE, 42),
                ),
            transfers =
                listOf(
                    TransferEntity(
                        id = "failed",
                        accountId = "account",
                        spaceId = "personal",
                        resourceId = "broken",
                        direction = TransferDirection.DOWNLOAD.name,
                        sourceUri = null,
                        destinationPath = "/Broken.pdf",
                        displayName = "Broken.pdf",
                        mimeType = "application/pdf",
                        bytesTotal = 42,
                        state = TransferState.FAILED.name,
                        error = "Network connection lost.",
                        createdAtEpochMillis = 0,
                        updatedAtEpochMillis = 0,
                    ),
                ),
        )

    private fun resource(
        id: String,
        name: String,
        kind: ResourceKind,
        size: Long = 0,
        parentId: String? = null,
    ) = ResourceEntity(
        "account",
        "personal",
        id,
        parentId,
        "/$name",
        name,
        kind,
        null,
        size,
        null,
        0,
        0,
    )
}
