package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.json.simple.*
import org.junit.*
import org.junit.Test
import java.net.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*

class OAuth2Test {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    private val DefaultSettings = OAuthServerSettings.OAuth2ServerSettings(
            name = "oauth2",
            authorizeUrl = "https://login-server-com/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",
            clientId = "clientId1",
            clientSecret = "clientSecret1"
    )

    private val DefaultSettingsWithScopes = OAuthServerSettings.OAuth2ServerSettings(
            name = "oauth2",
            authorizeUrl = "https://login-server-com/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",
            clientId = "clientId1",
            clientSecret = "clientSecret1",
            defaultScopes = listOf("http://example.com/scope1", "http://example.com/scope2")
    )

    private val DefaultSettingsWithInterceptor = OAuthServerSettings.OAuth2ServerSettings(
            name = "oauth2",
            authorizeUrl = "https://login-server-com/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",
            clientId = "clientId1",
            clientSecret = "clientSecret1",
            authorizeUrlInterceptor = {
                parameters.append("custom", "value1")
            }
    )

    private val DefaultSettingsWithMethodPost = OAuthServerSettings.OAuth2ServerSettings(
            name = "oauth2",
            authorizeUrl = "https://login-server-com/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",
            clientId = "clientId1",
            clientSecret = "clientSecret1",
            requestMethod = HttpMethod.Post
    )

    private val testClient = createOAuth2Server(object : OAuth2Server {
        override fun requestToken(clientId: String, clientSecret: String, grantType: String, state: String?, code: String?, redirectUri: String?, userName: String?, password: String?): OAuthAccessTokenResponse.OAuth2 {
            if (clientId != "clientId1") {
                throw OAuth2Exception.InvalidGrant("Wrong clientId $clientId")
            }
            if (clientSecret != "clientSecret1") {
                throw OAuth2Exception.InvalidGrant("Wrong client secret $clientSecret")
            }
            if (grantType == OAuthGrantTypes.AuthorizationCode) {
                if (state != "state1") {
                    throw OAuth2Exception.InvalidGrant("Wrong state $state")
                }
                if (code != "code1") {
                    throw OAuth2Exception.InvalidGrant("Wrong code $code")
                }
                if (redirectUri != "http://localhost/login") {
                    throw OAuth2Exception.InvalidGrant("Wrong redirect $redirectUri")
                }

                return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
            } else if (grantType == OAuthGrantTypes.Password) {
                if (userName != "user1") {
                    throw OAuth2Exception.InvalidGrant("Wrong username $userName")
                }
                if (password != "password1") {
                    throw OAuth2Exception.InvalidGrant("Wrong password $password")
                }

                return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
            } else {
                throw OAuth2Exception.UnsupportedGrantType(grantType)
            }
        }
    })

    val failures = ArrayList<Throwable>()
    fun Application.module(settings: OAuthServerSettings.OAuth2ServerSettings = DefaultSettings) {
        install(Authentication) {
            oauth("login") {
                client = testClient
                providerLookup = { settings }
                urlProvider = { "http://localhost/login" }
            }
            basic("resource") {
                realm = "oauth2"
                validate {
                    try {
                        verifyWithOAuth2(it, testClient, settings)
                    } catch (ioe: OAuth2Exception) {
                        null
                    }
                }
            }
        }
        routing {
            authenticate("login") {
                get("/login") {
                    val principal = call.authentication.principal as? OAuthAccessTokenResponse.OAuth2
                    call.respondText("Hej, $principal")
                }
            }
            authenticate("resource") {
                get("/resource") {
                    call.respondText("ok")
                }
            }
        }
    }

    @After
    fun tearDown() {
        testClient.close()
        executor.shutdownNow()
    }

    @Test
    fun testRedirect() = withTestApplication({ module() }) {
        val result = handleRequest {
            uri = "/login"
        }

        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response"))
        assertEquals("/authorize", url.path)
        assertEquals("login-server-com", url.host)

        val query = parseQueryString(url.query)
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
    }

    @Test
    fun testRedirectWithScopes() = withTestApplication({ module(DefaultSettingsWithScopes) }) {
        val result = handleRequest {
            uri = "/login"
        }

        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response"))
        assertEquals("/authorize", url.path)
        assertEquals("login-server-com", url.host)

        val query = parseQueryString(url.query)
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
        assertEquals("http://example.com/scope1 http://example.com/scope2", query[OAuth2RequestParameters.Scope])
    }

