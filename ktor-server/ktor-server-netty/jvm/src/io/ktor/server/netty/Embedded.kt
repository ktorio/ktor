/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Netty-based [ApplicationEngine]
 */
object Netty : ApplicationEngineFactory<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: NettyApplicationEngine.Configuration.() -> Unit): NettyApplicationEngine {
        return NettyApplicationEngine(environment, configure)
    }
}
