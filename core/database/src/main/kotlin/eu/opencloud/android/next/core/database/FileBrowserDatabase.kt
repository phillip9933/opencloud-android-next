package eu.opencloud.android.next.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.room.withTransaction
import eu.opencloud.android.next.core.model.ResourceKind
import eu.opencloud.android.next.core.model.auth.Account
import eu.opencloud.android.next.core.model.auth.ServerCapabilities
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        AccountEntity::class,
        SpaceEntity::class,
        ResourceEntity::class,
        TransferEntity::class,
        FolderBackupEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(FileBrowserConverters::class)
abstract class FileBrowserDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun spaceDao(): SpaceDao

    abstract fun resourceDao(): ResourceDao

    abstract fun transferDao(): TransferDao

    abstract fun folderBackupDao(): FolderBackupDao

    companion object {
        @Volatile
        private var instance: FileBrowserDatabase? = null

        fun create(context: Context): FileBrowserDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(
                        context.applicationContext,
                        FileBrowserDatabase::class.java,
                        "opencloud-file-browser.db",
                    ).addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                    ).build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            }

        private val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, `serverUrl` TEXT NOT NULL, " +
                            "`userId` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                            "`authenticationType` TEXT NOT NULL, " +
                            "`tusSupported` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `transfers` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, " +
                            "`spaceId` TEXT NOT NULL, `resourceId` TEXT, " +
                            "`direction` TEXT NOT NULL, `sourceUri` TEXT, " +
                            "`destinationPath` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT, " +
                            "`bytesTotal` INTEGER NOT NULL, `bytesTransferred` INTEGER NOT NULL, " +
                            "`state` TEXT NOT NULL, `error` TEXT, `workId` TEXT, " +
                            "`overwrite` INTEGER NOT NULL, `offlinePin` INTEGER NOT NULL, " +
                            "`tusUrl` TEXT, `tusOffset` INTEGER NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                            "`createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transfers_accountId_state` " +
                            "ON `transfers` (`accountId`, `state`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_transfers_accountId_spaceId_resourceId_direction` " +
                            "ON `transfers` (`accountId`, `spaceId`, `resourceId`, `direction`)",
                    )
                }
            }

        private val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `oidcIssuer` TEXT")
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `oidcTokenEndpoint` TEXT")
                    db.execSQL(
                        "ALTER TABLE `transfers` ADD COLUMN `deleteSourceAfterSuccess` INTEGER NOT NULL DEFAULT 0",
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `folder_backups` (`id` TEXT NOT NULL, " +
                            "`accountId` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `sourceTreeUri` TEXT NOT NULL, " +
                            "`destinationPath` TEXT NOT NULL, " +
                            "`mediaType` TEXT NOT NULL, `wifiOnly` INTEGER NOT NULL, " +
                            "`chargingOnly` INTEGER NOT NULL, " +
                            "`deleteAfterUpload` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                            "`lastSafeScanEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_folder_backups_accountId_sourceTreeUri_mediaType` " +
                            "ON `folder_backups` (`accountId`, `sourceTreeUri`, `mediaType`)",
                    )
                }
            }

        private val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `folder_backups` ADD COLUMN `sourceDisplayName` TEXT NOT NULL DEFAULT ''",
                    )
                }
            }

        private val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `remoteSearchUrl` TEXT")
                }
            }

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `accounts` ADD COLUMN `trashSupported` INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}

class FileBrowserConverters {
    @TypeConverter
    fun resourceKindToString(value: ResourceKind): String = value.name

    @TypeConverter
    fun stringToResourceKind(value: String): ResourceKind = ResourceKind.valueOf(value)
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @androidx.room.PrimaryKey val id: String,
    val serverUrl: String,
    val userId: String,
    val displayName: String,
    val authenticationType: String,
    val tusSupported: Boolean,
    val isActive: Boolean = true,
    val oidcIssuer: String? = null,
    val oidcTokenEndpoint: String? = null,
    val remoteSearchUrl: String? = null,
    val trashSupported: Boolean = false,
)

