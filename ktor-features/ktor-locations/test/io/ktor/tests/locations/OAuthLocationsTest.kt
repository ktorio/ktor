package io.ktor.tests.locations

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.client.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Test
import java.net.*
import kotlin.test.*

class OAuthLocationsTest {
    @Test
    fun testOAuthAtLocation() = withTestApplication {
        application.install(Locations)

        application.install(Routing) {
            val client = HttpClient(TestHttpClientEngine.config { app = this@withTestApplication })

            authentication {
                oauthAtLocation<A>(client, Unconfined,
                        { OAuthServerSettings.OAuth2ServerSettings("a", "http://oauth-server/auth",
                                "http://oauth-server/token",
                                clientId = "test", clientSecret = "secret")},
                        { _, _ -> B() }
                )
            }

            get<A> {
            }

            get<B> {
            }
        }

        handleRequest(HttpMethod.Get, "/A").let { call ->
            assertEquals(HttpStatusCode.Found, call.response.status())
            val locationHeader = call.response.headers[HttpHeaders.Location]
            assertNotNull(locationHeader)
            assertEquals("/auth", URL(locationHeader).path)
            assertEquals("/B", URL(parseQueryString(URL(locationHeader).query)["redirect_uri"]).path)
        }
    }

    @Test
    fun testOAuthAtLocationString() = withTestApplication {
        application.install(Locations)

        application.install(Routing) {
            val client = HttpClient(TestHttpClientEngine.config { app = this@withTestApplication })

            authentication {
                oauthAtLocation<A>(client, Unconfined,
                        { OAuthServerSettings.OAuth2ServerSettings("a", "http://oauth-server/auth",
                                "http://oauth-server/token",
                                clientId = "test", clientSecret = "secret")},
                        { _, _ -> "http://localhost/B" }
                )
            }

            get<A> {
            }

            get<B> {
            }
        }

        handleRequest(HttpMethod.Get, "/A").let { call ->
            assertEquals(HttpStatusCode.Found, call.response.status())
            val locationHeader = call.response.headers[HttpHeaders.Location]
            assertNotNull(locationHeader)
            assertEquals("/auth", URL(locationHeader).path)
            assertEquals("/B", URL(parseQueryString(URL(locationHeader).query)["redirect_uri"]).path)
        }
    }

    @Location("/A")
    class A

    @Location("/B")
    class B
}