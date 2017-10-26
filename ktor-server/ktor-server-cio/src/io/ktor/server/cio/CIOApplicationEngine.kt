package io.ktor.server.cio

import io.ktor.http.cio.*
import io.ktor.network.util.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class CIOApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit) : BaseApplicationEngine(environment) {
    class Configuration : BaseApplicationEngine.Configuration()

    private val configuration = Configuration().apply(configure)

    private val callExecutor = Executors.newFixedThreadPool(configuration.callGroupSize) { r ->
        Thread(r, "engine-thread")
    }

    private val hostDispatcher = ioCoroutineDispatcher
    private val userDispatcher = DispatcherWithShutdown(callExecutor.asCoroutineDispatcher())

    private val state = AtomicReference<State>(State.CREATED)

    @Volatile
    private var connectors: List<HttpServer>? = null

    private val serverJob = Job()

    override fun start(wait: Boolean): ApplicationEngine {
        if (!state.compareAndSet(State.CREATED, State.STARTING)) throw IllegalStateException("Server is already started")
        environment.start()

        val connectors = ArrayList<HttpServer>(environment.connectors.size)

        try {
            environment.connectors.forEach { connectorSpec ->
                if (connectorSpec.type == ConnectorType.HTTPS) throw UnsupportedOperationException("HTTPS is not supported")
                val connector = startConnector(connectorSpec.port)

                connectors.add(connector)
            }
        } catch (t: Throwable) {
            connectors.forEach { it.serverSocket.cancel() }
            this.connectors = emptyList()
            state.set(State.TERMINATED)
            serverJob.cancel()
            callExecutor.shutdown()
            userDispatcher.completeShutdown()

            throw t
        }

        this.connectors = connectors

        if (!state.compareAndSet(State.STARTING, State.RUNNING)) {
            // shutting down
            doShutdown(connectors)
        }

        if (wait) {
            runBlocking {
                serverJob.join()
            }
        }

        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        val oldState: State
        while (true) {
            val current = state.get()
            if (current == State.TERMINATED) return
            if (current == State.CREATED && state.compareAndSet(current, State.TERMINATED)) return

            if (state.compareAndSet(current, State.STOPPING)) {
                oldState = current
                break
            }
        }

        if (oldState == State.RUNNING) {
            doShutdown(this.connectors!!)
        }

        runBlocking {
            val result = withTimeoutOrNull(gracePeriod, timeUnit) {
                serverJob.join()
                true
            }

            if (result == null) {
                // timeout
                connectors?.forEach {
                    it.serverSocket.invokeOnCompletion { t ->
                        if (t == null) {
                            it.serverSocket.getCompleted().close()
                        }
                    }
                }

                val forceShutdown = withTimeoutOrNull(timeout - gracePeriod, timeUnit) {
                    serverJob.join()
                    false
                } ?: true

                if (forceShutdown) {
                    environment.stop()
                }
            }
        }
    }

    private fun doShutdown(connectors: List<HttpServer>) {
        userDispatcher.prepareShutdown()

        for (connector in connectors) {
            connector.rootServerJob.cancel()
        }

        launch(hostDispatcher) {
            for (connector in connectors) {
                connector.rootServerJob.join()
            }

            try {
                environment.stop()
            } finally {
                state.set(State.TERMINATED)
                serverJob.cancel()

                userDispatcher.completeShutdown()
                callExecutor.shutdown()
            }
        }
    }

    private fun startConnector(port: Int): HttpServer {
        if (state.get() != State.STARTING) return HttpServer.CancelledServer

        val server = httpServer(port, userDispatcher) { request, input, output, upgraded ->
            if (state.get() != State.RUNNING) {
                respondServiceUnavailable(request.version, output)
                return@httpServer
            }

            val call = CIOApplicationCall(application, request, input, output, hostDispatcher, userDispatcher, upgraded)

            try {
                pipeline.execute(call)
            } catch (t: Throwable) {
                t.printStackTrace()
                output.close(t)
            }
        }

        if (state.get() != State.STARTING) {
            server.rootServerJob.cancel()
        }

        return server
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

                output.writePacket(build())
            } finally {
                release()
            }
        }
    }

    private enum class State {
        CREATED,
        STARTING,
        RUNNING,
        STOPPING,
        TERMINATED
    }
}