package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlinx.coroutines.scheduling.*
import java.util.concurrent.*

/**
 * Engine that based on CIO backend
 */
class CIOApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit) :
    BaseApplicationEngine(environment) {

    /**
     * CIO-based server configuration
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Number of seconds that the server will keep HTTP IDLE connections open.
         * A connection is IDLE if there are no active requests running.
         */
        var connectionIdleTimeoutSeconds: Int = 45
    }

    private val configuration = Configuration().apply(configure)

    private val corePoolSize: Int = maxOf(
        configuration.connectionGroupSize + configuration.workerGroupSize,
        environment.connectors.size + 1 // number of selectors + 1
    )

    @UseExperimental(InternalCoroutinesApi::class)
    private val engineDispatcher = ExperimentalCoroutineDispatcher(corePoolSize)

    @UseExperimental(InternalCoroutinesApi::class)
    private val userDispatcher = DispatcherWithShutdown(engineDispatcher.blocking(configuration.callGroupSize))

    private val stopRequest: CompletableJob = Job()

    private val serverJob = CoroutineScope(
        environment.parentCoroutineContext + engineDispatcher
    ).launch(start = CoroutineStart.LAZY) {
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
        } catch (cause: Throwable) {
            connectors.forEach { it.rootServerJob.cancel() }
            stopRequest.completeExceptionally(cause)
            throw cause
        }

        stopRequest.join()

        // stopping
        connectors.forEach { it.acceptJob.cancel() }

        withContext(userDispatcher) {
            environment.monitor.raise(ApplicationStopPreparing, environment)
        }
    }

    private val scope: CoroutineScope =
        CoroutineScope(environment.parentCoroutineContext + engineDispatcher + serverJob)

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
        try {
            shutdownServer(gracePeriod, timeout, timeUnit)
        } finally {
            @UseExperimental(InternalCoroutinesApi::class)
            GlobalScope.launch(engineDispatcher) {
                engineDispatcher.close()
            }
        }
    }

    private fun shutdownServer(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        stopRequest.complete()

        runBlocking {
            val result = withTimeoutOrNull(timeUnit.toMillis(gracePeriod)) {
                serverJob.join()
                true
            }

            if (result == null) {
                // timeout
                userDispatcher.prepareShutdown()
                serverJob.cancel()

                val forceShutdown = withTimeoutOrNull(timeUnit.toMillis(timeout - gracePeriod)) {
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

        return scope.httpServer(settings) { request, input, output, upgraded ->
            withContext(userDispatcher) {
                val call = CIOApplicationCall(
                    application, request, input, output,
                    engineDispatcher, userDispatcher, upgraded
                )
                try {
                    pipeline.execute(call)
                } finally {
                    call.release()
                }
            }
        }
    }
}
