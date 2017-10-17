package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.backend.jvm.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import org.junit.*

class FollowRedirectsTest : TestWithKtor() {
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
        val client = HttpClient(ApacheBackend)

        runBlocking {
            client.get<HttpResponse>("http://localhost:8080/").use {
                assert(it.statusCode == HttpStatusCode.Found)
            }

            client.get<HttpResponse>(port = 8080) {
                followRedirects = true
            }.use {
                assert(it.statusCode == HttpStatusCode.OK)
            }
        }

        client.close()
    }
}