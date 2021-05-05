/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.server.engine.*

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
public object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(
        environment: ApplicationEngineEnvironment,
        configure: CIOApplicationEngine.Configuration.() -> Unit
    ): CIOApplicationEngine {
        return CIOApplicationEngine(environment, configure)
    }
}
