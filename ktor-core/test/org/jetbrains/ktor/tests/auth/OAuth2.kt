package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.json.simple.*
import org.junit.*
import java.net.*
import java.util.concurrent.*
import kotlin.test.*

class OAuth2Test {
    val exec = Executors.newSingleThreadExecutor()

    val settings = OAuthServerSettings.OAuth2ServerSettings(
            name = "oauth2",
            authorizeUrl = "https://login-server-com/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",
            clientId = "clientId1",
            clientSecret = "clientSecret1"
    )

    val testClient = createOAuth2Server(object: OAuth2Server {
        override fun requestToken(clientId: String, clientSecret: String, grandType: String, state: String, code: String, redirectUri: String): OAuthAccessTokenResponse.OAuth2 {
            if (clientId != "clientId1") {
                throw IllegalArgumentException("Wrong clientId $clientId")
            }
            if (clientSecret != "clientSecret1") {
                throw IllegalArgumentException("Wrong client secret $clientSecret")
            }
            if (grandType != "authorization_code") {
                throw IllegalArgumentException("Wrong grand type $grandType")
            }
            if (state != "state1") {
                throw IllegalArgumentException("Wrong state $state")
            }
            if (code != "code1") {
                throw IllegalArgumentException("Wrong code $code")
            }
            if (redirectUri != "http://localhost/login") {
                throw IllegalArgumentException("Wrong redirect $redirectUri")
            }

            return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
        }
    })

    val host = createTestHost()
    init {
        host.application.routing {
            route("/login") {
                auth {
                    oauth(testClient, exec, { settings }, { "http://localhost/login" })
                }

                handle {
                    response.status(HttpStatusCode.OK)
                    response.sendText(ContentType.Text.Plain, "Hej, ${authContext.foundPrincipals}")
                }
            }
        }
    }

    @After
    fun tearDown() {
        exec.shutdownNow()
    }

    @Test
    fun testRedirect() {
        val result = host.handleRequest {
            uri = "/login"
        }

        assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
        assertEquals(HttpStatusCode.Found, result.response.status())

        val url = URI(result.response.headers[HttpHeaders.Location] ?: throw IllegalStateException("No location header in the response"))
        assertEquals("/authorize", url.path)
        assertEquals("login-server-com", url.host)

        val query = parseQueryString(url.query)
        assertEquals("clientId1", query[OAuth2RequestParameters.ClientId])
        assertEquals("code", query[OAuth2RequestParameters.ResponseType])
        assertNotNull(query[OAuth2RequestParameters.State])
        assertEquals("http://localhost/login", query[OAuth2RequestParameters.RedirectUri])
    }

    @Test
    fun testRequestToken() {
        val result = host.handleRequest {
            uri = "/login?" + listOf(
                    OAuth2RequestParameters.Code to "code1",
                    OAuth2RequestParameters.State to "state1"
            ).formUrlEncode()
        }

        assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult)

        waitExecutor()

        assertEquals(HttpStatusCode.OK, result.response.status())
    }

    private fun waitExecutor() {
        val latch = CountDownLatch(1)
        exec.submit {
            latch.countDown()
        }
        latch.await(1L, TimeUnit.MINUTES)
    }
}

private interface OAuth2Server {
    fun requestToken(clientId: String, clientSecret: String, grandType: String, state: String, code: String, redirectUri: String): OAuthAccessTokenResponse.OAuth2
}

private fun createOAuth2Server(server: OAuth2Server): TestingHttpClient {
    val testApp = createTestHost()
    testApp.application.routing {
        route("/oauth/access_token") {
            handle {
                val clientId = request.requireParameter(OAuth2RequestParameters.ClientId)
                val clientSecret = request.requireParameter(OAuth2RequestParameters.ClientSecret)
                val grantType = request.requireParameter(OAuth2RequestParameters.GrantType)
                val state = request.requireParameter(OAuth2RequestParameters.State)
                val code = request.requireParameter(OAuth2RequestParameters.Code)
                val redirectUri = request.requireParameter(OAuth2RequestParameters.RedirectUri)

                try {
                    val tokens = server.requestToken(clientId, clientSecret, grantType, state, code, redirectUri)

                    response.status(HttpStatusCode.OK)
                    response.sendText(ContentType.Application.Json, JSONObject().apply {
                        put(OAuth2ResponseParameters.AccessToken, tokens.accessToken)
                        put(OAuth2ResponseParameters.TokenType, tokens.tokenType)
                        put(OAuth2ResponseParameters.ExpiresIn, tokens.expiresIn)
                        put(OAuth2ResponseParameters.RefreshToken, tokens.refreshToken)
                        for (extraParam in tokens.extraParameters.flattenEntries()) {
                            put(extraParam.first, extraParam.second)
                        }
                    }.toJSONString())
                } catch (t: Throwable) {
                    response.status(HttpStatusCode.OK) // ??
                    response.sendText(ContentType.Application.Json, JSONObject().apply {
                        put(OAuth2ResponseParameters.Error, 1) // in fact we should provide code here, good enough for testing
                        put(OAuth2ResponseParameters.ErrorDescription, t.message)
                    }.toJSONString())
                }
            }
        }
    }

    return TestingHttpClient(testApp)
}

private fun ApplicationRequest.requireParameter(name: String) = parameter(name) ?: throw IllegalArgumentException("No parameter $name specified")