@Entity(
    tableName = "spaces",
    primaryKeys = ["accountId", "driveId"],
    indices = [Index(value = ["accountId", "name"])],
)
data class SpaceEntity(
    val accountId: String,
    val driveId: String,
    val name: String,
    val type: String,
    val description: String?,
    val ownerName: String?,
    val rootId: String,
    val rootWebDavUrl: String?,
    val rootETag: String?,
    val quotaBytes: Long?,
    val isDisabled: Boolean = false,
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "resources",
    primaryKeys = ["accountId", "spaceId", "remoteId"],
    indices = [
        Index(value = ["accountId", "spaceId", "parentId", "name"], unique = true),
        Index(value = ["accountId", "spaceId", "parentId"]),
    ],
)
data class ResourceEntity(
    val accountId: String,
    val spaceId: String,
    val remoteId: String,
    val parentId: String?,
    val path: String,
    val name: String,
    val kind: ResourceKind,
    val mimeType: String?,
    val sizeBytes: Long,
    val eTag: String?,
    val modifiedAtEpochMillis: Long,
    val createdAtEpochMillis: Long,
    val isFavorite: Boolean = false,
    val hasLocalCopy: Boolean = false,
    val localPath: String? = null,
    val offlinePinned: Boolean = false,
)

@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["accountId", "state"]),
        Index(value = ["accountId", "spaceId", "resourceId", "direction"]),
    ],
)
data class TransferEntity(
    @androidx.room.PrimaryKey val id: String,
    val accountId: String,
    val spaceId: String,
    val resourceId: String?,
    val direction: String,
    val sourceUri: String?,
    val destinationPath: String,
    val displayName: String,
    val mimeType: String?,
    val bytesTotal: Long,
    val bytesTransferred: Long = 0,
    val state: String = TransferState.QUEUED.name,
    val error: String? = null,
    val workId: String? = null,
    val overwrite: Boolean = false,
    val offlinePin: Boolean = false,
    val deleteSourceAfterSuccess: Boolean = false,
    val tusUrl: String? = null,
    val tusOffset: Long = 0,
    val attemptCount: Int = 0,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferState { QUEUED, RUNNING, RETRY, CONFLICT, SUCCEEDED, FAILED, CANCELLED }

@Entity(
    tableName = "folder_backups",
    indices = [Index(value = ["accountId", "sourceTreeUri", "mediaType"], unique = true)],
)
data class FolderBackupEntity(
    @androidx.room.PrimaryKey val id: String,
    val accountId: String,
    val spaceId: String,
    val sourceTreeUri: String,
    val sourceDisplayName: String = "",
    val destinationPath: String,
    val mediaType: String,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val deleteAfterUpload: Boolean,
    val enabled: Boolean = true,
    val lastSafeScanEpochMillis: Long = 0,
)

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :accountId AND isActive = 1 LIMIT 1")
    suspend fun findById(accountId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY displayName COLLATE NOCASE")
    suspend fun findActive(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY displayName COLLATE NOCASE")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("DELETE FROM accounts WHERE id = :accountId")
    suspend fun delete(accountId: String)
}

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces WHERE accountId = :accountId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeSpaces(accountId: String): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE accountId = :accountId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    suspend fun findSpaces(accountId: String): List<SpaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spaces: List<SpaceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(space: SpaceEntity)

    @Query("SELECT COUNT(*) FROM spaces WHERE accountId = :accountId")
    suspend fun count(accountId: String): Int

    @Query("SELECT * FROM spaces WHERE accountId = :accountId AND driveId = :spaceId LIMIT 1")
    suspend fun findById(
        accountId: String,
        spaceId: String,
    ): SpaceEntity?

    @Delete
    suspend fun delete(space: SpaceEntity)

    @Query("DELETE FROM spaces WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface ResourceDao {
    @Query(
        "SELECT * FROM resources WHERE accountId = :accountId AND isFavorite = 1 " +
            "ORDER BY CASE kind WHEN 'FOLDER' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    fun observeFavorites(accountId: String): Flow<List<ResourceEntity>>

    @Query(
        "SELECT * FROM resources WHERE accountId = :accountId " +
            "AND (name LIKE :pattern ESCAPE '\\' OR path LIKE :pattern ESCAPE '\\') " +
            "ORDER BY CASE kind WHEN 'FOLDER' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    fun search(
        accountId: String,
        pattern: String,
    ): Flow<List<ResourceEntity>>

    @Query(
        "SELECT * FROM resources WHERE accountId = :accountId AND spaceId = :spaceId AND " +
            "((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) " +
            "ORDER BY CASE kind WHEN 'FOLDER' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    fun observeChildren(
        accountId: String,
        spaceId: String,
        parentId: String?,
    ): Flow<List<ResourceEntity>>

    @Query(
        "SELECT * FROM resources WHERE accountId = :accountId AND spaceId = :spaceId AND remoteId = :resourceId LIMIT 1",
    )
    suspend fun findById(
        accountId: String,
        spaceId: String,
        resourceId: String,
    ): ResourceEntity?

    @Query("SELECT * FROM resources WHERE accountId = :accountId AND spaceId = :spaceId ORDER BY path COLLATE NOCASE")
    suspend fun findBySpace(
        accountId: String,
        spaceId: String,
    ): List<ResourceEntity>

    @Query(
        "SELECT * FROM resources WHERE accountId = :accountId AND spaceId = :spaceId AND " +
            "((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId) " +
            "ORDER BY CASE kind WHEN 'FOLDER' THEN 0 ELSE 1 END, name COLLATE NOCASE",
    )
    suspend fun findChildren(
        accountId: String,
        spaceId: String,
        parentId: String?,
    ): List<ResourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(resource: ResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(resource: ResourceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(resources: List<ResourceEntity>)

    @Update
    suspend fun update(resource: ResourceEntity)

    @Query(
        "UPDATE resources SET hasLocalCopy = :hasLocalCopy, localPath = :localPath, offlinePinned = :offlinePinned " +
            "WHERE accountId = :accountId AND spaceId = :spaceId AND remoteId = :resourceId",
    )
    @Suppress("LongParameterList")
    suspend fun updateLocalCopy(
        accountId: String,
        spaceId: String,
        resourceId: String,
        hasLocalCopy: Boolean,
        localPath: String?,
        offlinePinned: Boolean,
    )

    @Query(
        "UPDATE resources SET offlinePinned = :pinned " +
            "WHERE accountId = :accountId AND spaceId = :spaceId AND remoteId = :resourceId",
    )
    suspend fun setOfflinePinned(
        accountId: String,
        spaceId: String,
        resourceId: String,
        pinned: Boolean,
    )

    @Query(
        "UPDATE resources SET isFavorite = :favorite " +
            "WHERE accountId = :accountId AND spaceId = :spaceId AND remoteId = :resourceId",
    )
    suspend fun setFavorite(
        accountId: String,
        spaceId: String,
        resourceId: String,
        favorite: Boolean,
    )

    @Query("SELECT * FROM resources WHERE offlinePinned = 1")
    suspend fun findOfflinePinned(): List<ResourceEntity>

    @Delete
    suspend fun delete(resource: ResourceEntity)

    @Query("DELETE FROM resources WHERE accountId = :accountId AND spaceId = :spaceId AND path LIKE :pathPrefix")
    suspend fun deleteDescendants(
        accountId: String,
        spaceId: String,
        pathPrefix: String,
    )

    @Query("DELETE FROM resources WHERE accountId = :accountId AND spaceId = :spaceId")
    suspend fun deleteAll(
        accountId: String,
        spaceId: String,
    )

    @Query("DELETE FROM resources WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE accountId = :accountId ORDER BY createdAtEpochMillis DESC")
    fun observeForAccount(accountId: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers WHERE state IN ('QUEUED', 'RUNNING', 'RETRY')")
    suspend fun findPending(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE accountId = :accountId")
    suspend fun findForAccount(accountId: String): List<TransferEntity>

    @Query(
        "SELECT * FROM transfers WHERE accountId = :accountId AND spaceId = :spaceId " +
            "AND resourceId = :resourceId AND direction = 'DOWNLOAD' " +
            "AND state IN ('QUEUED', 'RUNNING', 'RETRY') LIMIT 1",
    )
    suspend fun findActiveDownload(
        accountId: String,
        spaceId: String,
        resourceId: String,
    ): TransferEntity?

    @Query(
        "SELECT * FROM transfers WHERE accountId = :accountId AND spaceId = :spaceId " +
            "AND sourceUri = :sourceUri AND destinationPath = :destinationPath AND direction = 'UPLOAD' " +
            "AND state IN ('QUEUED', 'RUNNING', 'RETRY') LIMIT 1",
    )
    suspend fun findActiveUpload(
        accountId: String,
        spaceId: String,
        sourceUri: String,
        destinationPath: String,
    ): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transfer: TransferEntity)

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE accountId = :accountId AND state IN ('SUCCEEDED', 'CANCELLED')")
    suspend fun deleteHistory(accountId: String)

    @Query("DELETE FROM transfers WHERE accountId = :accountId")
    suspend fun deleteAllForAccount(accountId: String)

    @Query("SELECT * FROM transfers WHERE state = 'CONFLICT' ORDER BY updatedAtEpochMillis DESC")
    fun observeConflicts(): Flow<List<TransferEntity>>
}

@Dao
interface FolderBackupDao {
    @Query("SELECT * FROM folder_backups WHERE accountId = :accountId ORDER BY mediaType")
    fun observeForAccount(accountId: String): Flow<List<FolderBackupEntity>>

    @Query("SELECT * FROM folder_backups WHERE enabled = 1")
    suspend fun findEnabled(): List<FolderBackupEntity>

    @Query("SELECT * FROM folder_backups WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): FolderBackupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(configuration: FolderBackupEntity)

    @Query("DELETE FROM folder_backups WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM folder_backups WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

class FileBrowserStore(
    private val database: FileBrowserDatabase,
) {
    private val spaces = database.spaceDao()
    private val resources = database.resourceDao()
    private val transfers = database.transferDao()
    private val backups = database.folderBackupDao()

    suspend fun saveAccount(
        account: Account,
        capabilities: ServerCapabilities,
        oidcConfiguration: eu.opencloud.android.next.core.model.auth.OidcConfiguration? = null,
    ) = database.accountDao().upsert(
        AccountEntity(
            id = account.id,
            serverUrl = account.serverUrl,
            userId = account.userId,
            displayName = account.displayName,
            authenticationType = account.authenticationType.name,
            tusSupported = capabilities.tusSupported,
            oidcIssuer = oidcConfiguration?.issuer,
            oidcTokenEndpoint = oidcConfiguration?.tokenEndpoint,
            remoteSearchUrl = capabilities.remoteSearchUrl,
            trashSupported = capabilities.trashSupported,
        ),
    )

    fun observeAccounts(): Flow<List<AccountEntity>> = database.accountDao().observeActive()

    fun observeFavorites(accountId: String): Flow<List<ResourceEntity>> = resources.observeFavorites(accountId)

    fun observeSpaces(accountId: String): Flow<List<SpaceEntity>> = spaces.observeSpaces(accountId)

    fun observeChildren(
        accountId: String,
        spaceId: String,
        parentId: String?,
    ): Flow<List<ResourceEntity>> = resources.observeChildren(accountId, spaceId, parentId)

    fun searchResources(
        accountId: String,
        query: String,
    ): Flow<List<ResourceEntity>> = resources.search(accountId, query.toLikePattern())

    fun observeTransfers(accountId: String): Flow<List<TransferEntity>> = transfers.observeForAccount(accountId)

    fun observeBackups(accountId: String): Flow<List<FolderBackupEntity>> = backups.observeForAccount(accountId)

    suspend fun account(accountId: String): AccountEntity? = database.accountDao().findById(accountId)

    suspend fun space(
        accountId: String,
        spaceId: String,
    ): SpaceEntity? = spaces.findById(accountId, spaceId)

    suspend fun resource(
        accountId: String,
        spaceId: String,
        resourceId: String,
    ): ResourceEntity? = resources.findById(accountId, spaceId, resourceId)

    suspend fun createTransfer(transfer: TransferEntity) = transfers.insert(transfer)

    suspend fun updateTransfer(transfer: TransferEntity) = transfers.update(transfer)

    suspend fun activeAccounts(): List<AccountEntity> = database.accountDao().findActive()

    suspend fun setFavorite(
        resource: ResourceEntity,
        favorite: Boolean,
    ) = resources.setFavorite(resource.accountId, resource.spaceId, resource.remoteId, favorite)

    suspend fun removeAccount(accountId: String) {
        database.withTransaction {
            backups.deleteForAccount(accountId)
            transfers.deleteAllForAccount(accountId)
            resources.deleteForAccount(accountId)
            spaces.deleteForAccount(accountId)
            database.accountDao().delete(accountId)
        }
    }

    suspend fun spaces(accountId: String): List<SpaceEntity> = spaces.findSpaces(accountId)

    suspend fun children(
        accountId: String,
        spaceId: String,
        parentId: String?,
    ): List<ResourceEntity> = resources.findChildren(accountId, spaceId, parentId)

    suspend fun resources(
        accountId: String,
        spaceId: String,
    ): List<ResourceEntity> = resources.findBySpace(accountId, spaceId)

    suspend fun transfer(id: String): TransferEntity? = transfers.findById(id)

    suspend fun pendingTransfers(): List<TransferEntity> = transfers.findPending()

    suspend fun activeTransfers(accountId: String): List<TransferEntity> = transfers.findForAccount(accountId)

    suspend fun activeDownload(
        accountId: String,
        spaceId: String,
        resourceId: String,
    ): TransferEntity? = transfers.findActiveDownload(accountId, spaceId, resourceId)

    suspend fun activeUpload(
        accountId: String,
        spaceId: String,
        sourceUri: String,
        destinationPath: String,
    ): TransferEntity? = transfers.findActiveUpload(accountId, spaceId, sourceUri, destinationPath)

    suspend fun updateLocalCopy(
        accountId: String,
        spaceId: String,
        resourceId: String,
        localPath: String,
        offlinePinned: Boolean,
    ) = resources.updateLocalCopy(accountId, spaceId, resourceId, true, localPath, offlinePinned)

    suspend fun completeUpload(
        transfer: TransferEntity,
        eTag: String?,
    ) {
        val parent = transfer.destinationPath.substringBeforeLast('/', "").ifBlank { null }
        val parentResource = resources.findBySpace(transfer.accountId, transfer.spaceId).find { it.path == parent }
        val now = System.currentTimeMillis()
        resources.upsert(
            ResourceEntity(
                accountId = transfer.accountId,
                spaceId = transfer.spaceId,
                remoteId = transfer.resourceId ?: "uploaded-${transfer.id}",
                parentId = parentResource?.remoteId,
                path = transfer.destinationPath,
                name = transfer.displayName,
                kind = ResourceKind.FILE,
                mimeType = transfer.mimeType,
                sizeBytes = transfer.bytesTotal,
                eTag = eTag,
                modifiedAtEpochMillis = now,
                createdAtEpochMillis = now,
            ),
        )
    }

    suspend fun clearTransferHistory(accountId: String) = transfers.deleteHistory(accountId)

    suspend fun clearTransfers(accountId: String) = transfers.deleteAllForAccount(accountId)

    suspend fun enabledBackups(): List<FolderBackupEntity> = backups.findEnabled()

    suspend fun backup(id: String): FolderBackupEntity? = backups.findById(id)

    suspend fun saveBackup(configuration: FolderBackupEntity) = backups.upsert(configuration)

    suspend fun deleteBackup(id: String) = backups.delete(id)

    suspend fun setOfflinePinned(
        resource: ResourceEntity,
        pinned: Boolean,
    ) = resources.setOfflinePinned(resource.accountId, resource.spaceId, resource.remoteId, pinned)

    suspend fun offlinePinnedResources(): List<ResourceEntity> = resources.findOfflinePinned()

    suspend fun replaceRemoteSpaces(
        accountId: String,
        snapshot: List<SpaceEntity>,
    ) {
        database.withTransaction {
            val incomingIds = snapshot.mapTo(mutableSetOf()) { it.driveId }
            spaces.findSpaces(accountId).filterNot { it.driveId in incomingIds }.forEach { stale ->
                resources.deleteAll(accountId, stale.driveId)
                spaces.delete(stale)
            }
            spaces.upsertAll(snapshot)
        }
    }

    suspend fun replaceFolderSnapshot(
        accountId: String,
        spaceId: String,
        parentId: String?,
        snapshot: List<ResourceEntity>,
    ) {
        database.withTransaction {
            val existing = resources.findChildren(accountId, spaceId, parentId)
            val incomingIds = snapshot.mapTo(mutableSetOf()) { it.remoteId }
            existing.filterNot { it.remoteId in incomingIds }.forEach { stale ->
                if (stale.kind ==
                    ResourceKind.FOLDER
                ) {
                    resources.deleteDescendants(accountId, spaceId, "${stale.path}/%")
                }
                resources.delete(stale)
            }
            snapshot.forEach { remote ->
                val local = resources.findById(accountId, spaceId, remote.remoteId)
                resources.upsert(
                    remote.copy(
                        hasLocalCopy = local?.hasLocalCopy ?: false,
                        localPath = local?.localPath,
                        offlinePinned = local?.offlinePinned ?: false,
                        isFavorite = remote.isFavorite,
                    ),
                )
            }
        }
    }

    suspend fun createSpace(
        accountId: String,
        name: String,
    ) {
        val normalizedName = name.trim().requireValidName()
        val localId = newLocalId()
        spaces.insert(
            SpaceEntity(
                accountId = accountId,
                driveId = localId,
                name = normalizedName,
                type = "project",
                description = null,
                ownerName = null,
                rootId = "$localId-root",
                rootWebDavUrl = null,
                rootETag = null,
                quotaBytes = null,
            ),
        )
    }

    @Transaction
    suspend fun createFolder(
        accountId: String,
        spaceId: String,
        parentId: String?,
        name: String,
    ) {
        val normalizedName = name.trim().requireValidName()
        val parent = parentId?.let { resources.findById(accountId, spaceId, it) }
        resources.insert(
            newResource(
                accountId = accountId,
                spaceId = spaceId,
                parentId = parentId,
                path = parent.childPath(normalizedName),
                name = normalizedName,
            ),
        )
    }

    @Transaction
    suspend fun rename(
        accountId: String,
        spaceId: String,
        resourceId: String,
        name: String,
    ) {
        val resource = requireNotNull(resources.findById(accountId, spaceId, resourceId))
        val normalizedName = name.trim().requireValidName()
        val parent = resource.parentId?.let { resources.findById(accountId, spaceId, it) }
        resources.update(
            resource.copy(
                name = normalizedName,
                path = parent.childPath(normalizedName),
                modifiedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Transaction
    suspend fun move(
        accountId: String,
        spaceId: String,
        resourceId: String,
        targetParentId: String?,
    ) {
        val resource = requireNotNull(resources.findById(accountId, spaceId, resourceId))
        require(resource.remoteId != targetParentId) { "A folder cannot contain itself." }
        val target = targetParentId?.let { resources.findById(accountId, spaceId, it) }
        require(target == null || target.kind == ResourceKind.FOLDER) { "Choose a folder as the destination." }
        resources.update(
            resource.copy(
                parentId = targetParentId,
                path = target.childPath(resource.name),
                modifiedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Transaction
    suspend fun copy(
        accountId: String,
        spaceId: String,
        resourceId: String,
        targetParentId: String?,
    ) {
        val resource = requireNotNull(resources.findById(accountId, spaceId, resourceId))
        val target = targetParentId?.let { resources.findById(accountId, spaceId, it) }
        val copyName = "${resource.name} copy"
        resources.insert(
            resource.copy(
                remoteId = newLocalId(),
                parentId = targetParentId,
                path = target.childPath(copyName),
                name = copyName,
                createdAtEpochMillis = System.currentTimeMillis(),
                modifiedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    @Transaction
    suspend fun delete(
        accountId: String,
        spaceId: String,
        resourceId: String,
    ) {
        resources.findById(accountId, spaceId, resourceId)?.let { resource ->
            if (resource.kind == ResourceKind.FOLDER) {
                resources.deleteDescendants(accountId, spaceId, "${resource.path}/%")
            }
            resources.delete(resource)
        }
    }

    private fun newResource(
        accountId: String,
        spaceId: String,
        parentId: String?,
        path: String,
        name: String,
    ) = ResourceEntity(
        accountId,
        spaceId,
        newLocalId(),
        parentId,
        path,
        name,
        ResourceKind.FOLDER,
        null,
        0,
        null,
        System.currentTimeMillis(),
        System.currentTimeMillis(),
    )
}

private fun ResourceEntity?.childPath(name: String): String = "${this?.path?.trimEnd('/') ?: ""}/$name"

private fun String.requireValidName(): String {
    require(isNotBlank() && '/' !in this) { "Enter a valid name without a slash." }
    return this
}

private fun newLocalId(): String = "local-${java.util.UUID.randomUUID()}"

internal fun String.toLikePattern(): String = "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