    @Test
    fun testRedirectCustomizedByInterceptor() = withTestApplication({ module(DefaultSettingsWithInterceptor) }) {
        val result = handleRequest {
            uri = "/login"
        }

        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response"))
        assertEquals("/authorize", url.path)
        assertEquals("login-server-com", url.host)

        val query = parseQueryString(url.query)
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
        assertEquals("value1", query["custom"])
    }

    @Test
    fun testRequestToken() = withTestApplication({ module() }) {
        val result = handleRequest {
            uri = "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        }

        waitExecutor()

        assertTrue(result.requestHandled, "request should be handled")
        assertEquals(HttpStatusCode.OK, result.response.status())
    }

    @Test
    fun testRequestTokenMethodPost() = withTestApplication({ module(DefaultSettingsWithMethodPost) }) {
        val result = handleRequest {
            uri = "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        }

        waitExecutor()

        assertTrue(result.requestHandled, "request should be handled")
        assertEquals(HttpStatusCode.OK, result.response.status())
    }

    @Test
    fun testRequestTokenBadCode() = withTestApplication({ module() }) {
        val call = handleRequest {
            uri = "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code2",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        }

        waitExecutor()

        assertTrue(call.requestHandled, "request should be handled")
        assertEquals(HttpStatusCode.Found, call.response.status())
        assertNotNull(call.response.headers[HttpHeaders.Location])
        assertTrue { call.response.headers[HttpHeaders.Location]!!.startsWith("https://login-server-com/authorize") }
    }

    @Test
    fun testRedirectLowLevel() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthRespondRedirect(testClient, dispatcher, DefaultSettings, "http://localhost/login")
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login")
            assertTrue(result.requestHandled, "request should not be handled asynchronously")

            assertEquals(HttpStatusCode.Found, result.response.status())
            val redirectUrl = URI.create(result.response.headers[HttpHeaders.Location]
                    ?: fail("Redirect uri is missing"))
            assertEquals("login-server-com", redirectUrl.host)
            assertEquals("/authorize", redirectUrl.path)
            val redirectParameters = redirectUrl.rawQuery?.parseUrlEncodedParameters() ?: fail("no redirect parameters")

            assertEquals("clientId1", redirectParameters[OAuth2RequestParameters.ClientId])
            assertEquals("code", redirectParameters[OAuth2RequestParameters.ResponseType])
            assertNotNull(redirectParameters[OAuth2RequestParameters.State])
            assertEquals("http://localhost/login", redirectParameters[OAuth2RequestParameters.RedirectUri])
        }
    }

    @Test
    fun testRequestTokenLowLevel() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/") { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode())

            waitExecutor()

            assertTrue(result.requestHandled, "request should be handled")
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertTrue { result.response.content!!.startsWith("Ho, ") }
            assertTrue { result.response.content!!.contains("OAuth2") }
        }
    }

    @Test
    fun testRequestTokenLowLevelBadCode() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/") { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code2",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode())

            waitExecutor()

            assertTrue(result.requestHandled, "request should be handled")
            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelErrorRedirect() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/") { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?error=failed")
            assertTrue(result.requestHandled, "request should not be handled asynchronously")

            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelBadContentType() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/", {
                        url.parameters["badContentType"] = "true"
                    }) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode())

            waitExecutor()

            assertTrue(result.requestHandled, "request should be handled")
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertTrue { result.response.content!!.startsWith("Ho, ") }
            assertTrue { result.response.content!!.contains("OAuth2") }
        }
    }

    @Test
    fun testRequestTokenLowLevelBadStatus() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/", {
                        url.parameters["respondHttpStatus"] = "500"
                    }) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?" + listOf(
                OAuth2RequestParameters.Code to "code1",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode())

            waitExecutor()

            assertTrue(result.requestHandled, "request should be handled")
            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelBadStatusNotFound() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(testClient, dispatcher, DefaultSettings, "http://localhost/login", "/", {
                        url.parameters["respondHttpStatus"] = "404"
                    }) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?" + listOf(
                OAuth2RequestParameters.Code to "code1",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode())

            waitExecutor()

            assertTrue(result.requestHandled, "request should be handled")
            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testResourceOwnerPasswordCredentials() = withTestApplication({ module() }) {
        handleRequestWithBasic("/resource", "user", "pass").let { result ->
            waitExecutor()
            assertWWWAuthenticateHeaderExist(result)
        }

        handleRequestWithBasic("/resource", "user1", "password1").let { result ->
            waitExecutor()
            assertFailures()
            assertEquals("ok", result.response.content)
        }

    }

    private fun waitExecutor() {
        val latch = CountDownLatch(1)
        executor.submit {
            latch.countDown()
        }
        latch.await(1L, TimeUnit.MINUTES)
    }

    private fun assertFailures() {
        failures.forEach {
            throw it
        }
    }
}

