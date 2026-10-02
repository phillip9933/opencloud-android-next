package eu.opencloud.android.next.core.sync

import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.network.LibreGraphSpacesClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpaceRepository(
    private val store: FileBrowserStore,
    private val remote: LibreGraphSpacesClient? = null,
    private val pendingCreations: PendingSpaceCreationStore = InMemoryPendingSpaceCreationStore(),
) {
    fun observe(accountId: String): Flow<List<SpaceEntity>> = store.observeSpaces(accountId)

    fun observeProjectSpaces(accountId: String): Flow<List<SpaceEntity>> =
        observe(accountId).map { spaces ->
            spaces.filter { space ->
                space.type.equals(PROJECT_DRIVE_TYPE, ignoreCase = true) &&
                    !space.isDisabled &&
                    !space.isDeleted
            }
        }

    suspend fun synchronize(
        accountId: String,
        serverUrl: String,
        authorization: String,
    ): List<SpaceEntity> {
        val token = store.beginSnapshot(accountId)
        val discovery =
            requireNotNull(remote) { "The Libre Graph Spaces client is unavailable." }
                .snapshot(serverUrl, authorization)
        return discovery.spaces
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
            }.also {
                if (!store.replaceRemoteSpaces(accountId, it, token, discovery.excludedVaultIds)) {
                    throw eu.opencloud.android.next.core.network.OpenCloudException(
                        eu.opencloud.android.next.core.network.OpenCloudError.PreconditionFailed,
                    )
                }
            }
    }

    suspend fun createProjectSpace(
        accountId: String,
        serverUrl: String,
        authorization: String,
        name: String,
    ): SpaceCreationResult {
        require(name.isNotBlank()) { "Enter a space name." }
        val client = requireNotNull(remote) { "The Libre Graph Spaces client is unavailable." }
        val key = PendingSpaceCreationKey.create(accountId, serverUrl, name)
        val mutex = SpaceCreationLocks.forKey(key)
        return mutex.withLock {
            val pendingId = pendingCreations.get(key)
            val driveId = pendingId ?: client.createProjectSpace(serverUrl, authorization, key.normalizedName)
            if (pendingId == null) {
                try {
                    // Record the acknowledgement before the next suspension point.
                    pendingCreations.put(key, driveId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The server has accepted the create; never report it as safely retryable.
                    return@withLock SpaceCreationResult.AwaitingDiscovery(driveId, retrySafe = false)
                }
            }

            val spaces =
                try {
                    synchronize(accountId, serverUrl, authorization)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withLock SpaceCreationResult.AwaitingDiscovery(driveId)
                }
            val visible =
                spaces.firstOrNull { it.driveId == driveId }
                    ?: return@withLock SpaceCreationResult.AwaitingDiscovery(driveId)
            forgetAcknowledgement(key)
            SpaceCreationResult.Created(visible)
        }
    }

    private fun forgetAcknowledgement(key: PendingSpaceCreationKey) {
        try {
            pendingCreations.remove(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A stale journal entry only causes another GET of the authoritative visible drive.
        }
    }

    private companion object {
        const val PROJECT_DRIVE_TYPE = "project"
    }
}

private object SpaceCreationLocks {
    private val mutexes = Array(64) { Mutex() }

    fun forKey(key: PendingSpaceCreationKey): Mutex = mutexes[(key.hashCode() and Int.MAX_VALUE) % mutexes.size]
}
