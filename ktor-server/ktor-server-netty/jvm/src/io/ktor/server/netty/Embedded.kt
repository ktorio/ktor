// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ServerEngineFactory] providing a Netty-based [ServerEngine]
 */
public object Netty : ServerEngineFactory<NettyServerEngine, NettyServerEngine.Configuration> {

    override fun configuration(
        configure: NettyServerEngine.Configuration.() -> Unit
    ): NettyServerEngine.Configuration {
        return NettyServerEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: NettyServerEngine.Configuration,
        serverProvider: () -> Server
    ): NettyServerEngine {
        return NettyServerEngine(environment, monitor, developmentMode, configuration, serverProvider)
    }
}
