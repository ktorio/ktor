/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.annotate.*
import io.ktor.annotate.OpenApiSpecSource.Companion.readOpenApiSource
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.swagger.codegen.v3.ClientOpts
import io.swagger.codegen.v3.generators.html.StaticHtml2Codegen
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.io.File

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [File].
 *
 * The documentation is generated using [StaticHtml2Codegen] by default. It can be customized using config in [block].
 * See [OpenAPIConfig] for more details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.openAPI)
 *
 * @param path The base path where the OpenAPI UI will be accessible.
 * @param swaggerFile The path to the OpenAPI file.
 * @param block A configuration block to apply additional OpenAPI configuration settings.
 */
public fun Route.openAPI(
    path: String,
    swaggerFile: String,
    block: OpenAPIConfig.() -> Unit = {}
): Route = openAPI(path) {
    block()
    source = OpenApiSpecSource.FileSource(swaggerFile)
}

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method tries to lookup [swaggerFile] in the resources first, and if it's not found, it will try to read it from
 * the file system using [File].
 *
 * The documentation is generated using [StaticHtml2Codegen] by default. It can be customized using config in [block].
 * See [OpenAPIConfig] for more details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.openapi.openAPI)
 *
 * @param path The base path where the OpenAPI UI will be accessible.
 * @param block A configuration block to apply additional OpenAPI configuration settings.
 */
public fun Route.openAPI(
    path: String,
    block: OpenAPIConfig.() -> Unit = {}
): Route {
    val outputDir = File(OpenAPIConfig().apply(block).outputPath)
    return staticFiles(path, outputDir).apply {
        install(OpenAPI) {
            block()
        }
    }
}

/**
 * When installed on a route, this plugin will generate OpenAPI documentation UI code before the first request.
 */
internal val OpenAPI: RouteScopedPlugin<OpenAPIConfig> = createRouteScopedPlugin("OpenAPI", ::OpenAPIConfig) {
    val source = pluginConfig.source
    val createFiles = with(application) {
        launch(start = CoroutineStart.LAZY) {
            val apiDocument = readOpenApiSource(source) ?: run {
                log.error("Failed to read OpenAPI document from $source")
                return@launch
            }
            this@createRouteScopedPlugin.pluginConfig
                .generateFiles(apiDocument)
        }
    }
    onCall {
        createFiles.join()
    }
}

internal fun OpenAPIConfig.generateFiles(apiDocument: String) {
    val swagger = parser.readContents(apiDocument, null, options)
    File(outputPath).mkdirs()

    opts.apply {
        config(codegen)
        opts(ClientOpts())
        openAPI(swagger.openAPI)
    }

    generator.opts(opts)
    generator.generate()
}
