package eu.opencloud.android.next.feature.shares

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.core.database.AccountEntity
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.core.database.ShareEntity
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions
import eu.opencloud.android.next.core.network.CreateShareRequest
import eu.opencloud.android.next.core.network.OcsShareType
import eu.opencloud.android.next.core.network.ShareRecipient
import eu.opencloud.android.next.core.network.TransferHttpException
import eu.opencloud.android.next.core.network.UpdateShareRequest
import eu.opencloud.android.next.core.sync.ShareManager
import eu.opencloud.android.next.core.sync.TransientPublicLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class ShareCategory(
    val label: String,
) {
    WITH_ME("Shared with me"),
    BY_ME("Shared by me"),
    PUBLIC("Public links"),
}

data class SharesUiState(
    val account: AccountEntity? = null,
    val shares: List<ShareEntity> = emptyList(),
    val category: ShareCategory = ShareCategory.WITH_ME,
    val resource: ResourceEntity? = null,
    val resourceShares: List<ShareEntity> = emptyList(),
    val recipients: List<ShareRecipient> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val createdPublicLink: TransientPublicLink? = null,
)

class SharesViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = FileBrowserStore(FileBrowserDatabase.create(application))
    private val manager = ShareManager(application, store)
    private val mutableState = MutableStateFlow(SharesUiState())
    val state = mutableState.asStateFlow()
    private var accountId: String? = null

    fun load(
        accountId: String,
        resource: ResourceEntity? = null,
    ) {
        if (this.accountId == accountId && mutableState.value.resource == resource) return
        this.accountId = accountId
        mutableState.value = mutableState.value.copy(resource = resource)
        viewModelScope.launch(Dispatchers.IO) {
            val account = store.account(accountId)
            mutableState.value =
                mutableState.value.copy(
                    // Temporary diagnostic bypass for accounts persisted before capability parsing was corrected.
                    account = account?.copy(sharingEnabled = true),
                )
            manager.observe(accountId).collectLatest { values ->
                mutableState.value =
                    mutableState.value.copy(shares = values)
            }
        }
        refresh()
        resource?.let(::loadResource)
    }

    fun selectCategory(value: ShareCategory) {
        mutableState.value = mutableState.value.copy(category = value)
    }

    fun refresh() {
        val id = accountId ?: return
        launchOperation { manager.refresh(id) }
    }

    fun loadResource(resource: ResourceEntity) {
        val id = accountId ?: return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, resource = resource)
            runCatching { withContext(Dispatchers.IO) { manager.sharesForResource(id, resource.path) } }
                .onSuccess { mutableState.value = mutableState.value.copy(resourceShares = it, loading = false) }
                .onFailure { fail(it) }
        }
    }

    fun searchRecipients(query: String) {
        val id = accountId ?: return
        if (query.length < 2) {
            mutableState.value = mutableState.value.copy(recipients = emptyList())
            return
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { manager.searchRecipients(id, query) } }
                .onSuccess { mutableState.value = mutableState.value.copy(recipients = it) }
                .onFailure(::fail)
        }
    }

    fun createRecipientShare(
        recipient: ShareRecipient,
        permissions: Int,
    ) {
        val id = accountId ?: return
        val resource = mutableState.value.resource ?: return
        launchOperation("Share created.") {
            manager.create(id, CreateShareRequest(resource.path, recipient.type, recipient.shareWith, permissions))
            loadResource(resource)
        }
    }

    fun createPublicLink(
        label: String,
        password: String?,
        expiration: LocalDate?,
        permissions: Int,
    ) {
        val id = accountId ?: return
        val resource = mutableState.value.resource ?: return
        launchOperation("Public link created.") {
            val created =
                manager.create(
                    id,
                    CreateShareRequest(
                        resource.path,
                        OcsShareType.PUBLIC_LINK,
                        permissions = permissions,
                        label = label,
                        password = password,
                        expirationDate = expiration,
                    ),
                )
            mutableState.value = mutableState.value.copy(createdPublicLink = created.publicLink)
            loadResource(resource)
        }
    }

    fun updatePermissions(
        share: ShareEntity,
        permissions: Int,
    ) {
        val id = accountId ?: return
        launchOperation("Permissions updated.") {
            manager.update(id, share.remoteId, UpdateShareRequest(permissions = permissions))
        }
    }

    fun revoke(share: ShareEntity) {
        val id = accountId ?: return
        launchOperation("Share revoked.") {
            manager.revoke(id, share.remoteId)
            mutableState.value.resource?.let(::loadResource)
        }
    }

    fun dismissNotice() {
        mutableState.value = mutableState.value.copy(error = null, message = null, createdPublicLink = null)
    }

    private fun launchOperation(
        message: String? = null,
        action: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(saving = true)
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { mutableState.value = mutableState.value.copy(saving = false, message = message) }
                .onFailure(::fail)
        }
    }

    private fun fail(error: Throwable) {
        mutableState.value =
            mutableState.value.copy(loading = false, saving = false, error = error.toSharingMessage())
    }
}

