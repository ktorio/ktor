/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.swagger

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.server.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.html.*
import java.io.File

private val docExpansionValues = listOf("list", "full", "none")

private val openApiVersionJsonRegex = Regex(""""openapi"\s*:\s*"([^"]+)"""")
private val openApiVersionYamlRegex = Regex("""(?m)^openapi:\s*["']?([^"'#\s]+)["']?""")

internal fun ContentType.isYaml(): Boolean {
    return contentSubtype.equals("yaml", ignoreCase = true) || contentSubtype.equals("x-yaml", ignoreCase = true)
}

private fun OpenApiDocSource.Text.openApiVersion(): String? = when {
    contentType.match(ContentType.Application.Json) ->
        openApiVersionJsonRegex.find(content)?.groupValues?.get(1)

    contentType.isYaml() ->
        openApiVersionYamlRegex.find(content)?.groupValues?.get(1)

    else -> null
}

/**
 * Warns when an OpenAPI 3.1.x document is served with Swagger UI older than 5.x.
 */
internal fun Logger.warnIfIncompatibleOpenApiAndSwaggerUi(
    document: OpenApiDocSource.Text,
    swaggerUiVersion: String
) {
    val openApiVersion = document.openApiVersion() ?: return
    if (!openApiVersion.startsWith("3.1")) return
    val swaggerMajor = swaggerUiVersion.substringBefore('.').toIntOrNull() ?: return
    if (swaggerMajor >= 5) return
    warn(
        "OpenAPI document version is $openApiVersion, but Swagger UI version $swaggerUiVersion " +
            "does not support OpenAPI 3.1. Use Swagger UI 5.0.0+, or stick to an OpenAPI 3.0.x specification."
    )
}

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
        source = OpenApiDocSource.File(swaggerFile)
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
        source = OpenApiDocSource.File(apiFile.absolutePath)
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
        source = OpenApiDocSource.Text(api)
        remotePath = apiUrl
    }

/**
 * Adds a Swagger UI endpoint to the current route.
 *
 * OpenAPI 3.1.x specifications require Swagger UI 5.0.0 or later ([SwaggerConfig.version]). The default version is
 * 5.31.0. Pinning a 4.x UI version only works reliably with OpenAPI 3.0.x specifications.
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
    val log = application.log
    val openApiDoc = with(application) {
        async(start = CoroutineStart.LAZY) {
            val doc = source.read(this@with, config.buildBaseDoc())
                ?: error("Failed to read OpenAPI document from $source")
            doc.also { log.warnIfIncompatibleOpenApiAndSwaggerUi(document = it, swaggerUiVersion = config.version) }
        }
    }

    @OptIn(ExperimentalKtorApi::class)
    return route(path) {
        get(apiUrl) {
            val doc = openApiDoc.await()
            call.respondText(doc.content, doc.contentType)
        }.hide()

        get("oauth2-redirect.html") {
            call.respondHtml {
                body {
                    script(src = "${config.packageLocation}@${config.version}/oauth2-redirect.js") {}
                }
            }
        }.hide()

        get {
            val fullPath = call.request.path()
            val oauth2RedirectUrlJs = config.oauth2RedirectUrl?.let { "'$it'" }
                ?: "window.location.origin + '$fullPath/oauth2-redirect.html'"
            val docExpansion = runCatching {
                call.request.queryParameters.getOrFail<String>("docExpansion")
            }.getOrNull()?.takeIf {
                it in docExpansionValues
            }

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
        oauth2RedirectUrl: $oauth2RedirectUrlJs,
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
        }.hide()
    }
}
