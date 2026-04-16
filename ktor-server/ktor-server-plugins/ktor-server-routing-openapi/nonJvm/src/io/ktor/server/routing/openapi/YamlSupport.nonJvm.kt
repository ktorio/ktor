/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.openapi.OpenApiDoc

internal actual fun serializeToYaml(openApiSpec: OpenApiDoc): String {
    throw UnsupportedOperationException(
        "OpenAPI YAML serialization is not supported on non-JVM targets yet. Use JSON instead."
    )
}
