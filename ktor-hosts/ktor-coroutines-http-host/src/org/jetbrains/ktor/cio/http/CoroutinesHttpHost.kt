package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import kotlinx.sockets.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

class CoroutinesHttpHost(environment: ApplicationHostEnvironment) : BaseApplicationHost(environment) {
    private val exec = Executors.newFixedThreadPool(100)

    private val hostCtx = ioCoroutineDispatcher
    private val appCtx = DispatcherWithShutdown(exec.asCoroutineDispatcher())

    @Volatile
    private var shutdown = false

    @Volatile
    private var j: Job? = null

    @Volatile
    private var s: Deferred<ServerSocket> = CompletableDeferred()

    override fun start(wait: Boolean): ApplicationHost {
        environment.start()

        environment.connectors.forEach { connector ->
            if (connector.type == ConnectorType.HTTPS) throw UnsupportedOperationException("HTTP is not supported")
            run(connector.port)
        }

        if (wait) {
            runBlocking {
                j!!.join()
            }
        }

        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        try {
            runBlocking {
                shutdown = true
                appCtx.prepareShutdown()
//                delay(gracePeriod, timeUnit)
                j?.cancel()

                withTimeoutOrNull(timeout, timeUnit) {
                    j?.join()
                }

                s.invokeOnCompletion { t ->
                    if (t == null && !s.isCancelled) {
                        s.getCompleted().close()
                    }
                }
            }
        } finally {
            environment.stop()
        }
    }

    private fun run(port: Int) {
        val (j, s) = httpServer(port, appCtx) { request, input, output ->
            if (shutdown) {
                respondServiceUnavailable(request.version, output)
                return@httpServer
            }

            val call = CIOApplicationCall(application, request, input, output, hostCtx, appCtx)

            try {
                pipeline.execute(call)
            } catch (t: Throwable) {
                t.printStackTrace()
                output.close(t)
            }
        }

        this.s = s
        this.j = j
    }

    private suspend fun respondServiceUnavailable(httpVersion: CharSequence, output: ByteWriteChannel) {
        RequestResponseBuilder().apply {
            try {
                val su = "Service Unavailable"
                responseLine(httpVersion, 503, su)
                headerLine("Connection", "close")
                headerLine("Content-Length", su.length.toString())
                emptyLine()
                bytes(su.toByteArray())

                writeTo(output)
            } finally {
                release()
            }
        }
    }
}