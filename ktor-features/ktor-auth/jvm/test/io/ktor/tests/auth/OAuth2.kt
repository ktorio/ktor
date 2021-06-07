/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.json.simple.*
import java.net.*
import java.util.*
import java.util.concurrent.*
import kotlin.test.*

@Suppress("DEPRECATION")
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

    private fun DefaultSettingsWithAccessTokenInterceptor(post: Boolean) = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1",
        requestMethod = when (post) {
            false -> HttpMethod.Get
            true -> HttpMethod.Post
        },
        accessTokenInterceptor = {
            url.parameters.remove("state")

            if (method == HttpMethod.Post) {
                body = runBlocking {
                    val query = parseQueryString((body as OutgoingContent).toByteReadPacket().readText())
                    val filtered = ParametersBuilder().apply {
                        appendFiltered(query) { key, _ -> key != "state" }
                    }.build()
                    TextContent(filtered.formUrlEncode(), ContentType.Application.FormUrlEncoded)
                }
            }
        }
    )

    private val testClient = createOAuth2Server(
        object : OAuth2Server {
            override fun requestToken(
                clientId: String,
                clientSecret: String,
                grantType: String,
                state: String?,
                code: String?,
                redirectUri: String?,
                userName: String?,
                password: String?
            ): OAuthAccessTokenResponse.OAuth2 {
                if (clientId != "clientId1") {
                    throw OAuth2Exception.InvalidGrant("Wrong clientId $clientId")
                }
                if (clientSecret != "clientSecret1") {
                    throw OAuth2Exception.InvalidGrant("Wrong client secret $clientSecret")
                }
                if (grantType == OAuthGrantTypes.AuthorizationCode) {
                    if (state != "state1" && state != null) {
                        throw OAuth2Exception.InvalidGrant("Wrong state $state")
                    }
                    if (code != "code1" && code != "code2") {
                        throw OAuth2Exception.InvalidGrant("Wrong code $code")
                    }
                    if ((code == "code1" && state == null) || (code == "code2" && state != null)) {
                        throw OAuth2Exception.InvalidGrant("Wrong code $code or state $state")
                    }
                    if (redirectUri != "http://localhost/login") {
                        throw OAuth2Exception.InvalidGrant("Wrong redirect $redirectUri")
                    }
                    if (userName != null || password != null) {
                        throw OAuth2Exception.UnknownException(
                            "User/password shouldn't be specified for authorization_code grant type.",
                            "none"
                        )
                    }

                    return OAuthAccessTokenResponse.OAuth2(
                        "accessToken1",
                        "type",
                        Long.MAX_VALUE,
                        null,
                        when (state) {
                            null -> parametersOf("noState", "Had no state")
                            else -> Parameters.Empty
                        }
                    )
                } else if (grantType == OAuthGrantTypes.Password) {
                    if (userName != "user1") {
                        throw OAuth2Exception.InvalidGrant("Wrong username $userName")
                    }
                    if (password != "password1") {
                        throw OAuth2Exception.InvalidGrant("Wrong password $password")
                    }
                    if (state != null || code != null) {
                        throw OAuth2Exception.UnknownException(
                            "State/code shouldn't be specified for password grant type.",
                            "none"
                        )
                    }

                    return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
                } else {
                    throw OAuth2Exception.UnsupportedGrantType(grantType)
                }
            }
        }
    )

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

    @AfterTest
    fun tearDown() {
        testClient.close()
        executor.shutdownNow()
    }

    @Test
    fun testRedirect() = withTestApplication({ module() }) {
        val result = handleRequest {
            uri = "/login"
        }

        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(
            result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
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

        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(
            result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
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

        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(
            result.response.headers[HttpHeaders.Location]
                ?: throw IllegalStateException("No location header in the response")
        )
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

            assertEquals(HttpStatusCode.Found, result.response.status())
            val redirectUrl = URI.create(
                result.response.headers[HttpHeaders.Location]
                    ?: fail("Redirect uri is missing")
            )
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
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/"
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(
                HttpMethod.Get,
                "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )

            waitExecutor()

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
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/"
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(
                HttpMethod.Get,
                "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code2",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )

            waitExecutor()

            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelErrorRedirect() {
        withTestApplication {
            application.routing {
                get("/login") {
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/"
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login?error=failed")

            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelBadContentType() {
        withTestApplication {
            application.routing {
                get("/login") {
                    @Suppress("DEPRECATION")
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/",
                        { url.parameters["badContentType"] = "true" }
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(
                HttpMethod.Get,
                "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )

            waitExecutor()

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
                    @Suppress("DEPRECATION")
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/",
                        { url.parameters["respondHttpStatus"] = "500" }
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(
                HttpMethod.Get,
                "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )

            waitExecutor()

            assertEquals(HttpStatusCode.Found, result.response.status())
        }
    }

    @Test
    fun testRequestTokenLowLevelBadStatusNotFound() {
        withTestApplication {
            application.routing {
                get("/login") {
                    @Suppress("DEPRECATION")
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettings,
                        "http://localhost/login",
                        "/",
                        { url.parameters["respondHttpStatus"] = "404" }
                    ) { token ->
                        call.respondText("Ho, $token")
                    }
                }
            }

            val result = handleRequest(
                HttpMethod.Get,
                "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
                ).formUrlEncode()
            )

            waitExecutor()

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

    @Test
    fun testParamsInURL(): Unit = withApplication(createTestEnvironment()) {
        application.apply {
            install(Authentication) {
                oauth("login") {
                    client = HttpClient(TestHttpClientEngine.create { app = this@withApplication })
                    urlProvider = { "http://localhost/login" }
                    providerLookup = {
                        OAuthServerSettings.OAuth2ServerSettings(
                            name = "oauth2",
                            authorizeUrl = "http://localhost/authorize",
                            accessTokenUrl = "http://localhost/oauth/access_token",
                            clientId = "clientId1",
                            clientSecret = "clientSecret1",
                            requestMethod = HttpMethod.Post,
                            passParamsInURL = true
                        )
                    }
                }
            }
            routing {
                post("/oauth/access_token") {
                    // If these fail, you will see '401 Unauthorized' in test logs.
                    assertEquals(call.request.queryParameters[OAuth2RequestParameters.Code], "mow", "Code is in URL")
                    assertEquals(call.request.queryParameters[OAuth2RequestParameters.State], "wow", "State is in URL")
                    call.respondText("access_token=a_token", ContentType.Application.FormUrlEncoded)
                }
                authenticate("login") {
                    get("/login") {
                        call.respond("We're in.")
                    }
                }
            }
        }

        handleRequest {
            uri = "/login?code=mow&state=wow"
        }.also {
            // Usually 401 here means, that tests above failed.
            assertEquals(it.response.status(), HttpStatusCode.OK)
            assertEquals(it.response.content, "We're in.")
        }
    }

    @Test
    fun testRemoveStateFromAccessTokenRequest(): Unit = withApplication(createTestEnvironment()) {
        with(application) {
            routing {
                get("/login") {
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettingsWithAccessTokenInterceptor(post = false),
                        "http://localhost/login",
                        "/login"
                    ) { token ->
                        token as OAuthAccessTokenResponse.OAuth2
                        call.respondText(token.extraParameters["noState"] ?: "Had state")
                    }
                }
            }
        }

        val result = handleRequest(
            HttpMethod.Get,
            "/login?" + listOf(
                OAuth2RequestParameters.Code to "code2",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        ).response.content

        assertEquals("Had no state", result)
    }

    @Test
    fun testRemoveStateFromAccessTokenRequestMethodPost(): Unit = withApplication(createTestEnvironment()) {
        with(application) {
            routing {
                get("/login") {
                    oauthHandleCallback(
                        testClient,
                        dispatcher,
                        DefaultSettingsWithAccessTokenInterceptor(post = true),
                        "http://localhost/login",
                        "/login"
                    ) { token ->
                        token as OAuthAccessTokenResponse.OAuth2
                        call.respondText(token.extraParameters["noState"] ?: "Had state")
                    }
                }
            }
        }

        val result = handleRequest(
            HttpMethod.Get,
            "/login?" + listOf(
                OAuth2RequestParameters.Code to "code2",
                OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        ).response.content

        assertEquals("Had no state", result)
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
        val encoded = up.toByteArray(Charsets.ISO_8859_1).encodeBase64()
        addHeader(HttpHeaders.Authorization, "Basic $encoded")
    }

private fun assertWWWAuthenticateHeaderExist(response: ApplicationCall) {
    assertNotNull(response.response.headers[HttpHeaders.WWWAuthenticate])
    val header =
        parseAuthorizationHeader(
            response.response.headers[HttpHeaders.WWWAuthenticate]!!
        ) as HttpAuthHeader.Parameterized

    assertEquals(AuthScheme.Basic, header.authScheme)
    assertEquals("oauth2", header.parameter(HttpAuthHeader.Parameters.Realm))
}

private interface OAuth2Server {
    fun requestToken(
        clientId: String,
        clientSecret: String,
        grantType: String,
        state: String?,
        code: String?,
        redirectUri: String?,
        userName: String?,
        password: String?
    ): OAuthAccessTokenResponse.OAuth2
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
                                put(
                                    OAuth2ResponseParameters.Error,
                                    1
                                ) // in fact we should provide code here, good enough for testing
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
    with(TestApplicationEngine(environment)) {
        start()
        return client.config {
            expectSuccess = false
        }
    }
}

private fun Parameters.requireParameter(name: String) = get(name)
    ?: throw IllegalArgumentException("No parameter $name specified")