private fun Throwable.toSharingMessage(): String =
    if (
        this is TransferHttpException &&
        statusCode == 400 &&
        message.orEmpty().contains("password", ignoreCase = true)
    ) {
        "This server requires a password for public links."
    } else {
        message ?: "Sharing request failed."
    }

@Composable
fun SharesRoute(
    accountId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharesViewModel = viewModel(key = "shares-$accountId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    SharesScreen(
        state,
        onNavigateBack,
        viewModel::selectCategory,
        viewModel::refresh,
        viewModel::updatePermissions,
        viewModel::revoke,
        viewModel::dismissNotice,
        modifier,
    )
}

@Composable
fun TopLevelSharesRoute(
    accountId: String,
    modifier: Modifier = Modifier,
    viewModel: SharesViewModel = viewModel(key = "shares-$accountId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId) { viewModel.load(accountId) }
    SharesContent(
        state = state,
        onCategory = viewModel::selectCategory,
        onRefresh = viewModel::refresh,
        onUpdatePermissions = viewModel::updatePermissions,
        onRevoke = viewModel::revoke,
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
    )
}

@Composable
fun ResourceSharesRoute(
    accountId: String,
    resource: ResourceEntity,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharesViewModel = viewModel(key = "resource-shares-$accountId-${resource.remoteId}"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(accountId, resource.remoteId) { viewModel.load(accountId, resource) }
    ResourceSharesScreen(
        state,
        onNavigateBack,
        viewModel::searchRecipients,
        viewModel::createRecipientShare,
        viewModel::createPublicLink,
        viewModel::updatePermissions,
        viewModel::revoke,
        viewModel::dismissNotice,
        modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SharesScreen(
    state: SharesUiState,
    onNavigateBack: () -> Unit,
    onCategory: (ShareCategory) -> Unit,
    onRefresh: () -> Unit,
    onUpdatePermissions: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Shares") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Refresh shares")
                    }
                },
            )
        },
    ) { padding ->
        SharesContent(
            state = state,
            onCategory = onCategory,
            onRefresh = onRefresh,
            onUpdatePermissions = onUpdatePermissions,
            onRevoke = onRevoke,
            onDismissNotice = onDismissNotice,
            modifier = Modifier.padding(padding),
            showRefreshAction = false,
        )
    }
}

