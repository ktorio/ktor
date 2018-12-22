package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.*
import kotlin.test.*


abstract class HttpRedirectTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
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
                val token = client.feature(HttpCookies)!!.get(it.call.request.url)["Token"]!!
                assertEquals("Hello", token.value)
            }
        }
    }

    @Test
    fun customUrlsTest() = clientTest(factory) {
        val urls = listOf(
            "https://files.forgecdn.net/files/2574/880/BiblioCraft[v2.4.5][MC1.12.2].jar",
            "https://files.forgecdn.net/files/2611/560/Botania r1.10-356.jar",
            "https://files.forgecdn.net/files/2613/730/Toast Control-1.12.2-1.7.1.jar"
        )

        config {
            install(HttpRedirect)
        }

        test { client ->
            urls.forEach { url ->
                client.get<HttpResponse>(url).use {
                    if (it.status.value >= 500) return@use
                    assertTrue(it.status.isSuccess(), url)
                }
            }
        }
    }

    @Test
    fun httpStatsTest() = clientTest(factory) {
        test { client ->
            client.get<HttpResponse>("https://httpstat.us/301").use { response ->
                assertEquals(response.status, HttpStatusCode.OK)
            }
        }
    }
}
