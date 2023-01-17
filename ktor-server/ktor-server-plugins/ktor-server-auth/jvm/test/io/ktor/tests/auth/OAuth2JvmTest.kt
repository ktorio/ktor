/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.net.*
import java.util.concurrent.*
import kotlin.test.*

@Suppress("DEPRECATION")
class OAuth2JvmTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()

    private val DefaultSettings = OAuthServerSettings.OAuth2ServerSettings(
        name = "oauth2",
        authorizeUrl = "https://login-server-com/authorize",
        accessTokenUrl = "https://login-server-com/oauth/access_token",
        clientId = "clientId1",
        clientSecret = "clientSecret1"
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
                setBody(
                    runBlocking {
                        val query = parseQueryString((body as OutgoingContent).toByteReadPacket().readText())
                        val filtered = ParametersBuilder().apply {
                            appendFiltered(query) { key, _ -> key != "state" }
                        }.build()
                        TextContent(filtered.formUrlEncode(), ContentType.Application.FormUrlEncoded)
                    }
                )
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
                        },
                        state
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
                route("/login") {
                    handle {
                        val principal = call.authentication.principal as? OAuthAccessTokenResponse.OAuth2
                        call.respondText("Hej, $principal")
                    }
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
    fun testRedirectLowLevel() {
        withTestApplication {
            application.routing {
                get("/login") {
                    @Suppress("DEPRECATION_ERROR")
                    oauthRespondRedirect(testClient, dispatcher, DefaultSettings, "http://localhost/login")
                }
            }

            val result = handleRequest(HttpMethod.Get, "/login")

            assertEquals(HttpStatusCode.Found, result.response.status())
            val redirectUrl = Url(
                result.response.headers[HttpHeaders.Location]
                    ?: fail("Redirect uri is missing")
            )
            assertEquals("login-server-com", redirectUrl.host)
            assertEquals("/authorize", redirectUrl.encodedPath)
            val redirectParameters = redirectUrl.parameters

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
                    @Suppress("DEPRECATION_ERROR")
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
                    @Suppress("DEPRECATION_ERROR")
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
                    @Suppress("DEPRECATION_ERROR")
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
    @Suppress("DEPRECATION_ERROR")
    fun testRequestTokenLowLevelBadContentType() {
        withTestApplication {
            application.routing {
                get("/login") {
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
                    @Suppress("DEPRECATION_ERROR")
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
                    @Suppress("DEPRECATION_ERROR")
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
    fun testRemoveStateFromAccessTokenRequest(): Unit = withApplication(createTestEnvironment()) {
        with(application) {
            routing {
                get("/login") {
                    @Suppress("DEPRECATION_ERROR")
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
                    @Suppress("DEPRECATION_ERROR")
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
}
