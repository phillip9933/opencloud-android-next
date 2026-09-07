package eu.opencloud.android.next.core.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenCloudApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OpenCloudApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OpenCloudApi(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `discovery preserves an explicit canonical server scheme`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = api.discover(server.url("/").toString().trimEnd('/'))

        assertEquals(server.url("/").toString().trimEnd('/'), result.canonicalServerUrl)
        assertEquals("/status.php", server.takeRequest().path)
    }

    @Test
    fun `webfinger reads issuer and server supplied client metadata`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"links":[{"rel":"http://opencloud.eu/ns/oidc/issuer","href":"https://issuer.example"}],"properties":{"http://opencloud.eu/ns/oidc/client_id":"mobile-client","http://opencloud.eu/ns/oidc/scopes":["openid","profile"]}}""",
            ),
        )

        val metadata = api.webFinger(server.url("/").toString().trimEnd('/'))

        assertEquals("https://issuer.example", metadata?.issuer)
        assertEquals("mobile-client", metadata?.clientId)
        assertEquals(listOf("openid", "profile"), metadata?.scopes)
    }

    @Test
    fun `basic profile and capabilities send required authorization and OCS headers`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(userResponse()))
        server.enqueue(MockResponse().setResponseCode(200).setBody(capabilitiesResponse()))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val profile = api.basicProfile(baseUrl, "alice", "secret")
        val capabilities = api.capabilities(baseUrl, "Basic YWxpY2U6c2VjcmV0")

        assertEquals("alice", profile.id)
        assertTrue(capabilities.sharingEnabled)
        assertEquals("Basic YWxpY2U6c2VjcmV0", server.takeRequest().getHeader("Authorization"))
        val capabilitiesRequest = server.takeRequest()
        assertEquals("true", capabilitiesRequest.getHeader("OCS-APIREQUEST"))
        assertEquals("/ocs/v2.php/cloud/capabilities?format=json", capabilitiesRequest.path)
    }

    @Test
    fun `token exchange and refresh use their expected grant types`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenResponse("access-1", "refresh-1")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenResponse("access-2", "refresh-2")))
        val configuration =
            eu.opencloud.android.next.core.model.auth.OidcConfiguration(
                issuer = server.url("/").toString(),
                authorizationEndpoint = server.url("authorize").toString(),
                tokenEndpoint = server.url("token").toString(),
                registrationEndpoint = null,
                clientId = "server-advertised-client",
                scopes = listOf("openid"),
            )

        val exchanged = api.exchangeCode(configuration, "code", "eu.opencloud.android.next://oauth", "verifier")
        val refreshed = api.refresh(configuration, "refresh-1")

        assertEquals("access-1", exchanged.accessToken)
        assertEquals("access-2", refreshed.accessToken)
        val authorizationCodeRequest = server.takeRequest().body.readUtf8()
        val refreshRequest = server.takeRequest().body.readUtf8()
        assertTrue(authorizationCodeRequest.contains("grant_type=authorization_code"))
        assertTrue(authorizationCodeRequest.contains("client_id=OpenCloudAndroid"))
        assertTrue(refreshRequest.contains("grant_type=refresh_token"))
        assertTrue(refreshRequest.contains("client_id=OpenCloudAndroid"))
    }

    private fun userResponse() =
        """{"ocs":{"data":{"id":"alice","display-name":"Alice","email":"alice@example.test"}}}"""

    private fun capabilitiesResponse() =
        """{"ocs":{"data":{"version":{"string":"7.4.0"},"capabilities":{"files":{"tus":{"enabled":true}},"files_sharing":{"api_enabled":true,"public":{"enabled":true}},"spaces":{"enabled":true}}}}}"""

    private fun tokenResponse(
        accessToken: String,
        refreshToken: String,
    ) = """{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_in":3600,"token_type":"Bearer"}"""
}
