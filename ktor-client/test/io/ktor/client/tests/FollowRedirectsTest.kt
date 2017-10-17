package io.ktor.client.tests

import io.ktor.client.HttpClient
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.client.get
import io.ktor.client.response.HttpResponse
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.client.utils.OutputStreamBody
import io.ktor.host.ApplicationHost
import io.ktor.host.embeddedServer
import io.ktor.http.HttpStatusCode
import io.ktor.jetty.Jetty
import io.ktor.pipeline.call
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.safeAs
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test

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