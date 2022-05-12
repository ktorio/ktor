/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import io.ktor.util.*

public actual val configLoaders: List<ConfigLoader>
    get() = _configLoaders

private val _configLoaders: MutableList<ConfigLoader> = mutableListOf()

@OptIn(InternalAPI::class)
public fun addConfigLoader(loader: ConfigLoader) {
    _configLoaders.add(loader)
}
