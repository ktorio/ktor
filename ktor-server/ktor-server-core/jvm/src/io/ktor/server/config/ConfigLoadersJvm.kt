/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import java.util.*

public actual val configLoaders: List<ConfigLoader> = ConfigLoader::class.java.let {
    ServiceLoader.load(it, it.classLoader).toList()
}
