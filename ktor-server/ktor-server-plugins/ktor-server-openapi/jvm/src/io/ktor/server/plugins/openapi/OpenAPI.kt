/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.annotate.*
import io.ktor.annotate.OpenApiDocSource.Companion.readOpenApiSource
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.swagger.codegen.v3.ClientOpts
import io.swagger.codegen.v3.generators.html.StaticHtml2Codegen
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
 * If source is supplied inside the config [block], the [swaggerFile] argument will take precedence.
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
    source = OpenApiDocSource.FileSource(swaggerFile)
}

/**
 * Creates a `get` endpoint at [path] with documentation rendered from the OpenAPI file.
 *
 * This method uses the configured [OpenAPIConfig.source] to resolve the OpenAPI specification.
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
    val outputPath = OpenAPIConfig().apply(block).outputPath
    return staticFiles(path, File(outputPath)).apply {
        install(OpenAPI) {
            block()
        }
    }
}

/**
 * When installed on a route, this plugin will generate OpenAPI documentation UI code before the first request.
 */
internal val OpenAPI: RouteScopedPlugin<OpenAPIConfig> = createRouteScopedPlugin("OpenAPI", ::OpenAPIConfig) {
    application.generateFilesBeforeStartup(pluginConfig)
}

internal fun Application.generateFilesBeforeStartup(config: OpenAPIConfig) {
    monitor.subscribe(ApplicationModulesLoaded) {
        val apiDocument = readOpenApiSource(config.source, config.buildBaseDoc())
            ?: error("Failed to read OpenAPI document from ${config.source}")
        config.generateFiles(apiDocument)
    }
}

internal fun OpenAPIConfig.generateFiles(apiDocument: String) {
    val swagger = try {
        parser.readContents(apiDocument, null, options)
    } catch (t: Throwable) {
        throw IllegalStateException("Failed to parse OpenAPI document", t)
    }
    val outDir = File(outputPath)
    if (!outDir.exists() && !outDir.mkdirs()) {
        throw kotlinx.io.IOException("Failed to create OpenAPI output directory: $outputPath")
    }
    val openApi = swagger.openAPI
        ?: throw IllegalStateException("Parsed OpenAPI document is missing `openAPI` (messages=${swagger.messages})")

    opts.apply {
        codegen.outputDir = outDir.absolutePath
        config(codegen)
        opts(ClientOpts())
        openAPI(openApi)
    }

    generator.opts(opts)
    try {
        generator.generate()
    } catch (t: Throwable) {
        throw IllegalStateException("Failed to generate OpenAPI UI files into $outputPath", t)
    }
}
