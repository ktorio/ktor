/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.config.*

internal class ConfigWithProperty(config: ApplicationConfig) {
    var property: String = config.tryGetString("property") ?: "Default Value"
}
