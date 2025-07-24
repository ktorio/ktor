/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.server.application.ApplicationEnvironment
import io.swagger.parser.OpenAPIParser

public data class OpenAPIReadContext(
    val config: OpenAPIConfig,
    val environment: ApplicationEnvironment
) {
    public val parser: OpenAPIParser = OpenAPIParser()
    public val classLoader: ClassLoader = environment.classLoader
}
