package io.ktor.server.engine

import java.util.concurrent.*

/**
 * Engine which runs an application
 */
interface ApplicationEngine {

    /**
     * Configuration for the [ApplicationEngine]
     */
    open class Configuration {
        /**
         * Provides currently available parallelism, e.g. number of available processors
         */
        val parallelism: Int = Runtime.getRuntime().availableProcessors()

        /**
         * Specifies size of the event group for accepting connections
         */
        var connectionGroupSize = parallelism / 2 + 1

        /**
         * Specifies size of the event group for processing connections, parsing messages and doing engine's internal work
         */
        var workerGroupSize = parallelism / 2 + 1

        /**
         * Specifies size of the event group for running application code
         */
        var callGroupSize = parallelism
    }

    /**
     * Environment with which this engine is running
     */
    val environment: ApplicationEngineEnvironment

    /**
     * Starts this [ApplicationEngine]
     *
     * @param wait if true, this function does not exit until application engine stops and exits
     * @return returns this instance
     */
    fun start(wait: Boolean = false): ApplicationEngine

    /**
     * Stops this [ApplicationEngine]
     *
     * @param gracePeriod the maximum amount of time for activity to cool down
     * @param timeout the maximum amount of time to wait until server stops gracefully
     * @param timeUnit the [TimeUnit] for [gracePeriod] and [timeout]
     */
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}

