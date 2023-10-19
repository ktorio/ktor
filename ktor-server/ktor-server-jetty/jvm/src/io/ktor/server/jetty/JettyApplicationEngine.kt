/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.server.engine.*
import kotlinx.coroutines.*

/**
 * [ApplicationEngine] implementation for running in a standalone Jetty
 */
public class JettyApplicationEngine(
    environment: ApplicationEngineEnvironment,
    configure: Configuration.() -> Unit
) : JettyApplicationEngineBase(environment, configure) {

    private val dispatcher = server.threadPool.asCoroutineDispatcher()

    override fun start(wait: Boolean): JettyApplicationEngine {
        server.handler = JettyKtorHandler(environment, this::pipeline, dispatcher, configuration)
        super.start(wait)
        return this
    }
}
