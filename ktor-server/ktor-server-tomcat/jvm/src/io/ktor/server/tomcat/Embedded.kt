// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ServerEngineFactory] providing a Tomcat-based [ServerEngine]
 */
public object Tomcat : ServerEngineFactory<TomcatServerEngine, TomcatServerEngine.Configuration> {

    override fun configuration(
        configure: TomcatServerEngine.Configuration.() -> Unit
    ): TomcatServerEngine.Configuration {
        return TomcatServerEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TomcatServerEngine.Configuration,
        serverProvider: () -> Server
    ): TomcatServerEngine {
        return TomcatServerEngine(environment, monitor, developmentMode, configuration, serverProvider)
    }
}
