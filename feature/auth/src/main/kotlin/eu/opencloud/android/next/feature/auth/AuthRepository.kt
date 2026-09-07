package eu.opencloud.android.next.feature.auth

import eu.opencloud.android.next.core.model.auth.Account
import eu.opencloud.android.next.core.model.auth.AuthTokens
import eu.opencloud.android.next.core.model.auth.AuthenticationType
import eu.opencloud.android.next.core.model.auth.OPEN_CLOUD_ANDROID_OIDC_CLIENT_ID
import eu.opencloud.android.next.core.model.auth.OidcConfiguration
import eu.opencloud.android.next.core.model.auth.PkceRequest
import eu.opencloud.android.next.core.model.auth.ServerCapabilities
import eu.opencloud.android.next.core.model.auth.UserProfile
import eu.opencloud.android.next.core.network.OpenCloudApi
import eu.opencloud.android.next.core.security.CredentialStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class AuthRepository(
    private val api: OpenCloudApi,
    private val credentialStore: CredentialStore,
) {
    private val refreshMutex = Mutex()

    fun discover(serverInput: String): DiscoveryResult =
        api.discover(serverInput).let { discovery ->
            val metadata = api.webFinger(discovery.canonicalServerUrl)
            val oidc =
                runCatching {
                    api.oidcDiscovery(metadata?.issuer ?: discovery.canonicalServerUrl, metadata)
                }.getOrNull()
            DiscoveryResult(discovery.canonicalServerUrl, oidc)
        }

    fun loginBasic(
        serverUrl: String,
        username: String,
        password: String,
    ): AuthenticatedSession {
        val profile = api.basicProfile(serverUrl, username, password)
        val account =
            Account(
                accountId(serverUrl, profile.id),
                serverUrl,
                profile.id,
                profile.displayName,
                AuthenticationType.BASIC,
            )
        credentialStore.saveBasicPassword(account.id, password)
        val authorization = okhttp3.Credentials.basic(username, password)
        return AuthenticatedSession(account, profile, api.capabilities(serverUrl, authorization))
    }

    fun beginPkce(configuration: OidcConfiguration): PkceRequest {
        val state = randomUrlSafeValue()
        val verifier = randomUrlSafeValue()
        val challenge =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            )
        val authorizationUrl =
            configuration.authorizationEndpoint
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("response_type", "code")
                .addQueryParameter("client_id", OPEN_CLOUD_ANDROID_OIDC_CLIENT_ID)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("scope", configuration.scopes.joinToString(" "))
                .addQueryParameter("state", state)
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .build()
                .toString()
        return PkceRequest(authorizationUrl, state, verifier)
    }

    fun completePkce(
        serverUrl: String,
        configuration: OidcConfiguration,
        code: String,
        verifier: String,
    ): AuthenticatedSession {
        val tokens = api.exchangeCode(configuration, code, REDIRECT_URI, verifier)
        val profile = api.bearerProfile(serverUrl, tokens.accessToken)
        val account =
            Account(
                accountId(serverUrl, profile.id),
                serverUrl,
                profile.id,
                profile.displayName,
                AuthenticationType.OIDC,
            )
        credentialStore.saveTokens(account.id, tokens)
        return AuthenticatedSession(account, profile, api.capabilities(serverUrl, "Bearer ${tokens.accessToken}"))
    }

    suspend fun refreshIfNeeded(
        account: Account,
        configuration: OidcConfiguration,
    ): AuthTokens? =
        refreshMutex.withLock {
            val current = credentialStore.readTokens(account.id) ?: return null
            if (current.expiresAtEpochSeconds >
                (System.currentTimeMillis() / 1000) + REFRESH_SKEW_SECONDS
            ) {
                return current
            }
            val refreshToken = current.refreshToken ?: return null
            api.refresh(configuration, refreshToken).also { refreshed ->
                credentialStore.saveTokens(account.id, refreshed)
            }
        }

    private fun accountId(
        serverUrl: String,
        userId: String,
    ): String = "${serverUrl.lowercase()}#$userId"

    private fun randomUrlSafeValue(): String =
        ByteArray(32).also(SecureRandom()::nextBytes).let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it)
        }

    private companion object {
        const val REDIRECT_URI = "eu.opencloud.android.next://oauth"
        const val REFRESH_SKEW_SECONDS = 60L
    }
}

data class DiscoveryResult(
    val serverUrl: String,
    val oidcConfiguration: OidcConfiguration?,
)

data class AuthenticatedSession(
    val account: Account,
    val profile: UserProfile,
    val capabilities: ServerCapabilities,
)
