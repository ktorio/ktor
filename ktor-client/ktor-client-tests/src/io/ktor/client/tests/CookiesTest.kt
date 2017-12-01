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
import org.junit.*
import org.junit.Assert.*


open class CookiesTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
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
        }
    }

    @Test
    fun testAccept() {
        val client = HttpClient(factory) {
            install(HttpCookies)
        }

        runBlocking { client.get<Unit>(port = serverPort) }

        client.cookies("localhost").let {
            assertEquals(1, it.size)
            assertEquals("my-awesome-value", it["hello-cookie"]!!.value)
        }

        client.close()
    }

    @Test
    fun testUpdate() = runBlocking {
        val client = HttpClient(factory) {
            install(HttpCookies) {
                default {
                    set("localhost", Cookie("id", "1"))
                }
            }
        }

        repeat(10) {
            val before = client.getId()
            client.get<Unit>(path = "/update-user-id", port = serverPort)
            assertEquals(before + 1, client.getId())
            assertEquals("ktor", client.cookies("localhost")["user"]?.value)
        }

        client.close()
    }

    @Test
    fun testConstant() {
        val client = HttpClient(factory) {
            install(HttpCookies) {
                storage = ConstantCookieStorage(Cookie("id", "1"))
            }
        }

        fun check() {
            assertEquals(1, client.getId())
            assertEquals(null, client.cookies("localhost")["user"]?.value)
        }

        runBlocking { client.get<Unit>(path = "/update-user-id", port = serverPort) }
        check()

        runBlocking { client.get<Unit>(path = "/update-user-id", port = serverPort) }
        check()

        client.close()
    }

    @Test
    @Ignore
    fun multipleClients() {
        /* a -> b
         * |    |
         * c    d
         */
        val client = HttpClient(factory)
        val a = client.config { install(HttpCookies) { default { set("localhost", Cookie("id", "1")) } } }
        val b = a.config { install(HttpCookies) { default { set("localhost", Cookie("id", "10")) } } }
        val c = a.config { }
        val d = b.config { }

        runBlocking {
            a.get<Unit>(path = "/update-user-id", port = serverPort)
        }

        assertEquals(2, a.getId())
        assertEquals(2, c.getId())
        assertEquals(10, b.getId())
        assertEquals(10, d.getId())

        runBlocking {
            b.get<Unit>(path = "/update-user-id", port = serverPort)
        }

        assertEquals(2, a.getId())
        assertEquals(2, c.getId())
        assertEquals(11, b.getId())
        assertEquals(11, d.getId())

        runBlocking {
            c.get<Unit>(path = "/update-user-id", port = serverPort)
        }

        assertEquals(3, a.getId())
        assertEquals(3, c.getId())
        assertEquals(11, b.getId())
        assertEquals(11, d.getId())

        runBlocking {
            d.get<Unit>(path = "/update-user-id", port = serverPort)
        }

        assertEquals(3, a.getId())
        assertEquals(3, c.getId())
        assertEquals(12, b.getId())
        assertEquals(12, d.getId())

        client.close()
    }

    private fun HttpClient.config(block: HttpClientConfig.() -> Unit): HttpClient = TODO()

    private fun HttpClient.getId() = cookies("localhost")["id"]?.value?.toInt()!!
}
