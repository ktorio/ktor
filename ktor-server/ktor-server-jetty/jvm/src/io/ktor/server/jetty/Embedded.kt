/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Jetty-based [ApplicationEngine]
 */
object Jetty : ApplicationEngineFactory<JettyApplicationEngine, JettyApplicationEngineBase.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: JettyApplicationEngineBase.Configuration.() -> Unit): JettyApplicationEngine {
        return JettyApplicationEngine(environment, configure)
    }
}
