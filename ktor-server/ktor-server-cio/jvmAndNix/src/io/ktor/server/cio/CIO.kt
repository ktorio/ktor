/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ServerEngineFactory] providing a CIO-based [ServerEngine]
 */
public object CIO : ServerEngineFactory<CIOServerEngine, CIOServerEngine.Configuration> {

    override fun configuration(
        configure: CIOServerEngine.Configuration.() -> Unit
    ): CIOServerEngine.Configuration {
        return CIOServerEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: CIOServerEngine.Configuration,
        serverProvider: () -> Server
    ): CIOServerEngine {
        return CIOServerEngine(environment, monitor, developmentMode, configuration, serverProvider)
    }
}
