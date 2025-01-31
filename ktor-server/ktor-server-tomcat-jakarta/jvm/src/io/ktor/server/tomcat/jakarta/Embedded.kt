/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Tomcat-based [ApplicationEngine]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.tomcat.jakarta.Tomcat)
 */
public object Tomcat : ApplicationEngineFactory<TomcatApplicationEngine, TomcatApplicationEngine.Configuration> {

    override fun configuration(
        configure: TomcatApplicationEngine.Configuration.() -> Unit
    ): TomcatApplicationEngine.Configuration {
        return TomcatApplicationEngine.Configuration().apply(configure)
    }

    override fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TomcatApplicationEngine.Configuration,
        applicationProvider: () -> Application
    ): TomcatApplicationEngine {
        return TomcatApplicationEngine(environment, monitor, developmentMode, configuration, applicationProvider)
    }
}
