package eu.opencloud.android.next.feature.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

@Composable
@Suppress("LongParameterList") // Screen state, explicit actions and scoped editor identity.
fun TextEditorRoute(
    accountId: String,
    spaceId: String,
    resourceId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TextEditorViewModel = viewModel(key = "editor-$accountId-$spaceId-$resourceId"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(accountId, spaceId, resourceId) { viewModel.load(accountId, spaceId, resourceId) }
    BackHandler { viewModel.close(onClose) }
    TextEditorScreen(
        state,
        viewModel::edit,
        viewModel::save,
        viewModel::refresh,
        { viewModel.close(onClose) },
        modifier,
        onDiscard = { viewModel.discard(onClose) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // Screen state, explicit actions and scoped editor identity.
fun TextEditorScreen(
    state: TextEditorState,
    onEdit: (String) -> Unit,
    onSave: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onDiscard: () -> Unit = {},
) {
    val queued = state.draft?.queuedId != null
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(state.draft?.name ?: stringResource(R.string.document_edit_text)) }, navigationIcon = {
            IconButton(
                onClick = onClose,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.document_back)) }
        }, actions = {
            TextButton(
                onClick = onSave,
                enabled = !state.busy && !queued && state.draft != null,
            ) { Text(stringResource(R.string.document_save_server)) }
        })
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(OpenCloudDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
        ) {
            if (state.busy) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (queued) {
                Text(stringResource(R.string.document_save_queued))
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.busy,
                ) { Text(stringResource(R.string.document_check_save)) }
            } else if (state.draft != null) {
                Text(
                    if (state.savingDraft) {
                        stringResource(
                            R.string.document_saving_draft,
                        )
                    } else {
                        stringResource(R.string.document_draft_saved)
                    },
                )
            }
            state.draft?.let { draft ->
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onEdit,
                    readOnly = state.busy || queued,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text(stringResource(R.string.document_utf8_text)) },
                )
            }
            DiscardDraftButton(enabled = !state.busy && !queued && state.draft != null, onDiscard = onDiscard)
        }
    }
}

@Composable
private fun DiscardDraftButton(
    enabled: Boolean,
    onDiscard: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TextButton(
            onClick = { confirming = true },
            enabled = enabled,
        ) { Text(stringResource(R.string.document_discard_draft)) }
        if (confirming) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(stringResource(R.string.document_discard_title)) },
                text = {
                    Text(
                        stringResource(R.string.document_discard_description),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirming = false
                        onDiscard()
                    }) { Text(stringResource(R.string.document_discard)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirming = false },
                    ) { Text(stringResource(R.string.document_cancel)) }
                },
            )
        }
    }
}
