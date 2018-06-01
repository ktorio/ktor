package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import kotlin.test.*


abstract class CookiesTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                val cookie = Cookie("hello-cookie", "my-awesome-value")
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
            get("/update-user-id") {
                val id = context.request.cookies["id"]?.toInt() ?: let {
                    context.response.status(HttpStatusCode.Forbidden)
                    context.respondText("Forbidden")
                    return@get
                }

                context.response.cookies.append(Cookie("id", (id + 1).toString()))
                context.response.cookies.append(Cookie("user", "ktor"))

                context.respond("Done")
            }
            get("/multiple") {
                val cookies = context.request.cookies
                val first = cookies["first"] ?: fail()
                val second = cookies["second"] ?: fail()

                assertEquals("first-cookie", first)
                assertEquals("second-cookie", second)
                context.respond("Multiple done")
            }
        }
    }

    @Test
    fun testAccept(): Unit = clientTest(factory) {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get<Unit>(port = serverPort)
            client.cookies("localhost").let {
                assertEquals(1, it.size)
                assertEquals("my-awesome-value", it["hello-cookie"]!!.value)
            }
        }
    }

    @Test
    fun testUpdate(): Unit = clientTest(factory) {
        config {
            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie("localhost", Cookie("id", "1"))
                    }
                }
            }
        }

        test { client ->
            repeat(10) {
                val before = client.getId()
                client.get<Unit>(path = "/update-user-id", port = serverPort)
                assertEquals(before + 1, client.getId())
                assertEquals("ktor", client.cookies("localhost")["user"]?.value)
            }
        }
    }

    @Test
    fun testConstant(): Unit = clientTest(factory) {
        config {
            install(HttpCookies) {
                storage = ConstantCookieStorage(Cookie("id", "1"))
            }
        }

        test { client ->
            repeat(3) {
                client.get<Unit>(path = "/update-user-id", port = serverPort)
                assertEquals(1, client.getId())
                assertNull(client.cookies("localhost")["user"]?.value)
            }
        }
    }

    @Test
    fun testMultipleCookies(): Unit = clientTest(factory) {
        config {
            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie("localhost", Cookie("first", "first-cookie"))
                        addCookie("localhost", Cookie("second", "second-cookie"))
                    }
                }
            }
        }

        test { client ->
            val response = client.get<String>(port = serverPort, path = "/multiple")
            assertEquals("Multiple done", response)
        }
    }

    @Test
    @Ignore
    fun multipleClients() = runBlocking {
        /* a -> b
         * |    |
         * c    d
         */
        val client = HttpClient(factory)
        val a = client.config {
            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie(
                            "localhost",
                            Cookie("id", "1")
                        )
                    }
                }
            }
        }
        val b = a.config {
            install(HttpCookies) { default { runBlocking { addCookie("localhost", Cookie("id", "10")) } } }
        }

        val c = a.config { }
        val d = b.config { }

        a.get<Unit>(path = "/update-user-id", port = serverPort)

        assertEquals(2, a.getId())
        assertEquals(2, c.getId())
        assertEquals(10, b.getId())
        assertEquals(10, d.getId())

        b.get<Unit>(path = "/update-user-id", port = serverPort)

        assertEquals(2, a.getId())
        assertEquals(2, c.getId())
        assertEquals(11, b.getId())
        assertEquals(11, d.getId())

        c.get<Unit>(path = "/update-user-id", port = serverPort)

        assertEquals(3, a.getId())
        assertEquals(3, c.getId())
        assertEquals(11, b.getId())
        assertEquals(11, d.getId())

        d.get<Unit>(path = "/update-user-id", port = serverPort)

        assertEquals(3, a.getId())
        assertEquals(3, c.getId())
        assertEquals(12, b.getId())
        assertEquals(12, d.getId())

        client.close()
    }

    private fun HttpClient.config(block: HttpClientConfig.() -> Unit): HttpClient = TODO("$block")

    private suspend fun HttpClient.getId() = cookies("localhost")["id"]?.value?.toInt()!!
}
