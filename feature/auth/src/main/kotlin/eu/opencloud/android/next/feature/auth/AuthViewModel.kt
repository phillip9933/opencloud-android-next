package eu.opencloud.android.next.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.next.core.database.AccountEntity
import eu.opencloud.android.next.core.database.FileBrowserDatabase
import eu.opencloud.android.next.core.database.FileBrowserStore
import eu.opencloud.android.next.core.model.auth.AuthTokens
import eu.opencloud.android.next.core.model.auth.AuthenticationType
import eu.opencloud.android.next.core.model.auth.OidcConfiguration
import eu.opencloud.android.next.core.network.OpenCloudApi
import eu.opencloud.android.next.core.security.KeystoreCredentialStore
import eu.opencloud.android.next.core.security.TlsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.cancellation.CancellationException

class AuthViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val tlsPolicy = TlsPolicy(application)
    private val fileStore = FileBrowserStore(FileBrowserDatabase.create(application))
    private val credentialStore = KeystoreCredentialStore(application)
    private var repository = repositoryFor("")
    private var discovery: DiscoveryResult? = null
    private var pkce: eu.opencloud.android.next.core.model.auth.PkceRequest? = null

    private val mutableState = MutableStateFlow(AuthUiState(isRestoringSession = true))
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val account =
                fileStore.activeAccounts().firstOrNull { saved ->
                    hasUsablePersistedCredential(saved, credentialStore, System.currentTimeMillis() / 1000)
                }
            mutableState.value =
                mutableState.value.copy(
                    activeAccountId = account?.id,
                    isRestoringSession = false,
                )
        }
    }

    fun discover(serverInput: String) =
        launchAuth {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            val result = repositoryFor(serverInput).discover(serverInput)
            discovery = result
            repository = repositoryFor(result.serverUrl)
            mutableState.value =
                mutableState.value.copy(
                    isLoading = false,
                    serverUrl = result.serverUrl,
                    authenticationMode =
                        if (result.oidcConfiguration ==
                            null
                        ) {
                            AuthenticationMode.BASIC
                        } else {
                            AuthenticationMode.OIDC
                        },
                    oidcConfiguration = result.oidcConfiguration,
                )
        }

    fun loginBasic(
        username: String,
        password: String,
    ) = launchAuth {
        val serverUrl = requireNotNull(discovery).serverUrl
        mutableState.value = mutableState.value.copy(isLoading = true, error = null)
        setSession(repository.loginBasic(serverUrl, username, password))
    }

    fun loginBasicDirect(
        serverUrl: String,
        username: String,
        password: String,
    ) = launchAuth {
        require(serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            "Configure dev.server.url, dev.server.username, and dev.server.password in local.properties."
        }
        repository = repositoryFor(serverUrl)
        mutableState.value = mutableState.value.copy(serverUrl = serverUrl, isLoading = true, error = null)
        setSession(repository.loginBasic(serverUrl, username, password))
    }

    fun beginOidc(): String? {
        val configuration = mutableState.value.oidcConfiguration ?: return null
        return repository.beginPkce(configuration).also { request -> pkce = request }.authorizationUrl
    }

    fun completeOidcCallback(callback: String) =
        launchAuth {
            val callbackUri = android.net.Uri.parse(callback)
            val authorizationError = callbackUri.getQueryParameter("error")
            if (!authorizationError.isNullOrBlank()) {
                val description = callbackUri.getQueryParameter("error_description")
                mutableState.value =
                    mutableState.value.copy(
                        isLoading = false,
                        error = description ?: "The sign-in request was rejected: $authorizationError",
                    )
                return@launchAuth
            }
            val request = pkce
            if (request == null) {
                mutableState.value =
                    mutableState.value.copy(
                        isLoading = false,
                        error = "The sign-in session expired before the callback was received. Please sign in again.",
                    )
                return@launchAuth
            }
            val state = callbackUri.getQueryParameter("state")
            val code = callbackUri.getQueryParameter("code")
            if (state != request.state || code.isNullOrBlank()) {
                mutableState.value =
                    mutableState.value.copy(
                        isLoading = false,
                        error = "The sign-in callback could not be verified.",
                    )
                return@launchAuth
            }
            val configuration = mutableState.value.oidcConfiguration
            val discoveryResult = discovery
            if (configuration == null || discoveryResult == null) {
                mutableState.value =
                    mutableState.value.copy(
                        isLoading = false,
                        error = "The server sign-in configuration is no longer available. Please start again.",
                    )
                return@launchAuth
            }
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)
            setSession(
                repository.completePkce(
                    discoveryResult.serverUrl,
                    configuration,
                    code,
                    request.codeVerifier,
                ),
            )
        }

    fun approveCertificatePin(pin: String) {
        val serverUrl = mutableState.value.serverUrl ?: return
        val host = java.net.URI(serverUrl).host ?: return
        tlsPolicy.approvePin(host, pin)
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun repositoryFor(serverUrl: String): AuthRepository {
        val baseClient = OkHttpClient.Builder().followRedirects(false).build()
        return AuthRepository(
            OpenCloudApi(tlsPolicy.applyTo(baseClient, serverUrl)),
            credentialStore,
        )
    }

    private suspend fun setSession(session: AuthenticatedSession) {
        fileStore.saveAccount(session.account, session.capabilities, session.oidcConfiguration)
        mutableState.value =
            mutableState.value.copy(
                session = session,
                activeAccountId = session.account.id,
                isLoading = false,
                isRestoringSession = false,
            )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Network, protocol, and TLS failures are intentionally mapped to one safe UI error boundary.
                mutableState.value = mutableState.value.copy(isLoading = false, error = exception.toAuthError())
            }
        }
    }
}

data class AuthUiState(
    val serverUrl: String? = null,
    val authenticationMode: AuthenticationMode? = null,
    val oidcConfiguration: OidcConfiguration? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val session: AuthenticatedSession? = null,
    val activeAccountId: String? = null,
    val isRestoringSession: Boolean = false,
)

enum class AuthenticationMode {
    BASIC,
    OIDC,
}

private fun Throwable.toAuthError(): String =
    when {
        this is SSLHandshakeException || cause is SSLHandshakeException || cause is CertificateException ->
            "The server certificate could not be trusted. Review the certificate fingerprint before approving an explicit pin."
        message?.contains("HTTP 401") == true -> "The server rejected these credentials."
        else -> message ?: "Unable to connect to the server."
    }

internal fun hasUsablePersistedCredential(
    account: AccountEntity,
    credentialStore: eu.opencloud.android.next.core.security.CredentialStore,
    nowEpochSeconds: Long,
): Boolean =
    when (account.authenticationType) {
        AuthenticationType.BASIC.name -> credentialStore.readBasicPassword(account.id) != null
        AuthenticationType.OIDC.name -> credentialStore.readTokens(account.id).isUsable(nowEpochSeconds)
        else -> false
    }

private fun AuthTokens?.isUsable(nowEpochSeconds: Long): Boolean =
    this != null && (expiresAtEpochSeconds > nowEpochSeconds || !refreshToken.isNullOrBlank())
