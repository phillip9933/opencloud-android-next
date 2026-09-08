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
import eu.opencloud.android.next.core.database.TransferDirection
import eu.opencloud.android.next.core.database.TransferEntity
import eu.opencloud.android.next.core.database.TransferState
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudTheme
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.feature.search.SearchScreen
import eu.opencloud.android.next.feature.search.SearchUiState
import eu.opencloud.android.next.feature.transfers.TransfersScreen
import eu.opencloud.android.next.feature.transfers.TransfersUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class FeatureGoldenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun searchEmpty_matchesGolden() = captureSearch(SearchUiState(), "search_empty")

    @Test fun searchLoading_matchesGolden() =
        captureSearch(
            SearchUiState(query = "quarterly plan", remoteSupported = true, isRemoteLoading = true),
            "search_loading",
        )

    @Test fun searchResults_matchesGolden() =
        captureSearch(
            SearchUiState(
                query = "plan",
                remoteSupported = true,
                resources =
                    listOf(
                        resource("plans", "Plans", ResourceKind.FOLDER, "/Projects/Plans"),
                        resource("plan", "Quarterly plan.pdf", ResourceKind.FILE, "/Projects/Quarterly plan.pdf"),
                    ),
            ),
            "search_results",
        )

    @Test fun transfersEmpty_matchesGolden() = captureTransfers(TransfersUiState(), "transfers_empty")

    @Test fun transfersActive_matchesGolden() =
        captureTransfers(
            TransfersUiState(
                active =
                    listOf(
                        transfer(TransferFixture("upload", "Roadmap.pdf", TransferState.RUNNING, 42, 100)),
                        transfer(
                            TransferFixture(
                                "download",
                                "Photos.zip",
                                TransferState.QUEUED,
                                0,
                                250,
                                TransferDirection.DOWNLOAD,
                            ),
                        ),
                    ),
            ),
            "transfers_active",
        )

    @Test fun transfersFailed_matchesGolden() =
        captureTransfers(
            TransfersUiState(
                failed =
                    listOf(
                        transfer(
                            TransferFixture(
                                "failed",
                                "Budget.xlsx",
                                TransferState.FAILED,
                                12,
                                80,
                                error = "Network connection lost.",
                            ),
                        ),
                        transfer(TransferFixture("conflict", "Notes.txt", TransferState.CONFLICT, 0, 10)),
                    ),
            ),
            "transfers_failed",
        )

    @Test fun transfersOverflow_exposesClearAndRetryAllActions() {
        renderTransfers(failedTransfersState())
        composeRule.onNodeWithContentDescription("Transfer actions").performClick()
        composeRule.onNodeWithText("Retry all failed").fetchSemanticsNode()
        composeRule.onNodeWithText("Clear all").fetchSemanticsNode()
    }

    @Test fun transfersActions_matchesGolden() {
        renderTransfers(failedTransfersState())
        composeRule.onNodeWithContentDescription("Transfer actions").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = snapshotPath("transfers_actions"),
            roborazziOptions = featureRoborazziOptions(),
        )
    }

    private fun captureSearch(
        state: SearchUiState,
        fileName: String,
    ) {
        composeRule.activity.setContent {
            OpenCloudTheme {
                SearchScreen(
                    state = state,
                    onQueryChange = {},
                    onNavigateBack = {},
                    onShowActions = {},
                    onDismissActions = {},
                    onDownloadForOffline = {},
                    onUnavailableAction = {},
                    onDismissMessage = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = snapshotPath(fileName),
            roborazziOptions = featureRoborazziOptions(),
        )
    }

    private fun captureTransfers(
        state: TransfersUiState,
        fileName: String,
    ) {
        renderTransfers(state)
        composeRule.onRoot().captureRoboImage(
            filePath = snapshotPath(fileName),
            roborazziOptions = featureRoborazziOptions(),
        )
    }

    private fun renderTransfers(state: TransfersUiState) {
        composeRule.activity.setContent {
            OpenCloudTheme {
                TransfersScreen(
                    state = state,
                    onNavigateBack = {},
                    onRetry = {},
                    onCancel = {},
                    onResolveConflict = { _, _ -> },
                    onRetryAll = {},
                    onClearAll = {},
                    onDismissError = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun resource(
        id: String,
        name: String,
        kind: ResourceKind,
        path: String,
    ) = ResourceEntity("account", "space", id, null, path, name, kind, null, 42, null, 0, 0)

    private fun snapshotPath(fileName: String) = "src/test/snapshots/images/$fileName.png"

    private fun failedTransfersState() =
        TransfersUiState(
            failed = listOf(transfer(TransferFixture("failed", "Budget.xlsx", TransferState.FAILED, 0, 80))),
        )

    private fun featureRoborazziOptions() =
        RoborazziOptions(
            compareOptions =
                RoborazziOptions.CompareOptions(
                    // Robolectric host anti-aliasing varies slightly; layout and color changes still fail.
                    resultValidator = { result ->
                        result.pixelDifferences.toFloat() / result.pixelCount <= 0.001f
                    },
                ),
        )

    private fun transfer(fixture: TransferFixture) =
        TransferEntity(
            id = fixture.id,
            accountId = "account",
            spaceId = "space",
            resourceId = null,
            direction = fixture.direction.name,
            sourceUri = null,
            destinationPath = "/${fixture.name}",
            displayName = fixture.name,
            mimeType = null,
            bytesTotal = fixture.total,
            bytesTransferred = fixture.transferred,
            state = fixture.state.name,
            error = fixture.error,
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )

    private data class TransferFixture(
        val id: String,
        val name: String,
        val state: TransferState,
        val transferred: Long,
        val total: Long,
        val direction: TransferDirection = TransferDirection.UPLOAD,
        val error: String? = null,
    )
}
