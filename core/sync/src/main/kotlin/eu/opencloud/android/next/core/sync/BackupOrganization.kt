package eu.opencloud.android.next.core.sync

import eu.opencloud.android.next.core.database.FolderBackupEntity
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun backupDestination(
    backup: FolderBackupEntity,
    document: BackupDocument,
): String {
    val dateFolder =
        when (backup.dateOrganization) {
            "YEAR_MONTH" ->
                if (document.modified > 0) {
                    DateTimeFormatter
                        .ofPattern(
                            "uuuu/MM",
                        ).withZone(ZoneOffset.UTC)
                        .format(Instant.ofEpochMilli(document.modified))
                } else {
                    "Undated"
                }
            else -> ""
        }
    return backupParent(backupParent(backup.destinationPath, dateFolder), document.relativeParent)
}

internal fun backupReceiptKey(
    backup: FolderBackupEntity,
    document: BackupDocument,
): String =
    listOf(
        backup.destinationPath,
        backup.dateOrganization,
        document.relativeParent,
        document.name,
        document.uri.toString(),
    ).joinToString("\u0000")