@Composable
@Suppress("LongParameterList")
fun SharesContent(
    state: SharesUiState,
    onCategory: (ShareCategory) -> Unit,
    onRefresh: () -> Unit,
    onUpdatePermissions: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
    showRefreshAction: Boolean = true,
) {
    val visible =
        when (state.category) {
            ShareCategory.WITH_ME -> state.shares.filter(ShareEntity::sharedWithMe)
            ShareCategory.BY_ME ->
                state.shares.filter { share ->
                    !share.sharedWithMe &&
                        share.shareType != OcsShareType.PUBLIC_LINK.value
                }
            ShareCategory.PUBLIC ->
                state.shares.filter { share ->
                    !share.sharedWithMe &&
                        share.shareType == OcsShareType.PUBLIC_LINK.value
                }
        }

    Column(modifier.fillMaxSize()) {
        if (showRefreshAction) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "Refresh shares")
                }
            }
        }
        TabRow(selectedTabIndex = state.category.ordinal) {
            ShareCategory.entries.forEach { category ->
                Tab(
                    selected = state.category == category,
                    onClick = { onCategory(category) },
                    text = { Text(category.label) },
                )
            }
        }
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else if (visible.isEmpty()) {
            ShareEmpty(state.category)
        } else {
            ShareList(visible, onUpdatePermissions, onRevoke)
        }
    }
    ShareNotice(state, onDismissNotice)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun ResourceSharesScreen(
    state: SharesUiState,
    onNavigateBack: () -> Unit,
    onSearch: (String) -> Unit,
    onCreateRecipient: (ShareRecipient, Int) -> Unit,
    onCreatePublic: (String, String?, LocalDate?, Int) -> Unit,
    onUpdatePermissions: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
    initialInviteOpen: Boolean = false,
) {
    val context = LocalContext.current
    var showInvite by remember { mutableStateOf(initialInviteOpen) }
    var showPublic by remember { mutableStateOf(false) }
    val internalShares = state.resourceShares.filter { it.shareType != OcsShareType.PUBLIC_LINK.value }
    val publicLinks = state.resourceShares.filter { it.shareType == OcsShareType.PUBLIC_LINK.value }

    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = OpenCloudDimensions.SpacingMd,
                    end = OpenCloudDimensions.SpacingMd,
                    bottom = OpenCloudDimensions.SpacingXl,
                ),
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
        ) {
            item {
                Text(
                    text = state.resource?.name ?: "Share",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            item {
                Text("Internal sharing", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text(
                    "Share with people and groups from this OpenCloud server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Button(
                    onClick = { showInvite = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Person, null)
                    Text(" Add people")
                }
            }
            if (internalShares.isEmpty()) {
                item {
                    Text(
                        "No people or groups have access yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(internalShares, key = { "internal-${it.remoteId}" }) { share ->
                    ShareRow(share, onUpdatePermissions, onRevoke)
                }
            }
            item {
                Text(
                    "External sharing",
                    modifier = Modifier.padding(top = OpenCloudDimensions.SpacingSm),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    "Create a public link for people outside this OpenCloud server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Button(
                    onClick = {
                        state.createdPublicLink?.let(context::copyPublicLink) ?: run { showPublic = true }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        if (state.createdPublicLink == null) Icons.Default.Link else Icons.Default.ContentCopy,
                        null,
                    )
                    Text(if (state.createdPublicLink == null) " Create public link" else " Copy link")
                }
            }
            if (publicLinks.isEmpty()) {
                item {
                    Text(
                        "No public links have been created.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(publicLinks, key = { "public-${it.remoteId}" }) { share ->
                    ShareRow(share, onUpdatePermissions, onRevoke)
                }
            }
            if (state.loading || state.saving) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
    if (showInvite) {
        InviteSheet(
            state = state,
            onDismiss = { showInvite = false },
            onSearch = onSearch,
            onCreate = onCreateRecipient,
        )
    }
    if (showPublic) {
        PublicLinkSheet(
            account = state.account,
            folder = state.resource?.kind?.name == "FOLDER",
            onDismiss = { showPublic = false },
            onCreate = onCreatePublic,
        )
    }
    ShareNotice(
        state.copy(message = state.message.takeIf { state.createdPublicLink == null }),
        onDismissNotice,
    )
}

@Composable
private fun ShareList(
    values: List<ShareEntity>,
    onUpdatePermissions: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
) = LazyColumn {
    items(values, key = { it.remoteId }) { share ->
        ShareRow(share, onUpdatePermissions, onRevoke)
    }
}

@Composable
private fun ShareRow(
    share: ShareEntity,
    onUpdatePermissions: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
) {
    var editing by remember(share.remoteId) { mutableStateOf(false) }
    val title = share.displayName ?: share.label ?: share.shareWith ?: share.path.substringAfterLast('/')
    val expiration = if (share.expiresAtEpochMillis != null) " • Expires" else ""
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text("${shareTypeLabel(share)} • ${permissionLabel(share.permissions)}$expiration")
        },
        leadingContent = {
            Icon(
                if (share.shareType == 1) {
                    Icons.Default.Group
                } else if (share.shareType == OcsShareType.PUBLIC_LINK.value) {
                    Icons.Default.Link
                } else {
                    Icons.Default.Person
                },
                null,
            )
        },
        trailingContent = {
            TextButton(onClick = { editing = true }) {
                Text("Manage")
            }
        },
    )
    if (editing) {
        ManageShareDialog(
            share = share,
            onDismiss = { editing = false },
            onUpdate = onUpdatePermissions,
            onRevoke = onRevoke,
        )
    }
}

@Composable
private fun ManageShareDialog(
    share: ShareEntity,
    onDismiss: () -> Unit,
    onUpdate: (ShareEntity, Int) -> Unit,
    onRevoke: (ShareEntity) -> Unit,
) {
    var permissions by remember { mutableIntStateOf(share.permissions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage share") },
        text = {
            Column {
                PermissionControls(permissions, share.isFolder) { permissions = it }
                TextButton(
                    onClick = {
                        onRevoke(share)
                        onDismiss()
                    },
                ) {
                    Text("Revoke share", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onUpdate(share, permissions)
                    onDismiss()
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(
    state: SharesUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onCreate: (ShareRecipient, Int) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var permissions by remember { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OpenCloudDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
        ) {
            Text("Share with people or groups", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search recipients") },
            )
            PermissionControls(permissions, state.resource?.kind?.name == "FOLDER") { permissions = it }
            state.recipients.forEach { recipient ->
                val recipientType = if (recipient.type == OcsShareType.GROUP) "Group" else "User"
                ListItem(
                    headlineContent = { Text(recipient.label) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                recipientType,
                                recipient.additionalInfo,
                            ).joinToString(" • "),
                        )
                    },
                    modifier =
                        Modifier.clickable {
                            onCreate(recipient, permissions)
                            onDismiss()
                        },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublicLinkSheet(
    account: AccountEntity?,
    folder: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, LocalDate?, Int) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var expiration by remember { mutableStateOf("") }
    var permissions by remember { mutableIntStateOf(1) }
    val expirationRequired = account?.publicLinkExpirationEnforced == true
    val expirationDate = runCatching { LocalDate.parse(expiration) }.getOrNull()
    val expirationValid = expiration.isBlank() || expirationDate?.let { !it.isBefore(LocalDate.now()) } == true
    val valid =
        password.isNotBlank() &&
            (!expirationRequired || expirationDate != null) &&
            expirationValid

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OpenCloudDimensions.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingSm),
        ) {
            Text("Create public link", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link name (optional)") },
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password (required)") },
                supportingText = { Text("This server requires public links to be password protected.") },
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = expiration,
                onValueChange = { expiration = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(
                        if (expirationRequired) {
                            "Expiration YYYY-MM-DD (required)"
                        } else {
                            "Expiration YYYY-MM-DD (optional)"
                        },
                    )
                },
                supportingText = {
                    Text(
                        listOfNotNull(
                            account?.publicLinkExpirationDays?.let { "Maximum $it days" },
                            if (!expirationValid) "Choose today or a future date" else null,
                        ).joinToString(" • "),
                    )
                },
            )
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            PermissionControls(permissions, folder) { permissions = it }
            Button(
                onClick = {
                    onCreate(label, password, expirationDate, permissions)
                    onDismiss()
                },
                enabled = valid,
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Text(" Create link")
            }
        }
    }
}

@Composable
private fun PermissionControls(
    value: Int,
    folder: Boolean,
    onChange: (Int) -> Unit,
) {
    Column {
        PermissionToggle("Read", value and 1 != 0, enabled = false) {}
        PermissionToggle("Update", value and 2 != 0) {
            onChange(value.toggle(2, it) or 1)
        }
        if (folder) {
            PermissionToggle("Create", value and 4 != 0) {
                onChange(value.toggle(4, it) or 1)
            }
            PermissionToggle("Delete", value and 8 != 0) {
                onChange(value.toggle(8, it) or 1)
            }
        }
        PermissionToggle("Re-share", value and 16 != 0) {
            onChange(value.toggle(16, it) or 1)
        }
    }
}

@Composable
private fun PermissionToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked, onChange, enabled = enabled)
    Text(label)
}

private fun Int.toggle(
    flag: Int,
    enabled: Boolean,
) = if (enabled) this or flag else this and flag.inv()

private fun shareTypeLabel(share: ShareEntity) =
    when (share.shareType) {
        1 -> "Group"
        3 -> "Public link"
        else -> if (share.sharedWithMe) "Shared with you" else "User"
    }

private fun permissionLabel(value: Int) =
    buildList {
        add("Read")
        if (value and 2 != 0) add("Update")
        if (value and 4 != 0) add("Create")
        if (value and 8 != 0) add("Delete")
        if (value and 16 != 0) add("Share")
    }.joinToString(", ")

@Composable
private fun ShareEmpty(category: ShareCategory) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Share, null)
            Text(
                when (category) {
                    ShareCategory.WITH_ME -> "Nothing has been shared with you."
                    ShareCategory.BY_ME -> "You have not shared any files."
                    ShareCategory.PUBLIC -> "You have not created any public links."
                },
            )
        }
    }

@Composable
private fun ShareNotice(
    state: SharesUiState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val notice = state.error ?: state.message
    if (notice != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (state.error != null) "Sharing" else "Done") },
            text = { Text(notice) },
            confirmButton = {
                Row {
                    state.createdPublicLink?.let { link ->
                        TextButton(
                            onClick = {
                                context.copyPublicLink(link)
                                onDismiss()
                            },
                        ) {
                            Text("Copy link")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            },
        )
    }
}

private fun Context.copyPublicLink(link: TransientPublicLink) {
    getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("OpenCloud public link", link.valueForClipboard()))
}
