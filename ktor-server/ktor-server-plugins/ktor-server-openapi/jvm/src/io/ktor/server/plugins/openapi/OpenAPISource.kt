/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.AuthorizationValue
import java.io.FileNotFoundException

/**
 * Creates an [OpenAPISource] from the specified [files].
 */
public fun OpenAPISource(vararg files: String): OpenAPISource =
    when (files.size) {
        0 -> OpenAPISource.Empty
        1 -> OpenAPISource.File(files[0])
        else -> OpenAPISource.Merged.of(files.map(OpenAPISource::File))
    }

/**
 * Functional interface representing a source of an OpenAPI specification.
 *
 * Implementations of this interface define how an OpenAPI specification is
 * obtained and loaded into the application context. The specification can be
 * provided from various sources such as local files, resource files, or other
 * external systems.
 */
public fun interface OpenAPISource {
    public companion object {
        internal val EmptyModel: OpenAPI = OpenAPI()
        public val Empty: OpenAPISource = OpenAPISource { EmptyModel }
    }

    /**
     * Provides an OpenAPI specification based on the given context.
     *
     * This method reads and parses an OpenAPI document using the configuration
     * and class loader provided in the context.
     *
     * @param context The context containing the necessary configuration and environment
     *                for reading and parsing the OpenAPI document.
     * @return The parsed OpenAPI object representing the specification.
     */
    public suspend fun provide(context: OpenAPIReadContext): OpenAPI

    /**
     * Combines the output of two sources and merges the result.
     *
     * @see [Merged]
     */
    public operator fun plus(other: OpenAPISource): OpenAPISource =
        Merged.of(listOf(this, other))

    /**
     * Maps the current instance of OpenAPI using the provided mapping function.
     *
     * @param mapping A function that takes an [OpenAPI] instance and returns a transformed copy.
     * @return A new [OpenAPISource].[Adapter] instance that uses the transformed OpenAPI.
     */
    public fun map(mapping: (OpenAPI) -> OpenAPI): OpenAPISource =
        Adapter(this, mapping)

    /**
     * Represents an OpenAPI source that reads and parses OpenAPI documentation from a file or resource path.
     *
     * The file can either be located in the application resources or on the file system.
     * If the file is not found in the resources, the file system is used as a fallback.
     *
     * @property resourcePath The path to the OpenAPI file or resource.
     */
    public class File internal constructor(
        private val resourcePath: String,
        private val optional: Boolean = true,
    ) : OpenAPISource {
        private companion object {
            val NO_AUTH: List<AuthorizationValue>? = null
        }

        override suspend fun provide(context: OpenAPIReadContext): OpenAPI {
            try {
                return context.parser.readContents(
                    readOpenAPIFile(resourcePath, context.classLoader),
                    NO_AUTH,
                    context.config.options,
                ).openAPI
            } catch (e: FileNotFoundException) {
                if (optional) {
                    return OpenAPI()
                }
                throw e
            }
        }

        internal fun readOpenAPIFile(swaggerFile: String, classLoader: ClassLoader): String {
            val resource = classLoader.getResourceAsStream(swaggerFile)
                ?.bufferedReader()?.readText()

            if (resource != null) return resource

            val file = java.io.File(swaggerFile)
            if (!file.exists()) {
                throw FileNotFoundException("Swagger file not found: $swaggerFile")
            }

            return file.readText()
        }
    }

    public class Merged internal constructor(public val sources: List<OpenAPISource>) : OpenAPISource {
        public companion object {
            public fun of(sources: List<OpenAPISource>): Merged =
                Merged(sources.flatMap { (it as? Merged)?.sources ?: listOf(it) })
        }
        init {
            require(sources.isNotEmpty()) { "At least one source must be provided" }
        }

        override suspend fun provide(context: OpenAPIReadContext): OpenAPI {
            val result = sources.first().provide(context)
            for (i in 1 until sources.size) {
                val current = sources[i].provide(context)
                result.paths?.putAll(current.paths ?: emptyMap())
                result.components?.schemas?.putAll(current.components?.schemas ?: emptyMap())
                result.components?.responses?.putAll(current.components?.responses ?: emptyMap())
                result.components?.parameters?.putAll(current.components?.parameters ?: emptyMap())
                result.components?.examples?.putAll(current.components?.examples ?: emptyMap())
                result.components?.requestBodies?.putAll(current.components?.requestBodies ?: emptyMap())
                result.components?.headers?.putAll(current.components?.headers ?: emptyMap())
                result.components?.securitySchemes?.putAll(current.components?.securitySchemes ?: emptyMap())
                result.components?.links?.putAll(current.components?.links ?: emptyMap())
                result.components?.callbacks?.putAll(current.components?.callbacks ?: emptyMap())
                result.tags?.addAll(current.tags ?: emptyList())
                result.servers?.addAll(current.servers ?: emptyList())
                result.security?.addAll(current.security ?: emptyList())
            }
            return result
        }
    }

    public class Adapter internal constructor(
        private val source: OpenAPISource,
        private val function: (OpenAPI) -> OpenAPI
    ) : OpenAPISource {
        override suspend fun provide(context: OpenAPIReadContext): OpenAPI =
            function(source.provide(context))
    }
}
