package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class BearerAuthTest {

    @Test
    fun `unauthorized with no auth`() = testApplication {
        configureServer()

        val response = createClient {  }.get("/")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `successful with valid token`() = testApplication {
        configureServer()

        val response = createClient {  }.get("/") {
            withToken("letmein")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("admin", response.bodyAsText())
    }

    @Test
    fun `unauthorized with wrong scheme`() = testApplication {
        configureServer()

        val response = createClient {  }.get("/") {
            header(HttpHeaders.Authorization, "Barer letmein")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unauthorized with wrong token`() = testApplication {
        configureServer()

        val response = createClient {  }.get("/") {
            withToken("opensaysme")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `unauthorized with parameterized header`() = testApplication {
        configureServer()

        val response = createClient { }.get("/") {
            withToken("Token=letmein")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `exception when auth not configured`() = testApplication {
        install(Authentication) {
            bearer { }
        }

        routing {
            authenticate {
                get("/") {
                }
            }
        }

        assertFailsWith<NotImplementedError> {
            createClient {  }.get("/") {
                withToken("letmein")
            }
        }
    }

    private fun HttpRequestBuilder.withToken(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun ApplicationTestBuilder.configureServer(
        authenticate: suspend (BearerTokenCredential) -> Principal? = { token ->
            if (token.token == "letmein") UserIdPrincipal("admin") else null
        }
    ) {
        application {
            install(Authentication) {
                bearer {
                    authenticate {
                        authenticate(it)
                    }
                }
            }

            routing {
                authenticate {
                    route("/") {
                        handle { call.respondText(call.principal<UserIdPrincipal>()?.name ?: "") }
                    }
                }
            }
        }

    }
}
