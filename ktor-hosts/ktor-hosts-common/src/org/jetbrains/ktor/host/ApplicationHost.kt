package org.jetbrains.ktor.host

import java.util.concurrent.*

/**
 * Host which runs an application
 */
interface ApplicationHost {
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

