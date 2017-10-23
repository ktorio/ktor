package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.host.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*

open class FollowRedirectsTest(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    override val server: ApplicationHost = embeddedServer(Jetty, 8080) {
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
            client.get<HttpResponse>("http://localhost:8080/").use {
                assert(it.status == HttpStatusCode.Found, { "without redirect response: $it"})
            }

            client.get<HttpResponse>(port = 8080) {
                followRedirects = true
            }.use {
                assert(it.status == HttpStatusCode.OK, { "with redirect response: $it"})
            }
        }

        client.close()
    }
}