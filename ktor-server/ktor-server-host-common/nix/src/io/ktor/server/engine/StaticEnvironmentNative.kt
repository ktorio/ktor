/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

internal fun StaticApplicationEngineEnvironment(
    parentCoroutineContext: CoroutineContext,
    log: Logger,
    config: ApplicationConfig,
    rootPath: String,
    developmentMode: Boolean,
    connectors: List<EngineConnectorConfig>,
    modules: List<Application.() -> Unit>
): ApplicationEngineEnvironment = object : StaticApplicationEngineEnvironment(
    parentCoroutineContext,
    log,
    config,
    rootPath,
    developmentMode,
    connectors,
    modules
) {}
