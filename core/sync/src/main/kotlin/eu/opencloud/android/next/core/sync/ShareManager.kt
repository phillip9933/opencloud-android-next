package eu.opencloud.android.next.core.sync

import android.content.Context
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ShareEntity
import eu.opencloud.android.next.core.network.CreateShareRequest
import eu.opencloud.android.next.core.network.OcsShareType
import eu.opencloud.android.next.core.network.OcsSharingClient
import eu.opencloud.android.next.core.network.RemoteShare
import eu.opencloud.android.next.core.network.ShareRecipient
import eu.opencloud.android.next.core.network.UpdateShareRequest
import eu.opencloud.android.next.core.security.TlsPolicy
import okhttp3.OkHttpClient

class ShareManager(
    private val context: Context,
    private val store: FileBrowserStore = FileBrowserStore(FileBrowserDatabase.create(context)),
) {
    fun observe(accountId: String) = store.observeShares(accountId)

    suspend fun refresh(accountId: String) {
        val session = session(accountId)
        val owned = session.client.listShares(session.account.serverUrl, session.authorization)
        val received = session.client.listShares(session.account.serverUrl, session.authorization, sharedWithMe = true)
        store.replaceShares(
            accountId,
            owned.map { it.toEntity(accountId, false) } + received.map { it.toEntity(accountId, true) },
        )
    }

    suspend fun sharesForResource(
        accountId: String,
        path: String,
    ): List<ShareEntity> {
        val session = session(accountId)
        return session.client.listShares(session.account.serverUrl, session.authorization, path = path).map {
            it.toEntity(accountId, false)
        }
    }

    suspend fun searchRecipients(
        accountId: String,
        query: String,
    ): List<ShareRecipient> {
        val session = session(accountId)
        return session.client.searchRecipients(session.account.serverUrl, session.authorization, query)
    }

    suspend fun create(
        accountId: String,
        request: CreateShareRequest,
    ): CreatedShare {
        val session = session(accountId)
        if (request.type == OcsShareType.PUBLIC_LINK) {
            require(session.account.publicSharingEnabled) { "Public link sharing is not supported by this server." }
        }
        val remote =
            session.client
                .createShare(session.account.serverUrl, session.authorization, request)
        refresh(accountId)
        return CreatedShare(remote.toEntity(accountId, false), remote.publicUrl?.let(::TransientPublicLink))
    }

    suspend fun update(
        accountId: String,
        shareId: String,
        request: UpdateShareRequest,
    ) {
        val session = session(accountId)
        session.client.updateShare(session.account.serverUrl, session.authorization, shareId, request)
        refresh(accountId)
    }

    suspend fun revoke(
        accountId: String,
        shareId: String,
    ) {
        val session = session(accountId)
        session.client.revokeShare(session.account.serverUrl, session.authorization, shareId)
        store.deleteShare(accountId, shareId)
    }

    private suspend fun session(accountId: String): ShareSession {
        val account = requireNotNull(store.account(accountId)) { "The account is unavailable." }
        require(account.sharingEnabled) { "Sharing is not supported by this server." }
        val authorization = WorkerAuthorizationProvider(context).authorization(account)
        val http = TlsPolicy(context).applyTo(OkHttpClient.Builder().build(), account.serverUrl)
        return ShareSession(account, authorization, OcsSharingClient(http))
    }
}

class CreatedShare(
    val share: ShareEntity,
    val publicLink: TransientPublicLink?,
)

class TransientPublicLink internal constructor(
    private val value: String,
) {
    fun valueForClipboard(): String = value

    override fun toString(): String = "[REDACTED PUBLIC LINK]"
}

private data class ShareSession(
    val account: eu.opencloud.android.next.core.database.AccountEntity,
    val authorization: String,
    val client: OcsSharingClient,
)

private fun RemoteShare.toEntity(
    accountId: String,
    received: Boolean,
) = ShareEntity(
    accountId,
    id,
    resourceId,
    path,
    type.value,
    shareWith,
    displayName,
    additionalInfo,
    permissions,
    sharedAtEpochSeconds,
    expiresAtEpochMillis,
    label,
    isFolder,
    received,
)
