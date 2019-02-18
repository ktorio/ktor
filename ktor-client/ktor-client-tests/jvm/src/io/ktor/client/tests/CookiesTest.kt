package io.ktor.client.tests

import io.ktor.application.*
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
import kotlinx.coroutines.*
import kotlin.test.*


@Suppress("KDocMissingDocumentation")
abstract class CookiesTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val hostname = "http://localhost"

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                val cookie = Cookie("hello-cookie", "my-awesome-value", domain = "localhost")
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
            get("/update-user-id") {
                val id = context.request.cookies["id"]?.toInt() ?: let {
                    context.response.status(HttpStatusCode.Forbidden)
                    context.respondText("Forbidden")
                    return@get
                }

                with(context.response.cookies) {
                    append(Cookie("id", (id + 1).toString(), domain = "localhost", path = "/"))
                    append(Cookie("user", "ktor", domain = "localhost", path = "/"))
                }

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
            get("/withPath") {
                val cookie = Cookie("marker", "value", path = "/withPath/")
                context.response.cookies.append(cookie)
                context.respond("OK")
            }
            get("/withPath/something") {
                val cookies = context.request.cookies
                if (cookies["marker"] == "value") {
                    context.respond("OK")
                } else {
                    context.respond(HttpStatusCode.BadRequest)
                }
            }
            get("/foo") {
                val cookie = Cookie("foo", "bar")
                context.response.cookies.append(cookie)

                call.respond("OK")
            }
            get("/FOO") {
                assertTrue(call.request.cookies.rawCookies.isEmpty())
                call.respond("OK")
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
            client.cookies(hostname).let {
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
                        addCookie(hostname, Cookie("id", "1", domain = "localhost"))
                    }
                }
            }
        }

        test { client ->
            repeat(10) {
                val before = client.getId()
                client.get<Unit>(path = "/update-user-id", port = serverPort)
                assertEquals(before + 1, client.getId())
                assertEquals("ktor", client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testConstant(): Unit = clientTest(factory) {
        config {
            install(HttpCookies) {
                storage = ConstantCookiesStorage(Cookie("id", "1", domain = "localhost"))
            }
        }

        test { client ->
            repeat(3) {
                client.get<Unit>(path = "/update-user-id", port = serverPort)
                assertEquals(1, client.getId())
                assertNull(client.cookies(hostname)["user"]?.value)
            }
        }
    }

    @Test
    fun testMultipleCookies(): Unit = clientTest(factory) {
        config {
            install(HttpCookies) {
                default {
                    runBlocking {
                        addCookie(hostname, Cookie("first", "first-cookie", domain = "localhost"))
                        addCookie(hostname, Cookie("second", "second-cookie", domain = "localhost"))
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
    fun testPath() = clientTest(factory) {
        config {
            install(HttpCookies)
        }

        test { client ->
            assertEquals("OK", client.get<String>(port = serverPort, path = "/withPath"))
            assertEquals("OK", client.get<String>(port = serverPort, path = "/withPath/something"))
        }
    }

    @Test
    fun testWithLeadingDot() = clientTest(factory) {
        config {
            install(HttpCookies)
        }

        test { client ->
            client.get<Unit>("https://m.vk.com")
            assert(client.cookies("https://.vk.com").isNotEmpty())
            assert(client.cookies("https://vk.com").isNotEmpty())
            assert(client.cookies("https://m.vk.com").isNotEmpty())
            assert(client.cookies("https://m.vk.com").isNotEmpty())

            assert(client.cookies("https://google.com").isEmpty())
        }
    }

    @Test
    fun caseSensitive() = clientTest(factory) {
        config {
            install(HttpCookies)
        }

        test { client ->
            try {
                client.get<Unit>(port = serverPort, path = "/foo")
                client.get<Unit>(port = serverPort, path = "/FOO")
            } catch (cause: Throwable) {
                throw cause
            }
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
                default { runBlocking { addCookie(hostname, Cookie("id", "1")) } }
            }
        }
        val b = a.config {
            install(HttpCookies) { default { runBlocking { addCookie(hostname, Cookie("id", "10")) } } }
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

    private suspend fun HttpClient.getId() = cookies(hostname)["id"]!!.value.toInt()
}
