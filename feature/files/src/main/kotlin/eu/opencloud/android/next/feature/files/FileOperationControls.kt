package eu.opencloud.android.next.feature.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

@Composable
internal fun FileOperationControls(
    state: FileBrowserUiState,
    place: () -> Unit,
    cancel: () -> Unit,
    retry: (String) -> Unit,
    dismiss: (String) -> Unit,
) {
    if (state.clipboard == null && state.operations.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(OpenCloudDimensions.SpacingMd)) {
            state.clipboard?.let { source ->
                Text(
                    if (state.clipboardItems.size > 1) {
                        pluralStringResource(
                            R.plurals.file_operation_choose_destination_many,
                            state.clipboardItems.size,
                            state.clipboardItems.size,
                        )
                    } else {
                        stringResource(R.string.file_operation_choose_destination_one, source.name)
                    },
                )
                Row {
                    TextButton(onClick = place) {
                        Text(
                            stringResource(
                                if (state.moving) {
                                    R.string.file_operation_move_here
                                } else {
                                    R.string.file_operation_copy_here
                                },
                            ),
                        )
                    }
                    TextButton(onClick = cancel) { Text(stringResource(R.string.file_operation_cancel)) }
                }
            }
            state.operations.firstOrNull()?.let { operation ->
                Text(
                    if (operation.state in
                        setOf("NEEDS_ATTENTION", "BLOCKED_VAULT")
                    ) {
                        operation.error.orEmpty()
                    } else {
                        stringResource(
                            R.string.file_operation_verifying,
                            operation.sourcePath.substringAfterLast('/'),
                        )
                    },
                )
                if (operation.state in setOf("NEEDS_ATTENTION", "BLOCKED_VAULT")) {
                    Row {
                        if (operation.state == "NEEDS_ATTENTION") {
                            TextButton(onClick = { retry(operation.id) }) {
                                Text(stringResource(R.string.file_operation_check_again))
                            }
                        }
                        TextButton(onClick = { dismiss(operation.id) }) {
                            Text(stringResource(R.string.file_operation_dismiss))
                        }
                    }
                }
            }
        }
    }
}
