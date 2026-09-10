package eu.opencloud.android.next.core.sync

import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.network.LibreGraphSpacesClient
import kotlinx.coroutines.flow.Flow

class SpaceRepository(
    private val store: FileBrowserStore,
    private val remote: LibreGraphSpacesClient,
) {
    fun observe(accountId: String): Flow<List<SpaceEntity>> = store.observeSpaces(accountId)

    suspend fun synchronize(
        accountId: String,
        serverUrl: String,
        authorization: String,
    ): List<SpaceEntity> =
        remote
            .listSpaces(serverUrl, authorization)
            .map { space ->
                SpaceEntity(
                    accountId = accountId,
                    driveId = space.id,
                    name = space.name,
                    type = space.type,
                    description = space.description,
                    ownerName = space.ownerName,
                    rootId = space.rootId,
                    rootWebDavUrl = space.rootWebDavUrl,
                    rootETag = space.rootETag,
                    quotaBytes = space.quotaTotalBytes,
                    isDisabled = space.disabled,
                    isDeleted = space.deleted,
                    driveAlias = space.driveAlias,
                    webUrl = space.webUrl,
                    ownerId = space.ownerId,
                    lastModifiedDateTime = space.lastModifiedDateTime,
                    quotaUsedBytes = space.quotaUsedBytes,
                    quotaRemainingBytes = space.quotaRemainingBytes,
                    quotaState = space.quotaState,
                )
            }.also { store.replaceRemoteSpaces(accountId, it) }
}
