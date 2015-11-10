package org.jetbrains.ktor.tests.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.time.*
import java.util.concurrent.*
import kotlin.test.*

class OAuth1aSignatureTest {
    @Test
    fun testSignatureBaseString() {
        val header = HttpAuthHeader.Parameterized("OAuth", mapOf(
                "oauth_consumer_key" to "1CV4Ud1ZOOzRMwmRyCEe0PY7J",
                "oauth_nonce" to "e685449bf73912c1ebb57220a2158380",
                "oauth_signature_method" to "HMAC-SHA1",
                "oauth_timestamp" to "1447068216",
                "oauth_version" to "1.0"
        ))

        val baseString = signatureBaseString(header, HttpMethod.Post, "https://api.twitter.com/oauth/request_token", emptyList())

        assertEquals("POST&https%3A%2F%2Fapi.twitter.com%2Foauth%2Frequest_token&oauth_consumer_key%3D1CV4Ud1ZOOzRMwmRyCEe0PY7J%26oauth_nonce%3De685449bf73912c1ebb57220a2158380%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D1447068216%26oauth_version%3D1.0", baseString)
    }

    @Test
    fun testSignatureBaseStringWithCallback() {
        val header = HttpAuthHeader.Parameterized("OAuth", mapOf(
                "oauth_callback" to "http://localhost/sign-in-with-twitter/",
                "oauth_consumer_key" to "1CV4Ud1ZOOzRMwmRyCEe0PY7J",
                "oauth_nonce" to "2f085f69a50e55ea6f1bd4e2b3907448",
                "oauth_signature_method" to "HMAC-SHA1",
                "oauth_timestamp" to "1447072553",
                "oauth_version" to "1.0"
        ))

        val baseString = signatureBaseString(header, HttpMethod.Post, "https://api.twitter.com/oauth/request_token", emptyList())

        assertEquals("POST&https%3A%2F%2Fapi.twitter.com%2Foauth%2Frequest_token&oauth_callback%3Dhttp%253A%252F%252Flocalhost%252Fsign-in-with-twitter%252F%26oauth_consumer_key%3D1CV4Ud1ZOOzRMwmRyCEe0PY7J%26oauth_nonce%3D2f085f69a50e55ea6f1bd4e2b3907448%26oauth_signature_method%3DHMAC-SHA1%26oauth_timestamp%3D1447072553%26oauth_version%3D1.0", baseString)
    }
}

class OAuth1aFlowTest {
    val testClient = createOAuthServer(object: TestingOAuthServer {

        override fun requestToken(ctx: ApplicationRequestContext, callback: String?, consumerKey: String, nonce: String, signature: String, signatureMethod: String, timestamp: Long): TestOAuthTokenResponse {
            if (consumerKey != "1CV4Ud1ZOOzRMwmRyCEe0PY7J") {
                throw IllegalArgumentException("Bad consumer key specified: $consumerKey")
            }
            if (signatureMethod != "HMAC-SHA1") {
                throw IllegalArgumentException("Bad signature method specified: $signatureMethod")
            }
            val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            if (Math.abs(now - timestamp) > 10000) {
                throw IllegalArgumentException("timestamp is too old: $timestamp (now $now)")
            }

            return TestOAuthTokenResponse(callback == "http://localhost/login?redirected=true", "token1", "tokenSecret1")
        }

        override fun authorize(ctx: ApplicationRequestContext, oauthToken: String): ApplicationRequestStatus {
            if (oauthToken != "token1") {
                return ctx.response.sendRedirect("http://localhost/login?redirected=true&error=Wrong+token+$oauthToken")
            }

            return ctx.response.sendRedirect("http://localhost/login?redirected=true&oauth_token=$oauthToken&oauth_verifier=verifier1")
        }

        override fun accessToken(ctx: ApplicationRequestContext, consumerKey: String, nonce: String, signature: String, signatureMethod: String, timestamp: Long, token: String, verifier: String): OAuthAccessTokenResponse.OAuth1a {
            if (consumerKey != "1CV4Ud1ZOOzRMwmRyCEe0PY7J") {
                throw IllegalArgumentException("Bad consumer key specified $consumerKey")
            }
            if (signatureMethod != "HMAC-SHA1") {
                throw IllegalArgumentException("Bad signature method specified: $signatureMethod")
            }
            val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            if (Math.abs(now - timestamp) > 10000) {
                throw IllegalArgumentException("timestamp is too old: $timestamp (now $now)")
            }
            // NOTE real server should test it but as we don't test the whole workflow in one test we can't do it
//            if (nonce !in knownNonces) {
//                throw IllegalArgumentException("Bad nonce specified: $nonce")
//            }
            if (token != "token1") {
                throw IllegalArgumentException("Wrong token specified: $token")
            }
            if (verifier != "verifier1") {
                throw IllegalArgumentException("Wrong verifier specified: $verifier")
            }

            return OAuthAccessTokenResponse.OAuth1a("temp-token-1", "temp-secret-1", ValuesMap.Empty)
        }
    })

