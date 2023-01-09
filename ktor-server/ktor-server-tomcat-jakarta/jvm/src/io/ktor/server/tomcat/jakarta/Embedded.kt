// ktlint-disable filename
/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat.jakarta

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a Tomcat-based [ApplicationEngine]
 */
public object Tomcat : ApplicationEngineFactory<TomcatApplicationEngine, TomcatApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: TomcatApplicationEngine.Configuration.() -> Unit
    ): TomcatApplicationEngine {
        return TomcatApplicationEngine(environment, configure)
    }
}
