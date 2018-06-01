package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.atomic.*
import kotlin.test.*


abstract class CacheTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private var counter = AtomicInteger()
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/reset") {
                counter.set(0)
                call.respondText("")
            }
            get("/nocache") {
                counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoCache(null))
                call.respondText(counter.toString())
            }
            get("/nostore") {
                counter.incrementAndGet()
                call.response.cacheControl(CacheControl.NoStore(null))
                call.respondText(counter.toString())
            }
            get("/maxAge") {
                counter.incrementAndGet()
                call.response.cacheControl(CacheControl.MaxAge(5))
                call.respondText(counter.get().toString())
            }
            get("/etag") {
                val etag = if (counter.get() < 2) "0" else "1"
                counter.incrementAndGet()
                call.withETag(etag) {
                    call.respondText(counter.get().toString())
                }
            }
        }
    }

    @Test
    fun testDisabled() = runBlocking {
        val client = HttpClient(factory) {
//            install(HttpCache)
        }

        val builder = HttpRequestBuilder(port = serverPort)

        listOf("/nocache", "/nostore").forEach {
            builder.url.encodedPath = it
            assertNotEquals(client.get<String>(builder), client.get(builder))
        }

        client.close()
    }

    @Test
    fun maxAge() = runBlocking {
        val client = HttpClient(factory) {
//            install(HttpCache)
        }

        val results = mutableListOf<String>()
        val request = HttpRequestBuilder(path = "/maxAge", port = serverPort)

        results += client.get<String>(request)
        results += client.get<String>(request)

        Thread.sleep(7 * 1000)

        results += client.get<String>(request)
        results += client.get<String>(request)

        assertEquals(results[0], results[1])
        assertEquals(results[2], results[3])
        assertNotEquals(results[0], results[2])

        client.close()
    }

}