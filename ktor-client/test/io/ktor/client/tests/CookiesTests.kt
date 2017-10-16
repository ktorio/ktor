package io.ktor.client.tests

import io.ktor.client.HttpClient
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.client.features.cookies.ConstantCookieStorage
import io.ktor.client.features.cookies.HttpCookies
import io.ktor.client.features.cookies.cookies
import io.ktor.client.get
import io.ktor.client.pipeline.config
import io.ktor.client.tests.utils.TestWithKtor
import io.ktor.host.ApplicationHost
import io.ktor.host.embeddedServer
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.jetty.Jetty
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test


class CookiesTests : TestWithKtor() {
    override val server: ApplicationHost = embeddedServer(Jetty, 8080) {
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

                val cookie = Cookie("id", (id + 1).toString())
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
        }
    }

    @Test
    fun testAccept() {
        val client = HttpClient(ApacheBackend).config {
            install(HttpCookies)
        }

        runBlocking { client.get<Unit>(port = 8080) }

        client.cookies("localhost").let {
            assert(it.size == 1)
            assert(it["hello-cookie"]!!.value == "my-awesome-value")
        }

        client.close()
    }

    @Test
    fun testUpdate() {
        val client = HttpClient(ApacheBackend).config {
            install(HttpCookies) {
                default {
                    set("localhost", Cookie("id", "1"))
                }
            }
        }

        for (i in 1..10) {
            val before = client.getId()
            runBlocking { client.get<Unit>(path = "update-user-id", port = 8080) }
            assert(client.getId() == before + 1)
        }

        client.close()
    }

    @Test
    fun testConstant() {
        val client = HttpClient(ApacheBackend).config {
            install(HttpCookies) {
                storage = ConstantCookieStorage(Cookie("id", "1"))
            }
        }

        runBlocking {
            client.get<Unit>(path = "update-user-id", port = 8080)
        }
        assert(client.getId() == 1)
        runBlocking { client.get<Unit>(path = "update-user-id", port = 8080) }
        assert(client.getId() == 1)
    }

    @Test
    fun multipleClients() {
        /* a -> b
         * |    |
         * c    d
         */
        val client = HttpClient(ApacheBackend)
        val a = client.config { install(HttpCookies) { default { set("localhost", Cookie("id", "1")) } } }
        val b = a.config { install(HttpCookies) { default { set("localhost", Cookie("id", "10")) } } }
        val c = a.config { }
        val d = b.config { }

        runBlocking {
            a.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 2)
        assert(c.getId() == 2)
        assert(b.getId() == 10)
        assert(d.getId() == 10)

        runBlocking {
            b.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 2)
        assert(c.getId() == 2)
        assert(b.getId() == 11)
        assert(d.getId() == 11)

        runBlocking {
            c.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 3)
        assert(c.getId() == 3)
        assert(b.getId() == 11)
        assert(d.getId() == 11)

        runBlocking {
            d.get<Unit>(path = "update-user-id", port = 8080)
        }

        assert(a.getId() == 3)
        assert(c.getId() == 3)
        assert(b.getId() == 12)
        assert(d.getId() == 12)

        client.close()
    }

    private fun HttpClient.getId() = cookies("localhost")["id"]?.value?.toInt()!!
}
