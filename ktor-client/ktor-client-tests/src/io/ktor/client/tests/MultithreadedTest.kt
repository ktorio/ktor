package io.ktor.client.tests

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.test.*


private const val TEST_SIZE = 100_000
private const val DEFAULT_THREADS_COUNT = 32

abstract class MultithreadedTest(private val factory: HttpClientEngineFactory<*>) : TestWithKtor() {
    private val counter: AtomicInteger = AtomicInteger()

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        routing {
            get("/") {
                call.respondText(counter.incrementAndGet().toString())
            }
            static {
                resource("jarfile", "String.class", "java.lang")
            }
        }
    }

    @Test
    fun numberTest() = runBlocking {
        val client = HttpClient(factory)
        val result = withPool {
            val response = client.get<HttpResponse>("http://127.0.0.1:$serverPort")
            val result = response.readText().toInt()
            response.close()
            result
        }.toSet().size

        assertEquals(TEST_SIZE, result)
        assertEquals(TEST_SIZE, counter.get())
        client.close()
    }
}

private fun <T> withPool(
    threads: Int = DEFAULT_THREADS_COUNT,
    testSize: Int = TEST_SIZE,
    block: suspend () -> T
): List<T> {
    val pool = Executors.newFixedThreadPool(threads)
    val result = List(testSize) {
        pool.submit(Callable<T> {
            runBlocking { block() }
        })
    }.map { it.get() }

    pool.shutdown()
    assertTrue(pool.awaitTermination(1, TimeUnit.SECONDS))
    return result
}