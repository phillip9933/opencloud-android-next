package eu.opencloud.android.next.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.opencloud.android.next.BuildConfig
import eu.opencloud.android.next.feature.auth.AuthScreen
import eu.opencloud.android.next.feature.auth.AuthViewModel
import eu.opencloud.android.next.feature.auth.DevLoginConfiguration
import eu.opencloud.android.next.feature.files.FileBrowserRoute

@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun OpenCloudNextApp(
    oauthCallback: String?,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state = viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(oauthCallback) {
        oauthCallback?.let(viewModel::completeOidcCallback)
    }

    Surface(modifier = modifier.fillMaxSize()) {
        when {
            state.value.isRestoringSession ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.value.activeAccountId != null ->
                FileBrowserRoute(
                    accountId = requireNotNull(state.value.activeAccountId),
                    releaseVersion = BuildConfig.VERSION_NAME,
                )
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
                    onDevLogin = { configuration ->
                        viewModel.loginBasicDirect(
                            configuration.serverUrl,
                            configuration.username,
                            configuration.password,
                        )
                    },
                    onBeginOidc = {
                        viewModel.beginOidc()?.let { url ->
                            CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(url))
                        }
                    },
                )
        }
    }
}
