package eu.opencloud.android.next.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.BuildConfig
import eu.opencloud.android.next.core.database.ResourceEntity
import eu.opencloud.android.next.feature.account.AccountRoute
import eu.opencloud.android.next.feature.auth.AuthScreen
import eu.opencloud.android.next.feature.auth.AuthViewModel
import eu.opencloud.android.next.feature.auth.DevLoginConfiguration
import eu.opencloud.android.next.feature.files.BackupSettingsRoute
import eu.opencloud.android.next.feature.files.DeletedFilesRoute
import eu.opencloud.android.next.feature.files.FileBrowserDestinations
import eu.opencloud.android.next.feature.files.FileBrowserRoute
import eu.opencloud.android.next.feature.settings.SettingsRoute
import eu.opencloud.android.next.feature.shares.ResourceSharesRoute
import eu.opencloud.android.next.feature.shares.TopLevelSharesRoute
import eu.opencloud.android.next.feature.spaces.SpacesRoute
import eu.opencloud.android.next.feature.transfers.TransfersRoute

@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod", "FunctionNaming", "ktlint:standard:function-naming")
fun OpenCloudNextApp(
    oauthCallback: String?,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle()
    var destination by rememberSaveable(state.value.activeAccountId) { mutableStateOf(AppDestination.Files) }
    var nextBrowserAddRequest by remember(state.value.activeAccountId) { mutableIntStateOf(0) }
    var pendingBrowserAddRequest by remember(state.value.activeAccountId) { mutableIntStateOf(0) }
    var trashSpaceId by rememberSaveable(state.value.activeAccountId) { mutableStateOf<String?>(null) }
    var shareResource by remember(state.value.activeAccountId) { mutableStateOf<ResourceEntity?>(null) }

    LaunchedEffect(oauthCallback) {
        oauthCallback?.let(viewModel::completeOidcCallback)
    }

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.value.isRestoringSession ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.value.activeAccountId != null -> {
                val accountId = requireNotNull(state.value.activeAccountId)
                when (destination) {
                    AppDestination.Files ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            FileBrowserRoute(
                                accountId = accountId,
                                releaseVersion = BuildConfig.VERSION_NAME,
                                openAddMenuRequest = pendingBrowserAddRequest,
                                onConsumeAddMenuRequest = { pendingBrowserAddRequest = 0 },
                                destinations =
                                    FileBrowserDestinations(
                                        onOpenTransfers = { destination = AppDestination.Transfers },
                                        onOpenDeletedFiles = {
                                            trashSpaceId = null
                                            destination = AppDestination.DeletedFiles
                                        },
                                        onOpenSettings = { destination = AppDestination.Settings },
                                        onOpenAccount = { destination = AppDestination.Account },
                                        onShareResource = { shareResource = it },
                                    ),
                                sharesContent = { padding, onBrowseResource ->
                                    TopLevelSharesRoute(
                                        accountId = accountId,
                                        modifier = Modifier.padding(padding),
                                        onBrowseResource = onBrowseResource,
                                    )
                                },
                                spacesContent = { padding, onOpenSpace ->
                                    SpacesRoute(
                                        accountId = accountId,
                                        onOpenSpace = onOpenSpace,
                                        onOpenTrash = {
                                            trashSpaceId = it
                                            destination = AppDestination.DeletedFiles
                                        },
                                        modifier = Modifier.padding(padding),
                                    )
                                },
                            )
                            shareResource?.let { resource ->
                                ResourceSharesRoute(
                                    accountId = accountId,
                                    resource = resource,
                                    onNavigateBack = { shareResource = null },
                                )
                            }
                        }
                    AppDestination.Transfers ->
                        TransfersRoute(
                            accountId = accountId,
                            onNavigateBack = { destination = AppDestination.Files },
                            onOpenFileActions = {
                                nextBrowserAddRequest += 1
                                pendingBrowserAddRequest = nextBrowserAddRequest
                                destination = AppDestination.Files
                            },
                        )
                    AppDestination.DeletedFiles ->
                        DeletedFilesRoute(
                            accountId = accountId,
                            initialSpaceId = trashSpaceId,
                            onNavigateBack = { destination = AppDestination.Files },
                        )
                    AppDestination.Settings ->
                        SettingsRoute(
                            onNavigateBack = { destination = AppDestination.Files },
                            onOpenBackupSettings = { destination = AppDestination.BackupSettings },
                            onOpenSecurity = { destination = AppDestination.Security },
                        )
                    AppDestination.Security ->
                        eu.opencloud.android.next.feature.settings.SecuritySettingsScreen(onNavigateBack = {
                            destination =
                                AppDestination.Settings
                        })
                    AppDestination.BackupSettings ->
                        BackupSettingsRoute(
                            accountId = accountId,
                            onNavigateBack = { destination = AppDestination.Settings },
                        )
                    AppDestination.Account ->
                        AccountRoute(
                            activeAccountId = accountId,
                            onNavigateBack = { destination = AppDestination.Files },
                            onAccountSelect = {
                                viewModel.switchAccount(it)
                                destination = AppDestination.Files
                            },
                            onAccountRemove = {
                                viewModel.accountRemoved(it)
                                destination = AppDestination.Files
                            },
                            onAddAccount = {
                                viewModel.accountRemoved(null)
                                destination = AppDestination.Files
                            },
                        )
                }
            }
            else ->
                AuthScreen(
                    state = state.value,
                    devLogin =
                        if (BuildConfig.DEBUG) {
                            DevLoginConfiguration(
                                serverUrl = BuildConfig.DEV_SERVER_URL,
                                username = BuildConfig.DEV_SERVER_USERNAME,
                                password = BuildConfig.DEV_SERVER_PASSWORD,
                            )
                        } else {
                            null
                        },
                    onDiscover = viewModel::discover,
                    onBasicLogin = viewModel::loginBasic,
                    onAppTokenLogin = viewModel::loginBasicDirect,
                    onDevLogin = { configuration ->
                        viewModel.loginBasicDirect(
                            configuration.serverUrl,
                            configuration.username,
                            configuration.password,
                        )
                    },
                    onBeginOidc = {
                        viewModel.beginOidc()?.let { url ->
                            CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                        }
                    },
                )
        }
    }
    BackHandler(
        enabled =
            state.value.activeAccountId != null &&
                (
                    shareResource != null ||
                        (destination != AppDestination.Files && destination != AppDestination.Settings)
                ),
    ) {
        if (shareResource != null) {
            shareResource = null
        } else {
            destination =
                if (destination in
                    listOf(AppDestination.BackupSettings, AppDestination.Security)
                ) {
                    AppDestination.Settings
                } else {
                    AppDestination.Files
                }
        }
    }
}

private enum class AppDestination {
    Files,
    DeletedFiles,
    Transfers,
    Settings,
    BackupSettings,
    Security,
    Account,
}
