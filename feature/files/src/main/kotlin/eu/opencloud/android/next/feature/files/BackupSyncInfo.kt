package eu.opencloud.android.next.feature.files

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.opencloud.android.next.core.database.FolderBackupEntity
import eu.opencloud.android.next.core.database.TransferEntity
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupSyncInfo(
    backup: FolderBackupEntity,
    transfers: List<TransferEntity>,
) {
    androidx.compose.foundation.layout.Column {
        Text(
            if (backup.lastSafeScanEpochMillis >
                0
            ) {
                stringResource(
                    R.string.backup_settings_last_checked_through,
                    backupDate(backup.lastSafeScanEpochMillis),
                )
            } else {
                stringResource(R.string.backup_settings_not_scanned)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        val matching =
            transfers.filter {
                it.accountId == backup.accountId &&
                    it.spaceId == backup.spaceId &&
                    it.direction == "UPLOAD" &&
                    it.sourceUri?.startsWith(backup.sourceTreeUri.trimEnd('/') + "/document/") == true &&
                    it.destinationPath.startsWith(backup.destinationPath.trimEnd('/') + "/")
            }
        val active = matching.count { it.state in setOf("QUEUED", "RUNNING", "RETRY") }
        val failed = matching.count { it.state in setOf("FAILED", "CONFLICT") }
        if (active > 0) {
            Text(
                pluralStringResource(R.plurals.backup_settings_uploads_pending, active, active),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (failed >
            0
        ) {
            Text(
                pluralStringResource(R.plurals.backup_settings_uploads_need_attention, failed, failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val lastCompletedUpload =
            matching.filter { it.state == "SUCCEEDED" }.maxOfOrNull { it.updatedAtEpochMillis }
        if (lastCompletedUpload != null) {
            Text(
                stringResource(R.string.backup_settings_last_completed_upload, backupDate(lastCompletedUpload)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (active == 0 &&
            failed == 0
        ) {
            Text(
                stringResource(
                    if (backup.enabled) R.string.backup_settings_watching else R.string.backup_settings_paused,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun backupDate(timestamp: Long) =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
