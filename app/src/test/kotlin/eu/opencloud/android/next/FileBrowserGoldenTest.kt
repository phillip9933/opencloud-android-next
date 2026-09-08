package eu.opencloud.android.next

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
                    onAddBackup = {},
                    onDeleteBackup = {},
                    onOpenBackupPicker = {},
                    onOpenBackupPickerFolder = {},
                    onNavigateBackupPickerUp = {},
                    onCreateBackupPickerFolder = {},
                    onResolveConflict = { _, _ -> },
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
            roborazziOptions = browserRoborazziOptions(),
        )
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

    private fun browserState(
        layout: BrowserLayout = BrowserLayout.DEFAULT_TABLE,
        selectedIds: Set<String> = emptySet(),
        actionResource: ResourceEntity? = null,
        folderTrail: List<FolderCrumb> = emptyList(),
        transfers: List<TransferEntity> = emptyList(),
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
        parentId: String? = null,
    ) = ResourceEntity("account", "personal", id, parentId, "/$name", name, kind, null, size, null, 0, 0)
}
