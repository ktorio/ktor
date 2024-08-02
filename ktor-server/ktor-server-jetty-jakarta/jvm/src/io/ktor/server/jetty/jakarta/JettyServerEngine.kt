/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*

@Deprecated(message = "Renamed to JettyServerEngine", replaceWith = ReplaceWith("JettyServerEngine"))
public typealias JettyApplicationEngine = JettyServerEngine

/**
 * [ServerEngine] implementation for running in a standalone Jetty
 */
public class JettyServerEngine(
    environment: ServerEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configure: Configuration,
    private val serverProvider: () -> Server
) : JettyServerEngineBase(environment, monitor, developmentMode, configure, serverProvider) {

    private val dispatcher = server.threadPool.asCoroutineDispatcher()

    override fun start(wait: Boolean): JettyServerEngine {
        server.handler = JettyKtorHandler(environment, this::pipeline, dispatcher, configuration, serverProvider)
        super.start(wait)
        return this
    }
}
