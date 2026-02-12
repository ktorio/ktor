/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

/**
 * Sealed type for the different sources used for generating an OpenAPI document.
 *
 * This is used in the OpenAPI and Swagger plugins.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource)
 */
public sealed interface OpenApiDocSource {
    /**
     * Reads the OpenAPI document from the given source.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource.read)
     *
     * @param application The application to read from.
     * @param defaults Optional base document to merge into the generated document.
     */
    public fun read(application: Application, defaults: OpenApiDoc): Text?

    /**
     * A static string source for an OpenAPI document.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource.Text)
     *
     * @param content The text returned for the spec.
     */
    public class Text(
        public val content: String,
        public val contentType: ContentType = ContentType.Application.Json
    ) : OpenApiDocSource {
        override fun read(
            application: Application,
            defaults: OpenApiDoc
        ): Text = this

        override fun toString(): String = "<string>"
    }

    /**
     * A file-based source for OpenAPI document.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource.File)
     *
     * @param path The file path to read the document from.
     */
    public class File(public val path: String) : OpenApiDocSource {
        public val contentType: ContentType by lazy {
            ContentType.fromFilePath(path).firstOrNull() ?: ContentType.Application.Json
        }

        override fun read(application: Application, defaults: OpenApiDoc): Text? =
            application.readFileContents(path)?.let {
                Text(it, contentType)
            }

        override fun toString(): String =
            "file: $path"
    }

    /**
     * A source for an OpenAPI document that is generated from the application's routing tree.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource.Routing)
     *
     * @param contentType The content type of the generated document.
     * @param schemaInference The JSON schema inference strategy to use when building models. Defaults to [KotlinxSerializerJsonSchemaInference.Default].
     * @param securitySchemes Producer for security schemes to be included in the document. Defaults to all registered security schemes.
     * @param serializeModel Function for serializing the OpenAPI document to a string. Defaults to kotlinx-serialization for JSON or YAML.
     * @param routes Producer for routes to be included in the document.  Defaults to the full routing tree.
     */
    public class Routing(
        public val contentType: ContentType = ContentType.Application.Json,
        public val schemaInference: JsonSchemaInference? = null,
        public val securitySchemes: Application.() -> Map<String, ReferenceOr<SecurityScheme>> = {
            findSecuritySchemes()
        },
        public val serializeModel: (OpenApiDoc) -> String = serializeDefault(contentType),
        public val routes: Application.() -> Sequence<Route> = { routingRoot.descendants() }
    ) : OpenApiDocSource {
        internal companion object {
            internal fun serializeDefault(contentType: ContentType): (OpenApiDoc) -> String =
                when (contentType) {
                    ContentType.Application.Yaml -> ::serializeToYaml
                    ContentType.Application.Json -> Json::encodeToString
                    else -> throw IllegalArgumentException("Unsupported content type: $contentType")
                }
        }

        override fun read(application: Application, defaults: OpenApiDoc): Text {
            // assign schema inference for generating doc
            schemaInference?.let {
                application.attributes.put(JsonSchemaAttributeKey, it)
            }
            val combinedDocument = defaults + securitySchemes(application) + routes(application)
            val content = serializeModel(combinedDocument)
            return Text(content, contentType)
        }

        override fun toString(): String =
            "routes"
    }

    /**
     * A source for an OpenAPI document that is generated from the first available source.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OpenApiDocSource.FirstOf)
     *
     * @param options The list of sources to try.
     */
    public class FirstOf(public val options: List<OpenApiDocSource>) : OpenApiDocSource {
        public constructor(vararg options: OpenApiDocSource) : this(options.toList())

        override fun read(application: Application, defaults: OpenApiDoc): Text? =
            options.firstNotNullOfOrNull { it.read(application, defaults) }

        override fun toString(): String =
            "firstOf: ${options.joinToString(", ")}"
    }
}
