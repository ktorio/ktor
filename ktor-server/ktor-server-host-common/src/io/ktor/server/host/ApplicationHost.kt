package io.ktor.server.host

import java.util.concurrent.*

/**
 * Host which runs an application
 */
interface ApplicationHost {

    open class Configuration {
        val parallelism = Runtime.getRuntime().availableProcessors()

        var connectionGroupSize = parallelism / 2 + 1
        var workerGroupSize = parallelism / 2 + 1
        var callGroupSize = parallelism
    }

    /**
     * Environment with which this host is running
     */
    val environment: ApplicationHostEnvironment

    /**
     * Starts this [ApplicationHost]
     *
     * @param wait if true, this function does not exist until application host stops and exits
     * @return returns this instance
     */
    fun start(wait: Boolean = false): ApplicationHost

    /**
     * Stops this [ApplicationHost]
     *
     * @param gracePeriod the maximum amount of time in milliseconds to allow for activity to cool down
     * @param timeout the maximum amount of time to wait until server stops gracefully
     * @param timeUnit the [TimeUnit] for [timeout]
     */
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}

