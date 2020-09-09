/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*

/**
 * Engine which runs an application
 */
public interface ApplicationEngine {

    /**
     * Configuration for the [ApplicationEngine]
     */
    @Suppress("MemberVisibilityCanBePrivate")
    public open class Configuration {
        /**
         * Provides currently available parallelism, e.g. number of available processors
         */
        public val parallelism: Int = Runtime.getRuntime().availableProcessors()

        /**
         * Specifies size of the event group for accepting connections
         */
        public var connectionGroupSize: Int = parallelism / 2 + 1

        /**
         * Specifies size of the event group for processing connections, parsing messages and doing engine's internal work
         */
        public var workerGroupSize: Int = parallelism / 2 + 1

        /**
         * Specifies size of the event group for running application code
         */
        public var callGroupSize: Int = parallelism
    }

    /**
     * Environment with which this engine is running
     */
    public val environment: ApplicationEngineEnvironment

    /**
     * Currently running application instance
     */
    public val application: Application get() = environment.application

    /**
     * Starts this [ApplicationEngine]
     *
     * @param wait if true, this function does not exit until application engine stops and exits
     * @return returns this instance
     */
    public fun start(wait: Boolean = false): ApplicationEngine

    /**
     * Stops this [ApplicationEngine]
     *
     * @param gracePeriod the maximum amount of time for activity to cool down
     * @param timeout the maximum amount of time to wait until server stops gracefully
     * @param timeUnit the [java.util.concurrent.TimeUnit] for [gracePeriod] and [timeout]
     */
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun stop(gracePeriod: Long, timeout: Long, timeUnit: java.util.concurrent.TimeUnit) {
        stop(gracePeriod, timeout, timeUnit)
    }

    /**
     * Stops this [ApplicationEngine]
     *
     * @param gracePeriodMillis the maximum amount of time for activity to cool down
     * @param timeoutMillis the maximum amount of time to wait until server stops gracefully
     */
    public fun stop(gracePeriodMillis: Long, timeoutMillis: Long)
}

