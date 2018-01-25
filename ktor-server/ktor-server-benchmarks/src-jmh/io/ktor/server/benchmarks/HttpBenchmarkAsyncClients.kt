package io.ktor.server.benchmarks

import io.ktor.client.*
import io.ktor.client.cio.*
import io.ktor.client.cio.Semaphore
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.utils.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*


interface AsyncHttpBenchmarkClient {
    fun setup()
    fun shutdown()

    fun submitTask(url: String)
    fun joinTask()
}

class KtorBenchmarkClient(val engineFactory: HttpClientEngineFactory<*>) : AsyncHttpBenchmarkClient {
    private val loadLimit = Semaphore(1000)
    private var httpClient: HttpClient? = null
    private val jobs = ConcurrentLinkedQueue<Job>()

    override fun setup() {
        httpClient = HttpClient(engineFactory)
    }

    override fun shutdown() {
        runBlocking {
            while (jobs.isNotEmpty()) {
                jobs.poll().join()
            }
        }

        httpClient?.close()
        httpClient = null
    }

    override fun submitTask(url: String) {
        runBlocking {
            loadLimit.enter()
        }

        val job = launch(HTTP_CLIENT_DEFAULT_DISPATCHER) {
            httpClient!!.get<HttpResponse>(url).use { response ->
                val channel = response.receiveContent().readChannel()
                val buffer = ByteBuffer.allocate(1024)
                while (!channel.isClosedForRead) {
                    buffer.clear()
                    channel.readAvailable(buffer)
                }
            }

            loadLimit.leave()
        }

        jobs.add(job)
    }

    override fun joinTask() {
        while (true) {
            val job = jobs.poll() ?: continue
            runBlocking {
                job.join()
            }
            break
        }
    }
}