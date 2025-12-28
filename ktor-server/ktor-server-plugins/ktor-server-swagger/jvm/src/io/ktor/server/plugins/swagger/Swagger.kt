/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.annotate.*
import io.ktor.annotate.OpenApiDocSource.Companion.readOpenApiSource
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.html.*
import java.io.File

/**
 * Creates a `get` endpoint with [SwaggerUI] at [path] rendered from the OpenAPI file located at [swaggerFile].
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [java.io.File].
 *
 * If source is supplied inside the config [block], the [swaggerFile] argument will take precedence.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 */
public fun Route.swaggerUI(
    path: String,
    swaggerFile: String,
    block: SwaggerConfig.() -> Unit = {}
): Route =
    swaggerUI(path) {
        block()
        source = OpenApiDocSource.FileSource(swaggerFile)
        remotePath = File(swaggerFile).name
    }

/**
 * Creates a `get` endpoint with [swaggerUI] at [path] rendered from the [apiFile].
 *
 * If source is supplied inside the config [block], the [apiFile] argument will take precedence.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 */
public fun Route.swaggerUI(path: String, apiFile: File, block: SwaggerConfig.() -> Unit = {}): Route =
    swaggerUI(path) {
        block()
        source = OpenApiDocSource.FileSource(apiFile.absolutePath)
        remotePath = apiFile.name
    }

/**
 * Configures a route to serve Swagger UI and its corresponding API specification.
 *
 * This function sets up a given path to serve a Swagger UI interface based on the provided API specification.
 *
 * If source is supplied inside the config [block], the [api] argument will take precedence.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 *
 * @param path The base path where the Swagger UI will be accessible.
 * @param apiUrl The relative URL for the Swagger API JSON file.
 * @param api The content of the Swagger API specification.
 * @param block A configuration block to apply additional Swagger configuration settings.
 */
public fun Route.swaggerUI(
    path: String,
    apiUrl: String,
    api: String,
    block: SwaggerConfig.() -> Unit = {}
): Route =
    swaggerUI(path) {
        block()
        source = OpenApiDocSource.StringSource(api)
        remotePath = apiUrl
    }

/**
 * Adds a Swagger UI endpoint to the current route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.swagger.swaggerUI)
 *
 * @param path The root path where the Swagger UI will be available.
 * @param block Configuration block for customizing the Swagger UI, such as defining the OpenAPI specification source.
 */
public fun Route.swaggerUI(
    path: String,
    block: SwaggerConfig.() -> Unit = {}
): Route {
    val config = SwaggerConfig().apply(block)
    val source = config.source
    val apiUrl = config.remotePath
    val openApiDocText = with(application) {
        async(start = CoroutineStart.LAZY) {
            readOpenApiSource(source, config.buildBaseDoc())
                ?: error("Failed to read OpenAPI document from $source")
        }
    }

    return route(path) {
        get(apiUrl) {
            call.respondText(openApiDocText.await(), source.contentType)
        }
        get {
            val fullPath = call.request.path()
            val docExpansion = runCatching {
                call.request.queryParameters.getOrFail<String>("docExpansion")
            }.getOrNull()

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
                    link(
                        href = config.faviconLocation,
                        rel = "icon",
                        type = "image/x-icon"
                    )
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
        url: '$fullPath/$apiUrl',
        dom_id: '#swagger-ui',
        deepLinking: ${config.deepLinking},
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ],
        layout: 'StandaloneLayout'${docExpansion?.let { ",\n        docExpansion: '$it'" } ?: ""}
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
