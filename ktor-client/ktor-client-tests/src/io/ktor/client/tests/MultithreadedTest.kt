package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Assert.*
import java.net.*
import java.util.concurrent.atomic.*


abstract class MultithreadedTest(factory: HttpClientBackendFactory) : TestWithKtor(factory) {
    private val DEFAULT_SIZE = 100_000
    private val counter: AtomicInteger = AtomicInteger()

    override val server: ApplicationEngine = embeddedServer(Jetty, port) {
        routing {
            get("/") {
                call.respondText(counter.incrementAndGet().toString())
            }
        }
    }

    @Test
    fun numberTest() = runBlocking {
        val client = createClient()

        val result = List(DEFAULT_SIZE) {
            async {
                return@async client.get<String>("http://127.0.0.1:$port").toInt()
            }
        }.map {
            it.await()
        }.toSet().size

        assertEquals(DEFAULT_SIZE, result)
        assertEquals(DEFAULT_SIZE, counter.get())
        client.close()
    }
}