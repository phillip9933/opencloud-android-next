package eu.opencloud.android.next.feature.shares

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource

@Composable
internal fun IncomingCopyMenu(
    file: IncomingBrowserItem.File,
    onAction: (IncomingBrowserItem.File, SharedCopyAction) -> Unit,
) {
    var expanded by remember(file) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.shares_actions_for_file, file.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val kept = file.localCopy?.offlinePinned == true
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (kept) R.string.shares_make_copy_temporary else R.string.shares_keep_local_copy,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onAction(file, if (kept) SharedCopyAction.TEMPORARY else SharedCopyAction.KEEP)
                },
            )
            if (file.localCopy != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shares_remove_local_copy)) },
                    onClick = {
                        expanded = false
                        onAction(file, SharedCopyAction.REMOVE)
                    },
                )
            }
        }
    }
}
