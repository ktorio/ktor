package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.backend.jvm.*
import io.ktor.client.features.*
import io.ktor.client.pipeline.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.client.utils.url
import io.ktor.content.CacheControl
import io.ktor.features.*
import io.ktor.host.*
import io.ktor.jetty.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import java.util.concurrent.atomic.*


class CacheTests : TestWithKtor() {
    var counter = AtomicInteger()
    override val server: ApplicationHost = embeddedServer(Jetty, 8080) {
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
                call.withETag(etag.toString()) {
                    call.respondText(counter.get().toString())
                }
            }
        }
    }

    @Test
    fun testDisabled() {
        val client = HttpClient(ApacheBackend).config {
            install(HttpCache)
        }

        val builder = HttpRequestBuilder().apply {
            url(port = 8080)
        }

        runBlocking {
            listOf("nocache", "nostore").forEach {
                builder.url.path = it
                assert(client.get<String>(builder) != client.get<String>(builder))
            }
        }

        client.close()
    }

    @Test
    fun maxAge() {
        val client = HttpClient(ApacheBackend).config {
            install(HttpCache)
        }

        val results = mutableListOf<String>()
        val request = HttpRequestBuilder().apply {
            url(path = "maxAge", port = 8080)
        }

        runBlocking {
            results += client.get<String>(request)
            results += client.get<String>(request)

            Thread.sleep(7 * 1000)

            results += client.get<String>(request)
            results += client.get<String>(request)
        }

        assert(results[0] == results[1])
        assert(results[2] == results[3])
        assert(results[0] != results[2])

        client.close()
    }

}