private fun TestApplicationEngine.handleRequestWithBasic(url: String, user: String, pass: String) =
        handleRequest {
            uri = url

            val up = "$user:$pass"
            val encoded = encodeBase64(up.toByteArray(Charsets.ISO_8859_1))
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
        }

private fun assertWWWAuthenticateHeaderExist(response: ApplicationCall) {
    assertNotNull(response.response.headers[HttpHeaders.WWWAuthenticate])
    val header = parseAuthorizationHeader(response.response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

    assertEquals(AuthScheme.Basic, header.authScheme)
    assertEquals("oauth2", header.parameter(HttpAuthHeader.Parameters.Realm))
}

@Deprecated("", ReplaceWith("OAuth2Exception.InvalidGrant(message)", "io.ktor.auth.OAuth2Exception"))
private fun InvalidOAuthCredentials(message: String) = OAuth2Exception.InvalidGrant(message)

private interface OAuth2Server {
    fun requestToken(clientId: String, clientSecret: String, grantType: String, state: String?, code: String?, redirectUri: String?, userName: String?, password: String?): OAuthAccessTokenResponse.OAuth2
}

private fun createOAuth2Server(server: OAuth2Server): HttpClient {
    val environment = createTestEnvironment {
        module {
            routing {
                route("/oauth/access_token") {
                    handle {
                        val formData = call.receiveOrNull() ?: Parameters.Empty
                        val values = call.parameters + formData

                        val clientId = values.requireParameter(OAuth2RequestParameters.ClientId)
                        val clientSecret = values.requireParameter(OAuth2RequestParameters.ClientSecret)
                        val grantType = values.requireParameter(OAuth2RequestParameters.GrantType)
                        val state = values[OAuth2RequestParameters.State]
                        val code = values[OAuth2RequestParameters.Code]
                        val redirectUri = values[OAuth2RequestParameters.RedirectUri]
                        val username = values[OAuth2RequestParameters.UserName]
                        val password = values[OAuth2RequestParameters.Password]
                        val badContentType = values["badContentType"] == "true"
                        val respondStatus = values["respondHttpStatus"]

                        val obj = try {
                            val tokens = server.requestToken(
                                clientId,
                                clientSecret,
                                grantType,
                                state,
                                code,
                                redirectUri,
                                username,
                                password
                            )

                            JSONObject().apply {
                                put(OAuth2ResponseParameters.AccessToken, tokens.accessToken)
                                put(OAuth2ResponseParameters.TokenType, tokens.tokenType)
                                put(OAuth2ResponseParameters.ExpiresIn, tokens.expiresIn)
                                put(OAuth2ResponseParameters.RefreshToken, tokens.refreshToken)
                                for (extraParam in tokens.extraParameters.flattenEntries()) {
                                    put(extraParam.first, extraParam.second)
                                }
                            }
                        } catch (cause: OAuth2Exception) {
                            JSONObject().apply {
                                put(OAuth2ResponseParameters.Error, cause.errorCode ?: "?")
                                put(OAuth2ResponseParameters.ErrorDescription, cause.message)
                            }
                        } catch (t: Throwable) {
                            JSONObject().apply {
                                put(OAuth2ResponseParameters.Error, 1) // in fact we should provide code here, good enough for testing
                                put(OAuth2ResponseParameters.ErrorDescription, t.message)
                            }
                        }

                        val contentType = when {
                            badContentType -> ContentType.Text.Plain
                            else -> ContentType.Application.Json
                        }

                        val status = respondStatus?.let { HttpStatusCode.fromValue(it.toInt()) } ?: HttpStatusCode.OK
                        call.respondText(obj.toJSONString(), contentType, status)
                    }
                }
            }
        }
    }
    val engine = TestApplicationEngine(environment)
    engine.start()
    return HttpClient(TestHttpClientEngine.config { app = engine })
}

private fun Parameters.requireParameter(name: String) = get(name)
        ?: throw IllegalArgumentException("No parameter $name specified")