    val exec = Executors.newSingleThreadExecutor()

    val settings = OAuthServerSettings.OAuth1aServerSettings(
            name = "oauth1a",
            requestTokenUrl = "https://login-server-com/oauth/request_token",
            authorizeUrl = "https://login-server-com/oauth/authorize",
            accessTokenUrl = "https://login-server-com/oauth/access_token",

            consumerKey = "1CV4Ud1ZOOzRMwmRyCEe0PY7J",
            consumerSecret = "0xPR3CQaGOilgXCGUt4g6SpBkhti9DOGkWtBCOImNFomedZ3ZU"
    )

    @After
    fun tearDown() {
        exec.shutdownNow()
    }

    @Test
    fun testRequestToken() {
        withTestApplication {
            application.configureServer("http://localhost/login?redirected=true")

            val result = handleRequest(HttpMethod.Get, "/login")
            assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult, "request should be handled asynchronously")

            waitExecutor()

            assertEquals(HttpStatusCode.Found, result.response.status())
            assertEquals("https://login-server-com/oauth/authorize?oauth_token=token1", result.response.headers[HttpHeaders.Location], "Redirect target location is not valid")
        }
    }

    @Test
    fun testRequestTokenWrongConsumerKey() {
        withTestApplication {
            application.configureServer("http://localhost/login?redirected=true", mutateSettings = {
                OAuthServerSettings.OAuth1aServerSettings(name, requestTokenUrl, authorizeUrl, accessTokenUrl, "badConsumerKey", consumerSecret)
            })

            val result = handleRequest(HttpMethod.Get, "/login")
            assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult, "request should be handled asynchronously")

            waitExecutor()

            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("Ho, []", result.response.content)
        }
    }

    @Test
    fun testRequestTokenFailedRedirect() {
        withTestApplication {
            application.configureServer("http://localhost/login")

            val result = handleRequest(HttpMethod.Get, "/login")
            assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult, "request should be handled asynchronously")

            waitExecutor()

            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("Ho, []", result.response.content)
        }
    }

    @Test
    fun testAccessToken() {
        withTestApplication {
            application.configureServer()

            val result = handleRequest(HttpMethod.Get, "/login?redirected=true&oauth_token=token1&oauth_verifier=verifier1")
            assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult, "request should be handled asynchronously")

            waitExecutor()

            assertEquals(HttpStatusCode.OK, result.response.status())
            assertTrue { result.response.content!!.startsWith("Ho, ") }
            assertFalse { result.response.content!!.contains("[]") }
        }
    }

    @Test
    fun testAccessTokenWrongVerifier() {
        withTestApplication {
            application.configureServer()

            val result = handleRequest(HttpMethod.Get, "/login?redirected=true&oauth_token=token1&oauth_verifier=verifier2")
            assertEquals(ApplicationRequestStatus.Asynchronous, result.requestResult, "request should be handled asynchronously")

            waitExecutor()

            assertEquals(HttpStatusCode.OK, result.response.status())
            assertTrue { result.response.content!!.startsWith("Ho, ") }
            assertTrue { result.response.content!!.contains("[]") }
        }
    }

    private fun Application.configureServer(redirectUrl: String = "http://localhost/login?redirected=true", mutateSettings: OAuthServerSettings.OAuth1aServerSettings.() -> OAuthServerSettings.OAuth1aServerSettings = { this }) {
        routing {
            route(HttpMethod.Get, "/login") {
                auth {
                    oauth(testClient, exec, { settings.mutateSettings() }, { settings ->
                        redirectUrl
                    })
                }

                handle {
                    response.sendText("Ho, ${AuthContext.from(this).foundPrincipals}")
                }
            }
        }
    }

    private fun waitExecutor() {
        val latch = CountDownLatch(1)
        exec.submit {
            latch.countDown()
        }
        latch.await(1L, TimeUnit.MINUTES)
    }


}

// NOTICE in fact we can potentially reorganize it to provide API for ktor-users to build their own OAuth servers
//          for now we have it only for the testing purpose

