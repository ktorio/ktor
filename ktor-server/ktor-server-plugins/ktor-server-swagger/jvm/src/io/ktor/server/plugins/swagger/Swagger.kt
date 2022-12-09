/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import java.io.*

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the OpenAPI file located at [swaggerFile].
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 */
public fun Route.swaggerUI(
    path: String,
    swaggerFile: String = "openapi/documentation.yaml",
    block: SwaggerConfig.() -> Unit = {}
) {
    val resource = application.environment.classLoader.getResource(swaggerFile)
    if (resource != null) {
        swaggerUI(path, File(resource.toURI()), block)
        return
    }
    val file = File(swaggerFile)
    if (!file.exists()) {
        throw FileNotFoundException("Swagger file not found: $swaggerFile")
    }

    swaggerUI(path, file, block)
}

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the [apiFile].
 */
public fun Route.swaggerUI(path: String, apiFile: File, block: SwaggerConfig.() -> Unit = {}) {
    if (!apiFile.exists()) {
        throw FileNotFoundException("Swagger file not found: ${apiFile.absolutePath}")
    }

    val fileName = apiFile.name
    val config = SwaggerConfig().apply(block)

    route(path) {
        get(apiFile.name) {
            call.respondFile(apiFile)
        }
        get {
            val fullPath = call.request.path()
            call.respondHtml {
                head {
                    title { +"Swagger UI" }
                    link(
                        href = "${config.packageLocation}@${config.version}/swagger-ui.css",
                        rel = "stylesheet"
                    )
                    config.customStyle?.let {
                        link(href = it, rel = "stylesheet")
                    }
                }
                body {
                    div { id = "swagger-ui" }
                    script(src = "${config.packageLocation}@${config.version}/swagger-ui-bundle.js") {
                        attributes["crossorigin"] = "anonymous"
                    }

                    val src = "${config.packageLocation}@${config.version}/swagger-ui-standalone-preset.js"
                    script(src = src) {
                        attributes["crossorigin"] = "anonymous"
                    }

                    script {
                        unsafe {
                            +"""
window.onload = function() {
    window.ui = SwaggerUIBundle({
        url: '$fullPath/$fileName',
        dom_id: '#swagger-ui',
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ],
        layout: 'StandaloneLayout'
    });
}
                            """.trimIndent()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the [apiFile].
 */
@Deprecated( "Replaced with the extension on [Route]", level = DeprecationLevel.HIDDEN)
public fun Routing.swaggerUI(path: String, apiFile: File, block: SwaggerConfig.() -> Unit = {}) {
    swaggerUI(path, apiFile, block)
}

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the OpenAPI file located at [swaggerFile].
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 */
@Deprecated( "Replaced with the extension on [Route]", level = DeprecationLevel.HIDDEN)
public fun Routing.swaggerUI(
    path: String,
    swaggerFile: String = "openapi/documentation.yaml",
    block: SwaggerConfig.() -> Unit = {}
) {
    swaggerUI(path, swaggerFile, block)
}
