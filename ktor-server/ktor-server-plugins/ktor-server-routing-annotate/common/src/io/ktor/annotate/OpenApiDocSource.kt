/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.http.ContentType
import io.ktor.http.fromFilePath
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.routing.Route
import io.ktor.server.routing.routingRoot
import kotlinx.serialization.json.Json

/**
 * Sealed type for the different sources used for generating an OpenAPI document.
 *
 * This is used in the OpenAPI and Swagger plugins.
 */
public sealed interface OpenApiDocSource {
    public companion object {
        /**
         * Reads the OpenAPI document from the given source.
         *
         * @param source The source to read from.
         * @param baseDoc Optional base document to merge into the generated document.
         */
        public fun Application.readOpenApiSource(
            source: OpenApiDocSource,
            baseDoc: OpenApiDoc,
        ): String? =
            when (source) {
                is StringSource -> source.content
                is FileSource -> readFileContents(source.path)
                is RoutingSource -> readOpenApiFromRoute(source, baseDoc)
                is FirstOf -> source.options
                    .firstNotNullOfOrNull {
                        readOpenApiSource(it, baseDoc)
                    }
            }

        private fun Application.readOpenApiFromRoute(
            source: RoutingSource,
            baseDoc: OpenApiDoc,
        ): String {
            val doc = generateOpenApiDoc(
                base = baseDoc,
                routes = source.routes(this)
            )
            return when (source.contentType) {
                ContentType.Application.Yaml -> serializeToYaml(doc)
                ContentType.Application.Json -> Json.encodeToString(doc)
                else -> throw IllegalArgumentException("Unsupported content type: ${source.contentType}")
            }
        }
    }

    /**
     * The [ContentType] of the OpenAPI document.
     *
     * This is generally either application/json or application/yaml.
     */
    public val contentType: ContentType

    /**
     * A static string source for an OpenAPI document.
     *
     * @param content The text returned for the spec.
     */
    public data class StringSource(
        val content: String,
        override val contentType: ContentType = ContentType.Application.Json
    ) : OpenApiDocSource {
        override fun toString(): String = "<string>"
    }

    /**
     * A file-based source for OpenAPI document.
     *
     * @param path The file path to read the document from.
     */
    public data class FileSource(val path: String) : OpenApiDocSource {
        override val contentType: ContentType by lazy {
            ContentType.fromFilePath(path).firstOrNull() ?: ContentType.Application.Json
        }
        override fun toString(): String =
            "file: $path"
    }

    /**
     * A source for an OpenAPI document that is generated from the application's routing tree.
     *
     * @param info The [OpenApiInfo] to use for the document.
     * @param routes Producer for routes to be included in the document.  Defaults to the full routing tree.
     */
    public data class RoutingSource(
        override val contentType: ContentType,
        val routes: Application.() -> Sequence<Route> = { routingRoot.descendants() }
    ) : OpenApiDocSource {
        override fun toString(): String =
            "routes"
    }

    /**
     * A source for an OpenAPI document that is generated from the first available source.
     *
     * @param options The list of sources to try.
     */
    public data class FirstOf(val options: List<OpenApiDocSource>) : OpenApiDocSource {
        public constructor(vararg options: OpenApiDocSource) : this(options.toList())

        override val contentType: ContentType =
            options.firstNotNullOfOrNull { it.contentType } ?: ContentType.Application.Json

        override fun toString(): String =
            "firstOf: ${options.joinToString(", ")}"
    }
}
