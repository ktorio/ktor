/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Tomcat-based [ApplicationEngine]
 */
object Tomcat : ApplicationEngineFactory<TomcatApplicationEngine, TomcatApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: TomcatApplicationEngine.Configuration.() -> Unit
    ): TomcatApplicationEngine {
        return TomcatApplicationEngine(environment, configure)
    }
}
