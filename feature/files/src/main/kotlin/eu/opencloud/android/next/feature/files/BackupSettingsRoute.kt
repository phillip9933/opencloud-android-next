package eu.opencloud.android.next.feature.files

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsRoute(
    accountId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = viewModel(key = "backup-settings-$accountId"),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var pendingBackup by remember { mutableStateOf<BackupDraft?>(null) }
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val draft = pendingBackup
            if (uri != null && draft != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                viewModel.saveBackup(
                    uri,
                    draft.destinationPath,
                    draft.mediaType,
                    draft.wifiOnly,
                    draft.chargingOnly,
                    draft.deleteAfterUpload,
                )
            }
            pendingBackup = null
        }

    LaunchedEffect(accountId) { viewModel.load(accountId) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Folder & camera backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        FolderBackupSettingsContent(
            backups = state.backups,
            pickerTrail = state.backupPickerTrail,
            pickerFolders = state.backupPickerResources,
            onDismiss = onNavigateBack,
            onAdd = { draft ->
                pendingBackup = draft
                backupLauncher.launch(null)
            },
            onDelete = viewModel::deleteBackup,
            onOpenPicker = viewModel::openBackupPicker,
            onOpenFolder = viewModel::openBackupPickerFolder,
            onNavigateUp = viewModel::navigateBackupPickerUp,
            onCreateFolder = viewModel::createBackupPickerFolder,
            modifier = Modifier.padding(padding),
        )
    }
}
