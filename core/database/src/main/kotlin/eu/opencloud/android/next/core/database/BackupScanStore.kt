package eu.opencloud.android.next.core.database

import androidx.room.withTransaction

/** A scan cannot publish work or settings from a removed, disabled or edited pair. */
class BackupScanStore(
    private val database: FileBrowserDatabase,
) {
    suspend fun save(configuration: FolderBackupEntity) =
        database.withTransaction {
            require(
                !configuration.enabled ||
                    !database.vaultExclusionDao().denies(
                        configuration.accountId,
                        configuration.spaceId,
                        configuration.destinationPath,
                        true,
                    ),
            ) { "Encrypted vault locations are unavailable." }
            database.folderBackupDao().upsert(configuration)
        }

    suspend fun enqueue(
        expected: FolderBackupEntity,
        transfer: TransferEntity,
    ): TransferEntity =
        database.withTransaction {
            require(expected.enabled && database.folderBackupDao().findById(expected.id) == expected) {
                "The backup configuration changed."
            }
            require(transfer.accountId == expected.accountId && transfer.spaceId == expected.spaceId)
            require(transfer.direction == TransferDirection.UPLOAD.name)
            val root = expected.destinationPath.trimEnd('/')
            require(transfer.destinationPath.startsWith("$root/"))
            FileBrowserStore(database).enqueueTransfer(transfer)
        }

    suspend fun complete(
        expected: FolderBackupEntity,
        now: Long,
    ) = database.withTransaction {
        val dao = database.folderBackupDao()
        if (expected.enabled && dao.findById(expected.id) == expected) {
            dao.upsert(expected.copy(lastSafeScanEpochMillis = now))
        }
    }
}
