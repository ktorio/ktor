/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * [ApplicationEngine] implementation for running in a standalone Jetty
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.jetty.jakarta.JettyApplicationEngine)
 */
public class JettyApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    configuration: Configuration,
    private val applicationProvider: () -> Application
) : JettyApplicationEngineBase(environment, monitor, developmentMode, configuration, applicationProvider) {

    override fun start(wait: Boolean): JettyApplicationEngine {
        server.handler = JettyKtorHandler(environment, configuration, pipeline, applicationProvider)
        super.start(wait)
        return this
    }
}
