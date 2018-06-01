package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.compat.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import kotlin.test.*


open class HttpRedirectTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                call.respondRedirect("/get")
            }
            get("/get") {
                call.respondText("OK")
            }
            get("/infinity") {
                call.respondRedirect("/infinity")
            }
            get("/cookie") {
                val token = call.request.cookies["Token"] ?: run {
                    call.response.cookies.append("Token", "Hello")
                    call.respondRedirect("/cookie")
                    return@get
                }

                assertEquals("Hello", token)

                call.respondText("OK")
            }
        }
    }

    @Test
    fun redirectTest(): Unit = clientTest(factory) {
        config {
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpResponse>(port = serverPort).use {
                assertEquals(HttpStatusCode.OK, it.status)
                assertEquals("OK", it.readText())
            }
        }
    }

    @Test
    fun infinityRedirectTest() = clientTest(factory) {
        config {
            install(HttpRedirect)
        }

        test { client ->
            assertFails {
                runBlocking {
                    client.get<HttpResponse>(path = "/infinity", port = serverPort)
                }
            }
        }
    }

    @Test
    fun redirectWithCookiesTest() = clientTest(factory) {
        config {
            install(HttpCookies)
            install(HttpRedirect)
        }

        test { client ->
            client.get<HttpResponse>(path = "/cookie", port = serverPort).use {
                assertEquals("OK", it.readText())
                val token = client.feature(HttpCookies)!!.get(it.call.request.url.host.toLowerCase(), "Token")!!
                assertEquals("Hello", token.value)
            }
        }
    }
}