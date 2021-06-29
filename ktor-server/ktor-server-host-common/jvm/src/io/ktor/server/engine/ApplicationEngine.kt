/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.util.network.*

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
     * Local addresses for application connectors.
     * If [environment]'s [connector]s was configured to use port=0, you can use this function to get an actual port
     * for these connectors.
     * Available after server was started.
     */
    public suspend fun networkAddresses(): List<NetworkAddress>

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
     * @param gracePeriodMillis the maximum amount of time for activity to cool down
     * @param timeoutMillis the maximum amount of time to wait until server stops gracefully
     */
    public fun stop(gracePeriodMillis: Long, timeoutMillis: Long)
}
