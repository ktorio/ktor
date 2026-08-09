/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.scalar

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.hide
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.html.*
import java.io.File

/**
 * Creates a `get` endpoint with [ScalarUI] at [path] rendered from the OpenAPI file located at [swaggerFile].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.scalarUI)
 */
public fun Route.scalarUI(
    path: String,
    swaggerFile: String,
    block: ScalarConfig.() -> Unit = {}
): Route =
    scalarUI(path) {
        block()
        source = OpenApiDocSource.File(swaggerFile)
        remotePath = File(swaggerFile).name
    }

/**
 * Creates a `get` endpoint with [scalarUI] at [path] rendered from the [apiFile].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.scalarUI)
 */
public fun Route.scalarUI(path: String, apiFile: File, block: ScalarConfig.() -> Unit = {}): Route =
    scalarUI(path) {
        block()
        source = OpenApiDocSource.File(apiFile.absolutePath)
        remotePath = apiFile.name
    }

/**
 * Configures a route to serve Scalar UI and its corresponding API specification.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.scalarUI)
 *
 * @param path The base path where the Scalar UI will be accessible.
 * @param apiUrl The relative URL for the Scalar API specification file.
 * @param api The content of the Scalar API specification.
 * @param block A configuration block to apply additional Scalar configuration settings.
 */
public fun Route.scalarUI(
    path: String,
    apiUrl: String,
    api: String,
    block: ScalarConfig.() -> Unit = {}
): Route =
    scalarUI(path) {
        block()
        source = OpenApiDocSource.Text(api)
        remotePath = apiUrl
    }

/**
 * Adds a Scalar UI endpoint to the current route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.scalar.scalarUI)
 *
 * @param path The root path where the Scalar UI will be available.
 * @param block Configuration block for customizing the Scalar UI, such as defining the OpenAPI specification source.
 */
public fun Route.scalarUI(
    path: String,
    block: ScalarConfig.() -> Unit = {}
): Route {
    val config = ScalarConfig().apply(block)
    val source = config.source
    val apiUrl = config.remotePath
    val openApiDoc = with(application) {
        async(start = CoroutineStart.LAZY) {
            source.read(this@with, config.buildBaseDoc())
                ?: error("Failed to read OpenAPI document from $source")
        }
    }

    @OptIn(ExperimentalKtorApi::class)
    return route(path) {
        get(apiUrl) {
            val doc = try {
                openApiDoc.await()
            } catch (cause: Throwable) {
                application.environment.log.error("Failed to read OpenAPI document for Scalar UI from source $source", cause)
                null
            }
            if (doc != null) {
                call.respondText(doc.content, doc.contentType)
            } else {
                application.environment.log.warn("Scalar UI: Unable to resolve OpenAPI spec from $source")
                call.respondText(
                    """
                    {
                      "error": "OpenAPI Specification Not Found",
                      "source": "$source",
                      "details": "Failed to read or parse OpenAPI specification.",
                      "troubleshooting": [
                        "For file sources: verify that the specification file exists in resources or project working directory.",
                        "For routing sources: ensure 'source = OpenApiDocSource.Routing(...)' is set and routes are described using .describe { ... }."
                      ]
                    }
                    """.trimIndent(),
                    ContentType.Application.Json,
                    HttpStatusCode.NotFound
                )
            }
        }.hide()

        get {
            val fullPath = call.request.path().removeSuffix("/")
            call.respondHtml {
                head {
                    title { +"Scalar API Reference" }
                    meta { charset = "utf-8" }
                    meta {
                        name = "viewport"
                        content = "width=device-width, initial-scale=1"
                    }
                    config.customStyle?.let {
                        link(href = it, rel = "stylesheet")
                    }
                    config.faviconLocation?.let {
                        link(href = it, rel = "icon", type = "image/x-icon")
                    }
                }
                body {
                    div { id = "app" }
                    script(src = config.cdnUrl) {}
                    script {
                        unsafe {
                            +"""
                            Scalar.createApiReference('#app', {
                                spec: { url: '$fullPath/$apiUrl' },
                                theme: '${config.theme}',
                                layout: '${config.layout}',
                                showSidebar: ${config.showSidebar}
                            });
                            """.trimIndent()
                        }
                    }
                }
            }
        }.hide()
    }
}
