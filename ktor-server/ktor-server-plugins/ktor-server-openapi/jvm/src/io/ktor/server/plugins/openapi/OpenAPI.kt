/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.swagger.codegen.v3.*
import io.swagger.codegen.v3.generators.html.*
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import kotlinx.coroutines.launch
import java.io.*

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 * The documentation is generated using [StaticHtml2Codegen] by default. It can be customized using config in [block].
 * See [OpenAPIConfig] for more details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.openAPI)
 */
public fun Route.openAPI(
    path: String,
    swaggerFile: String,
    block: OpenAPIConfig.() -> Unit = {}
) {
    openAPI(path) {
        source = OpenAPISource.File(swaggerFile)
        block()
    }
}

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 * The documentation is generated using [StaticHtml2Codegen] by default. It can be customized using config in [block].
 * See [OpenAPIConfig] for more details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.openAPI)
 */
public fun Route.openAPI(
    path: String,
    block: OpenAPIConfig.() -> Unit = {}
) {
    val config = OpenAPIConfig()
    File("docs").mkdirs()
    application.launch {
        with(config) {
            val spec = source.provide(OpenAPIReadContext(config, environment)).apply {
                // Generator NPE's if these are missing
                info = info ?: Info()
                components = components ?: Components()
            }

            config.opts.apply {
                config(codegen)
                opts(ClientOpts())
                openAPI(spec)
            }

            block(this)

            generator.opts(opts)
            generator.generate()
        }
    }
    staticFiles(path, File("docs"))
}
