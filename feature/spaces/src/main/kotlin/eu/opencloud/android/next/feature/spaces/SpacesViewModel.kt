package eu.opencloud.android.next.feature.spaces

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.SpaceEntity
import eu.opencloud.android.next.core.network.OpenCloudError
import eu.opencloud.android.next.core.network.OpenCloudException
import eu.opencloud.android.next.core.network.safeMessage
import eu.opencloud.android.next.core.network.toOpenCloudError
import eu.opencloud.android.next.core.security.AppLock
import eu.opencloud.android.next.core.sync.SpaceManagementManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

class SpacesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val manager = SpaceManagementManager(application, store)
    private val mutableState = MutableStateFlow(SpacesUiState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null
    private var observation: Job? = null
    private var refreshJob: Job? = null

    @Suppress("TooGenericExceptionCaught") // UI boundary uses safe typed errors and preserves cancellation.
    fun refresh(clearError: Boolean = true) {
        val account = accountId ?: return
        if (state.value.busy || refreshJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = if (clearError) null else state.value.error)
        refreshJob =
            viewModelScope.launch {
                try {
                    val spaces = withContext(Dispatchers.IO) { manager.listSpaces(account) }
                    val memberSpaces = withContext(Dispatchers.IO) { store.spaces(account) }
                    mutableState.value =
                        state.value.copy(
                            spaces = spaces,
                            loading = false,
                            browsableIds =
                                memberSpaces
                                    .filterNot { it.isDisabled || it.isDeleted }
                                    .map { it.driveId }
                                    .toSet(),
                        )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportError(error.toOpenCloudError().safeMessage(getApplication()))
                }
            }
    }

    fun load(accountId: String) {
        if (this.accountId == accountId) return
        this.accountId = accountId
        observation?.cancel()
        refreshJob?.cancel()
        mutableState.value = SpacesUiState()
        observation =
            viewModelScope.launch {
                val cached = withContext(Dispatchers.IO) { store.spaces(accountId) }
                mutableState.value =
                    state.value.copy(spaces = cached.filter { it.type.equals("project", true) && !it.isDeleted })
                refresh()
            }
    }

    @Suppress("TooGenericExceptionCaught") // UI boundary uses safe typed errors and preserves cancellation.
    fun perform(
        space: SpaceEntity,
        action: SpaceAction,
        value: String,
    ) {
        val account = accountId ?: return
        if (state.value.busy || state.value.loading || space.accountId != account) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (action) {
                        SpaceAction.RENAME -> manager.update(account, space.driveId, name = value.trim())
                        SpaceAction.SUBTITLE -> manager.update(account, space.driveId, subtitle = value.trim())
                        SpaceAction.QUOTA ->
                            manager.update(
                                account,
                                space.driveId,
                                quotaBytes = requireNotNull(quotaBytes(value)),
                            )
                        SpaceAction.DISABLE -> manager.disable(account, space.driveId)
                        SpaceAction.ENABLE -> manager.enable(account, space.driveId)
                        SpaceAction.DELETE -> {
                            require(value == space.name)
                            manager.permanentlyDelete(account, space.driveId)
                        }
                        else -> Unit
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error.toOpenCloudError().safeMessage(getApplication()))
            } finally {
                mutableState.value = state.value.copy(busy = false)
                refresh(clearError = false)
            }
        }
    }

    suspend fun webUrl(space: SpaceEntity): String {
        val account = accountId
        if (account == null || space.accountId != account || !AppLock(getApplication()).canOpenApp()) {
            throw OpenCloudException(OpenCloudError.AuthenticationRequired)
        }
        val server = withContext(Dispatchers.IO) { requireNotNull(store.account(account)).serverUrl }
        return trustedSpaceWebUrl(server, space.webUrl) ?: throw OpenCloudException(OpenCloudError.Trust)
    }

    fun reportError(message: String) {
        mutableState.value = state.value.copy(loading = false, error = message)
    }

    fun dismissError() {
        mutableState.value = state.value.copy(error = null)
    }
}

internal fun trustedSpaceWebUrl(
    server: String,
    webUrl: String?,
): String? =
    try {
        val base = URI(server)
        val target = URI(webUrl ?: "")

        fun URI.portOrDefault() = if (port == -1) 443 else port
        webUrl?.takeIf {
            base.scheme == "https" &&
                target.scheme == "https" &&
                target.host != null &&
                target.host.equals(base.host, ignoreCase = true) &&
                target.portOrDefault() == base.portOrDefault() &&
                target.userInfo == null &&
                target.fragment == null
        }
    } catch (_: java.net.URISyntaxException) {
        null
    }
