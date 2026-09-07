package eu.opencloud.android.next.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudColor
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

@Composable
@Suppress("CyclomaticComplexMethod", "LongParameterList")
fun AuthScreen(
    state: AuthUiState,
    devLogin: DevLoginConfiguration?,
    onDiscover: (String) -> Unit,
    onBasicLogin: (String, String) -> Unit,
    onDevLogin: (DevLoginConfiguration) -> Unit,
    onBeginOidc: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(OpenCloudDimensions.SpacingXl),
        verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingMd),
    ) {
        Text("Connect to OpenCloud", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter your OpenCloud server address to discover its sign-in method.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Server address") },
            singleLine = true,
        )
        Button(onClick = { onDiscover(serverUrl) }, enabled = serverUrl.isNotBlank() && !state.isLoading) {
            Text("Continue")
        }
        devLogin?.let { configuration ->
            Button(
                onClick = { onDevLogin(configuration) },
                enabled = configuration.isConfigured && !state.isLoading,
            ) {
                Text("Dev Login")
            }
            if (!configuration.isConfigured) {
                Text(
                    "Configure dev.server.url, dev.server.username, and dev.server.password in local.properties.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.serverUrl?.let { Text("Server: $it", style = MaterialTheme.typography.bodySmall) }
        when (state.authenticationMode) {
            AuthenticationMode.BASIC -> {
                OutlinedTextField(value = username, onValueChange = {
                    username = it
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") })
                OutlinedTextField(value = password, onValueChange = {
                    password = it
                }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") })
                Button(
                    onClick = { onBasicLogin(username, password) },
                    enabled =
                        username.isNotBlank() && password.isNotBlank() && !state.isLoading,
                ) {
                    Text("Sign in")
                }
            }
            AuthenticationMode.OIDC ->
                Button(
                    onClick = onBeginOidc,
                    enabled = !state.isLoading,
                ) { Text("Sign in in browser") }
            null -> Unit
        }
        if (state.isLoading) CircularProgressIndicator()
        state.error?.let { message ->
            Card {
                Text(
                    message,
                    modifier = Modifier.padding(OpenCloudDimensions.SpacingMd),
                    color = OpenCloudColor.Error,
                )
            }
        }
        state.session?.let { session ->
            Card {
                Column(
                    modifier = Modifier.padding(OpenCloudDimensions.SpacingMd),
                    verticalArrangement = Arrangement.spacedBy(OpenCloudDimensions.SpacingXxs),
                ) {
                    Text("Signed in as ${session.profile.displayName}", style = MaterialTheme.typography.titleMedium)
                    Text("Server version: ${session.capabilities.version ?: "Unknown"}")
                    Text("Sharing: ${if (session.capabilities.sharingEnabled) "available" else "unavailable"}")
                }
            }
        }
    }
}

data class DevLoginConfiguration(
    val serverUrl: String,
    val username: String,
    val password: String,
) {
    val isConfigured: Boolean = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}
