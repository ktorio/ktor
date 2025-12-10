/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.openapi.OpenApiSpecification

internal actual fun serializeToYaml(openApiSpec: OpenApiSpecification): String {
    TODO("YAML support is not implemented on non-JVM targets yet")
}
