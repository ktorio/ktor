package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import org.junit.Assert.*

open class FollowRedirectsTest(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    override val server: ApplicationEngine = embeddedServer(Jetty, port) {
        routing {
            get("/") {
                call.respondRedirect("/get")
            }
            get("/get") {
                call.respondText("OK")
            }
        }
    }

    @Test
    fun simpleRedirect() {
        val client = createClient()

        runBlocking {
            client.get<HttpResponse>(port = port).use {
                assertEquals(HttpStatusCode.Found, it.status)
            }

            client.get<HttpResponse>(port = port) {
                followRedirects = true
            }.use {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }

        client.close()
    }
}