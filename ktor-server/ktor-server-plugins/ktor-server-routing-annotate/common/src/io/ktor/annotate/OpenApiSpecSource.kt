/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.http.ContentType
import io.ktor.http.fromFilePath
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.routingRoot
import kotlinx.serialization.json.Json

/**
 * Sealed type for the different sources used for the OpenAPI specification.
 *
 * This is used in the OpenAPI and Swagger plugins.
 */
public sealed interface OpenApiSpecSource {
    public companion object {
        public fun Application.readOpenApiSource(source: OpenApiSpecSource): String? =
            when (source) {
                is StringSource -> source.content
                is FileSource -> readFileContents(source.path)
                is RoutingSource -> readOpenApiFromRoute(source)
                is FirstOf -> source.options.asSequence().map { readOpenApiSource(it) }.firstOrNull()
            }

        public fun Application.readOpenApiFromRoute(source: RoutingSource): String {
            val specification = generateOpenApiSpec(
                info = source.info ?: OpenApiInfo(title = "Untitled", version = "1.0.0"),
                route = source.route ?: routingRoot
            )
            return when (source.contentType) {
                ContentType.Application.Yaml -> serializeToYaml(specification)
                ContentType.Application.Json -> Json.encodeToString(specification)
                else -> error { "Unsupported content type: ${source.contentType}" }
            }
        }
    }

    /**
     * The [ContentType] of the OpenAPI specification.
     *
     * This is generally either application/json or application/yaml.
     */
    public val contentType: ContentType?

    /**
     * A static string source for OpenAPI specification.
     *
     * @param content The text returned for the spec.
     */
    public data class StringSource(
        val content: String,
        override val contentType: ContentType = ContentType.Application.Json
    ) : OpenApiSpecSource {
        override fun toString(): String = "<string>"
    }
    public data class FileSource(val path: String) : OpenApiSpecSource {
        override val contentType: ContentType? by lazy {
            ContentType.fromFilePath(path).firstOrNull()
        }
        override fun toString(): String =
            "file: $path"
    }

    /**
     * A source for OpenAPI specification that is generated from the application's routing tree.
     *
     * @param info The [OpenApiInfo] to use for the specification.
     * @param route The root [RoutingNode] to use for the specification.
     */
    public data class RoutingSource(
        override val contentType: ContentType,
        val info: OpenApiInfo? = null,
        val route: RoutingNode? = null,
    ) : OpenApiSpecSource {
        override fun toString(): String =
            "route: ${route?.selector}"
    }

    /**
     * A source for OpenAPI specification that is generated from the first non-null source.
     *
     * @param options The list of sources to try.
     */
    public data class FirstOf(val options: List<OpenApiSpecSource>) : OpenApiSpecSource {
        public constructor(vararg options: OpenApiSpecSource) : this(options.toList())

        override val contentType: ContentType =
            options.firstNotNullOfOrNull { it.contentType } ?: ContentType.Application.Json

        override fun toString(): String =
            "firstOf: ${options.joinToString(", ")}"
    }
}
