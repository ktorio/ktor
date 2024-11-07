/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.server.engine.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

internal actual val CONFIG_PATH: List<String>
    get() = listOfNotNull(
        getEnvironmentProperty("config.file"),
        getEnvironmentProperty("config.resource"),
        getEnvironmentProperty("config.url"),
    )

@OptIn(InternalAPI::class)
public actual val configLoaders: List<ConfigLoader> = loadServices<ConfigLoader>()
