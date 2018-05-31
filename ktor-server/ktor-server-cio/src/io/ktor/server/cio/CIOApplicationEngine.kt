package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.network.util.*
import io.ktor.pipeline.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

class CIOApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit) :
    BaseApplicationEngine(environment) {
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Number of seconds that the server will keep HTTP IDLE connections open.
         * A connection is IDLE if there are no active requests running.
         */
        var connectionIdleTimeoutSeconds: Int = 45
    }

    private val configuration = Configuration().apply(configure)

    private val hostDispatcher = ioCoroutineDispatcher

    private val callExecutor = Executors.newFixedThreadPool(configuration.callGroupSize) { r ->
        Thread(r, "engine-thread")
    }

    private val userDispatcher = DispatcherWithShutdown(callExecutor.asCoroutineDispatcher())

    private val stopRequest = CompletableDeferred<Unit>()

    private val serverJob = launch(ioCoroutineDispatcher, start = CoroutineStart.LAZY) {
        // starting
        withContext(userDispatcher) {
            environment.start()
        }

        val connectors = ArrayList<HttpServer>(environment.connectors.size)

        try {
            environment.connectors.forEach { connectorSpec ->
                if (connectorSpec.type == ConnectorType.HTTPS) throw UnsupportedOperationException("HTTPS is not supported")
                val connector = startConnector(connectorSpec.port)

                connectors.add(connector)
            }
        } catch (t: Throwable) {
            connectors.forEach { it.rootServerJob.cancel(t) }
            stopRequest.cancel(t)
            throw t
        }

        stopRequest.await()

        // stopping
        connectors.forEach { it.acceptJob.cancel() }

        withContext(userDispatcher) {
            environment.monitor.raise(ApplicationStopPreparing, environment)
        }
    }

    override fun start(wait: Boolean): ApplicationEngine {
        serverJob.start()
        serverJob.invokeOnCompletion {
            try {
                environment.stop()
            } finally {
                userDispatcher.completeShutdown()
            }
        }

        if (wait) {
            runBlocking {
                serverJob.join()
            }
        }

        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        stopRequest.complete(Unit)

        runBlocking {
            val result = withTimeoutOrNull(gracePeriod, timeUnit) {
                serverJob.join()
                true
            }

            if (result == null) {
                // timeout
                userDispatcher.prepareShutdown()
                serverJob.cancel()

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

    private fun startConnector(port: Int): HttpServer {
        val settings = HttpServerSettings(
            port = port,
            connectionIdleTimeoutSeconds = configuration.connectionIdleTimeoutSeconds.toLong()
        )

        val hostContext = hostDispatcher + serverJob
        val userContext = userDispatcher + serverJob

        return httpServer(settings, serverJob, userDispatcher) { request, input, output, upgraded ->
            val call = CIOApplicationCall(application, request, input, output, hostContext, userContext, upgraded)
            pipeline.execute(call)
        }
    }
}