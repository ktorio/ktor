/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.*
import io.ktor.application.*
import io.ktor.config.*
import kotlin.coroutines.*

/**
 * Represents an environment in which engine runs.
 */
public interface ApplicationEngineEnvironment : ApplicationEnvironment {
    /**
     * Connectors that describers where and how server should listen.
     */
    public val connectors: List<EngineConnectorConfig>

    /**
     * Running [Application].
     *
     * @throws an exception if environment has not been started.
     */
    public val application: Application

    /**
     * Starts [ApplicationEngineEnvironment] and creates an application.
     */
    public fun start()

    /**
     * Stops [ApplicationEngineEnvironment] and destroys any running application.
     */
    public fun stop()
}

/**
 * Creates [ApplicationEngineEnvironment] using [ApplicationEngineEnvironmentBuilder].
 */
public fun applicationEngineEnvironment(
    builder: ApplicationEngineEnvironmentBuilder.() -> Unit
): ApplicationEngineEnvironment {
    return ApplicationEngineEnvironmentBuilder().build(builder)
}

