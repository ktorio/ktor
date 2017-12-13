package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*


abstract class MultithreadedTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val DEFAULT_SIZE = 100_000
    private val MAX_ACTIVE_TASKS = 4
    private val counter: AtomicInteger = AtomicInteger()

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                call.respondText(counter.incrementAndGet().toString())
            }
        }
    }

    @Test
    fun numberTest() = runBlocking {
        val client = HttpClient(factory)
        val pool = Executors.newFixedThreadPool(MAX_ACTIVE_TASKS)

        val result = List(DEFAULT_SIZE) {
            pool.submit(Callable<Int> {
                runBlocking {
                    val response = client.get<HttpResponse>("http://127.0.0.1:$serverPort")
                    val result = response.readText().toInt()
                    response.close()
                    result
                }
            })
        }.map { it.get() }.toSet().size

        pool.shutdown()
        assertEquals(DEFAULT_SIZE, result)
        assertEquals(DEFAULT_SIZE, counter.get())
        assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS))
        client.close()
    }
}