/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.http.ContentType
import io.ktor.http.fromFilePath
import io.ktor.openapi.JsonSchemaInference
import io.ktor.openapi.KotlinxJsonSchemaInference
import io.ktor.openapi.OpenApiDoc
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
        ): OpenApiDocText? =
            when (source) {
                is OpenApiDocText -> source
                is FileSource -> readFileContents(source.path)?.let { OpenApiDocText(it, source.contentType) }
                is RoutingSource -> {
                    // assign schema inference for generating doc
                    source.schemaInference?.let {
                        attributes.put(JsonSchemaAttributeKey, it)
                    }
                    OpenApiDocText(readOpenApiFromRoute(source, baseDoc), source.contentType)
                }
                is FirstOf ->
                    source.options
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
     * A static string source for an OpenAPI document.
     *
     * @param content The text returned for the spec.
     */
    public data class OpenApiDocText(
        val content: String,
        val contentType: ContentType = ContentType.Application.Json
    ) : OpenApiDocSource {
        override fun toString(): String = "<string>"
    }

    /**
     * A file-based source for OpenAPI document.
     *
     * @param path The file path to read the document from.
     */
    public data class FileSource(val path: String) : OpenApiDocSource {
        val contentType: ContentType by lazy {
            ContentType.fromFilePath(path).firstOrNull() ?: ContentType.Application.Json
        }
        override fun toString(): String =
            "file: $path"
    }

    /**
     * A source for an OpenAPI document that is generated from the application's routing tree.
     *
     * @param contentType The content type of the generated document.
     * @param schemaInference The JSON schema inference strategy to use when building models. Defaults to [KotlinxJsonSchemaInference].
     * @param routes Producer for routes to be included in the document.  Defaults to the full routing tree.
     */
    public data class RoutingSource(
        val contentType: ContentType = ContentType.Application.Json,
        val schemaInference: JsonSchemaInference? = null,
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

        override fun toString(): String =
            "firstOf: ${options.joinToString(", ")}"
    }
}
