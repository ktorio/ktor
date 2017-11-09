package io.ktor.client.backend.apache

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

open class FollowRedirectsTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
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
    fun defaultTest() = runBlocking {
        HttpClient(ApacheBackend).use { client ->
            client.get<HttpResponse>(port = serverPort).use {
                assertEquals(HttpStatusCode.Found, it.status)
            }
        }
    }

    @Test
    fun redirectTest() = runBlocking {
        HttpClient(ApacheBackend.config { followRedirects = true }).use { client ->
            client.get<HttpResponse>(port = serverPort).use {
                assertEquals(HttpStatusCode.OK, it.status)
            }
        }
    }
}