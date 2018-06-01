package io.ktor.server.benchmarks

import io.ktor.*
import io.ktor.cio.*
import io.ktor.client.*
import io.ktor.client.cio.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import io.ktor.compat.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.openjdk.jmh.infra.*
import java.nio.*
import java.util.concurrent.atomic.*


interface AsyncHttpBenchmarkClient {
    fun setup()
    fun shutdown()

    fun submitTask(url: String)
    fun joinTask(control: Control)
}

class KtorBenchmarkClient(val engineFactory: HttpClientEngineFactory<*>) : AsyncHttpBenchmarkClient {
    private val loadLimit = Semaphore(1000)
    private var httpClient: HttpClient? = null
    private val parent = Job()
    private val done = AtomicInteger()

    override fun setup() {
        done.set(0)
        httpClient = HttpClient(engineFactory)
    }

    override fun shutdown() {
        runBlocking {
            parent.cancelAndJoin()
        }

        httpClient?.close()
        httpClient = null
    }

    override fun submitTask(url: String) {
        runBlocking {
            loadLimit.enter()
        }

        launch(HTTP_CLIENT_DEFAULT_DISPATCHER, parent = parent) {
            try {
                httpClient!!.get<HttpResponse>(url).use { response ->
                    val content = response.content
                    val buffer = ByteBuffer.allocate(1024)
                    while (!content.isClosedForRead) {
                        buffer.clear()
                        content.readAvailable(buffer)
                    }

                }

                done.incrementAndGet()
            } catch (cause: Throwable) {
            } finally {
                loadLimit.leave()
            }
        }
    }

    override fun joinTask(control: Control) {
        while (!control.stopMeasurement) {
            val prev = done.get()
            if (prev <= 0) continue

            if (done.compareAndSet(prev, prev - 1)) break
        }
    }
}