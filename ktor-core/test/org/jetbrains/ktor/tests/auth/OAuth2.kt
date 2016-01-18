package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.json.simple.*
import org.junit.*
import java.net.*
import java.util.*
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
        override fun requestToken(clientId: String, clientSecret: String, grantType: String, state: String?, code: String?, redirectUri: String?, userName: String?, password: String?): OAuthAccessTokenResponse.OAuth2 {
            if (clientId != "clientId1") {
                throw IllegalArgumentException("Wrong clientId $clientId")
            }
            if (clientSecret != "clientSecret1") {
                throw IllegalArgumentException("Wrong client secret $clientSecret")
            }
            if (grantType == OAuthGrandTypes.AuthorizationCode) {
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
            } else if (grantType == OAuthGrandTypes.Password) {
                if (userName != "user1") {
                    throw IllegalArgumentException("Wrong username $userName")
                }
                if (password != "password1") {
                    throw IllegalArgumentException("Wrong password $password")
                }

                return OAuthAccessTokenResponse.OAuth2("accessToken1", "type", Long.MAX_VALUE, null)
            } else  {
                throw IllegalArgumentException("Wrong grand type $grantType")
            }
        }
    })

    val host = createTestHost()
    val failures = ArrayList<Throwable>()
    init {
        host.application.intercept { next ->
            failures.clear()
            next()
        }
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
            route("/resource") {
                auth {
                    basicAuth()
                    verifyWithOAuth2(testClient, settings)
                    fail {
                        authContext.failures.values.flatMapTo(failures) { it }
                        response.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("oauth2"))
                    }
                }
                handle {
                    response.sendText("ok")
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

        assertEquals(ApplicationCallResult.Handled, result.requestResult)
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

        assertEquals(ApplicationCallResult.Asynchronous, result.requestResult)

        waitExecutor()

        assertEquals(HttpStatusCode.OK, result.response.status())
    }

    @Test
    fun testResourceOwnerPasswordCredentials() {
        host.handleRequestWithBasic("/resource", "user", "pass").let { result ->
            waitExecutor()
            assertWWWAuthenticateHeaderExist(result)
        }

        host.handleRequestWithBasic("/resource", "user1", "password1").let { result ->
            waitExecutor()
            assertFailures()
            assertEquals("ok", result.response.content)
        }

    }

    private fun waitExecutor() {
        val latch = CountDownLatch(1)
        exec.submit {
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

private fun TestApplicationHost.handleRequestWithBasic(url: String, user: String, pass: String) =
        handleRequest {
            uri = url

            val up = "$user:$pass"
            val encoded = encodeBase64(up.toByteArray(Charsets.ISO_8859_1))
            addHeader(HttpHeaders.Authorization, "Basic $encoded")
        }

private fun assertWWWAuthenticateHeaderExist(response: RequestResult) {
    assertNotNull(response.response.headers[HttpHeaders.WWWAuthenticate])
    val header = parseAuthorizationHeader(response.response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized

    assertEquals(AuthScheme.Basic, header.authScheme)
    assertEquals("oauth2", header.parameter(HttpAuthHeader.Parameters.Realm))
}

private interface OAuth2Server {
    fun requestToken(clientId: String, clientSecret: String, grantType: String, state: String?, code: String?, redirectUri: String?, userName: String?, password: String?): OAuthAccessTokenResponse.OAuth2
}

private fun createOAuth2Server(server: OAuth2Server): TestingHttpClient {
    val testApp = createTestHost()
    testApp.application.routing {
        route("/oauth/access_token") {
            handle {
                val clientId = request.requireParameter(OAuth2RequestParameters.ClientId)
                val clientSecret = request.requireParameter(OAuth2RequestParameters.ClientSecret)
                val grantType = request.requireParameter(OAuth2RequestParameters.GrantType)
                val state = request.parameter(OAuth2RequestParameters.State)
                val code = request.parameter(OAuth2RequestParameters.Code)
                val redirectUri = request.parameter(OAuth2RequestParameters.RedirectUri)
                val username = request.parameter(OAuth2RequestParameters.UserName)
                val password = request.parameter(OAuth2RequestParameters.Password)

                try {
                    val tokens = server.requestToken(clientId, clientSecret, grantType, state, code, redirectUri, username, password)

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