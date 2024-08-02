// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ServerEngineFactory] providing a Jetty-based [ServerEngine]
 */
public object Jetty : ServerEngineFactory<JettyServerEngine, JettyServerEngineBase.Configuration> {

    override fun configuration(
        configure: JettyServerEngineBase.Configuration.() -> Unit
    ): JettyServerEngineBase.Configuration {
        return JettyServerEngineBase.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: JettyServerEngineBase.Configuration,
        serverProvider: () -> Server
    ): JettyServerEngine {
        return JettyServerEngine(environment, monitor, developmentMode, configuration, serverProvider)
    }
}
