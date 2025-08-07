/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.info.*
import io.swagger.v3.oas.models.security.*
import io.swagger.v3.oas.models.servers.*
import io.swagger.v3.oas.models.tags.*

/**
 * Convenience function to mimic data classes for working with OpenAPI spec objects.
 */
public fun OpenAPI.copy(
    info: Info? = null,
    externalDocs: ExternalDocumentation? = null,
    servers: List<Server>? = null,
    security: List<SecurityRequirement>? = null,
    tags: List<Tag>? = null,
    paths: Paths? = null,
    components: Components? = null,
    extensions: Map<String, Object>? = null
): OpenAPI =
    OpenAPI()
        .info(info ?: this.info)
        .externalDocs(externalDocs ?: this.externalDocs)
        .servers(servers ?: this.servers)
        .security(security ?: this.security)
        .tags(tags ?: this.tags)
        .paths(paths ?: this.paths)
        .components(components ?: this.components)
        .extensions(extensions ?: this.extensions)