private interface TestingOAuthServer {
    fun requestToken(ctx: ApplicationRequestContext, callback: String?, consumerKey: String, nonce: String, signature: String, signatureMethod: String, timestamp: Long): TestOAuthTokenResponse
    fun authorize(ctx: ApplicationRequestContext, oauthToken: String): ApplicationRequestStatus
    fun accessToken(ctx: ApplicationRequestContext, consumerKey: String, nonce: String, signature: String, signatureMethod: String,
                    timestamp: Long, token: String, verifier: String): OAuthAccessTokenResponse.OAuth1a
}

private fun createOAuthServer(server: TestingOAuthServer): TestingHttpClient {
    var testClient: TestingHttpClient? = null

    withTestApplication {
        testClient = TestingHttpClient(this)

        application.routing {
            post("/oauth/request_token") {
                val authHeader = request.parseAuthorizationHeader() ?: throw IllegalArgumentException("No auth header found")

                assertEquals(AuthScheme.OAuth, authHeader.authScheme, "This is not an OAuth request")
                if (authHeader !is HttpAuthHeader.Parameterized) {
                    throw IllegalStateException("Bad OAuth header supplied: should be parameterized auth header but token68 blob found")
                }

                val callback = authHeader.parameter(HttpAuthHeader.Parameters.OAuthCallback)?.decodeURL()
                val consumerKey = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthConsumerKey)
                val nonce = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthNonce)
                val signatureMethod = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthSignatureMethod)
                val signature = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthSignature)
                val timestamp = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthTimestamp).toLong()
                val version = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthVersion)

                assertEquals("1.0", version)

                val rr = server.requestToken(this, callback, consumerKey, nonce, signature, signatureMethod, timestamp)

                response.status(HttpStatusCode.OK)
                response.sendText(ContentType.Application.FormUrlEncoded,
                        listOf(
                                HttpAuthHeader.Parameters.OAuthToken to rr.token,
                                HttpAuthHeader.Parameters.OAuthTokenSecret to rr.tokenSecret,
                                HttpAuthHeader.Parameters.OAuthCallbackConfirmed to rr.callbackConfirmed
                        ).formUrlEncode()
                )
            }
            post("/oauth/access_token") {
                val authHeader = request.parseAuthorizationHeader() ?: throw IllegalArgumentException("No auth header found")
                assertEquals(AuthScheme.OAuth, authHeader.authScheme, "This is not an OAuth request")
                if (authHeader !is HttpAuthHeader.Parameterized) {
                    throw IllegalStateException("Bad OAuth header supplied: should be parameterized auth header but token68 blob found")
                }

                val consumerKey = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthConsumerKey)
                val nonce = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthNonce)
                val signature = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthSignature)
                val signatureMethod = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthSignatureMethod)
                val timestamp = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthTimestamp).toLong()
                val token = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthToken)
                val version = authHeader.requireParameter(HttpAuthHeader.Parameters.OAuthVersion)

                if (version != "1.0") {
                    throw IllegalArgumentException("Only version 1.0 is supported")
                }

                if (!request.contentType().match(ContentType.Application.FormUrlEncoded)) {
                    throw IllegalArgumentException("content type should be ${ContentType.Application.FormUrlEncoded}")
                }
                val verifier = request.parameter(HttpAuthHeader.Parameters.OAuthVerifier) ?: throw IllegalArgumentException("oauth_verified is not provided in the POST request body")

                val tokenPair = server.accessToken(this, consumerKey, nonce, signature, signatureMethod, timestamp, token, verifier)

                response.status(HttpStatusCode.OK)
                response.sendText(ContentType.Application.FormUrlEncoded, (listOf(
                        HttpAuthHeader.Parameters.OAuthToken to tokenPair.token,
                        HttpAuthHeader.Parameters.OAuthTokenSecret to tokenPair.tokenSecret
                ) + tokenPair.extraParameters.flattenEntries()).formUrlEncode())
            }
            post("/oauth/authorize") {
                val oauthToken = request.parameter(HttpAuthHeader.Parameters.OAuthToken) ?: throw IllegalArgumentException("No oauth_token parameter specified")
                server.authorize(this, oauthToken)
            }
        }
    }

    return testClient!!
}

private fun HttpAuthHeader.Parameterized.requireParameter(name: String) = parameter(name)?.decodeURL() ?: throw IllegalArgumentException("No $name parameter specified in OAuth header")

data class TestOAuthTokenResponse(val callbackConfirmed: Boolean, val token: String, val tokenSecret: String)