/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.utils.io.*
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/**
 * A small interface exposing the OpenAPI document builder state.
 *
 * This is used in the OpenAPI and Swagger plugins for providing details in the resulting generated document.
 */
public interface OpenApiDocDsl {
    /**
     * OpenAPI specification version to be written into the resulting document.
     *
     * This corresponds to the top-level `openapi` field (for example, `"3.1.1"`).
     */
    public var openapiVersion: String

    /**
     * Required OpenAPI "Info Object" describing the API (title, version, optional description, etc.).
     *
     * This corresponds to the top-level `info` section.
     */
    public var info: OpenApiInfo

    /**
     * Configures one or more servers for this API using the [Servers] DSL.
     *
     * Servers describe base URLs where the API can be accessed (for example, production and staging).
     *
     * @param configure DSL block used to add server entries.
     */
    public fun servers(configure: Servers.Builder.() -> Unit)

    /**
     * Configures one or more global security requirements using the [Security] DSL.
     *
     * These requirements apply to operations by default unless overridden at the operation level.
     *
     * @param configure DSL block used to declare security requirements.
     */
    public fun security(configure: Security.Builder.() -> Unit)

    /**
     * Registers a tag name at the document level.
     *
     * Tags are typically used by documentation tooling to group operations.
     *
     * @param tag The tag name.
     */
    public fun tag(tag: String)

    /**
     * Optional reusable components for the document (schemas, responses, parameters, request bodies, etc.).
     *
     * This corresponds to the top-level `components` section.
     */
    public var components: Components?

    /**
     * Optional external documentation for the entire API.
     *
     * This corresponds to the top-level `externalDocs` section.
     */
    public var externalDocs: ExternalDocs?

    /**
     * Specification extensions for the document.
     *
     * In OpenAPI, extension keys must start with `x-` (for example, `x-company-metadata`).
     */
    public val extensions: MutableMap<String, GenericElement>
}

@Serializable(OpenApiDoc.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class OpenApiDoc(
    @EncodeDefault(ALWAYS) public val openapi: String = OPENAPI_VERSION,
    /** Provides metadata about the API. The clients can use the metadata if needed. */
    public val info: OpenApiInfo,
    /** An array of Server Objects, which provide connectivity information to a target server. */
    public val servers: List<Server>? = null,
    /** The available paths and operations for the API. */
    public val paths: Map<String, ReferenceOr<PathItem>> = emptyMap(),
    /**
     * The incoming webhooks that MAY be received as part of this API and that the API consumer MAY
     * choose to implement. Closely related to the callbacks feature, this section describes requests
     * initiated other than by an API call, for example, by an out-of-band registration. The key name
     * is a unique string to refer to each webhook, while the (optionally referenced) Path Item Object
     * describes a request that may be initiated by the API provider and the expected responses.
     */
    public val webhooks: Map<String, ReferenceOr<PathItem>>? = null,
    /** An element to hold various schemas for the specification. */
    public val components: Components? = null,
    /**
     * A declaration of which security mechanisms can be used across the API. The list of values
     * includes alternative security requirement objects that can be used. Only one of the security
     * requirement objects needs to be satisfied to authorize a request. Individual operations can
     * override this definition. To make security optional, an empty security requirement can be
     * included in the array.
     */
    public val security: List<SecurityRequirement>? = null,
    /**
     * A list of tags used by the specification with additional metadata. The order of the tags can be
     * used to reflect on their order by the parsing tools. Not all tags that are used by the
     * 'Operation' Object must be declared. The tags that are not declared MAY be organized randomly
     * or based on the tools' logic. Each tag name in the list MUST be unique.
     */
    public val tags: List<Tag>? = null,
    /** Additional external documentation. */
    public val externalDocs: ExternalDocs? = null,
    /** Any additional external documentation for this OpenAPI document. */
    public override val extensions: Map<String, GenericElement>? = null,
) : Extensible {
    public companion object {
        /**
         * Represents the version of the OpenAPI Specification being used.
         *
         * This constant defines the specific OpenAPI Specification version supported by this implementation,
         * which influences the structure, features, and behavior of the OpenAPI document generation.
         *
         * The value adheres to the official OpenAPI Specification versioning format.
         */
        public const val OPENAPI_VERSION: String = "3.1.1"

        /**
         * Builds an [OpenApiDoc] instance using the provided DSL [configure] block.
         */
        public fun build(configure: Builder.() -> Unit): OpenApiDoc {
            return Builder().apply(configure).build()
        }

        internal object Serializer : ExtensibleMixinSerializer<OpenApiDoc>(
            generatedSerializer(),
            { o, extensions -> o.copy(extensions = extensions) }
        )
    }

    /**
     * Builder for the OpenAPI root document object.
     *
     * This does not include builder DSL for path items and webhooks.  These are generally handled on the routes
     * themselves, then resolved later at runtime.
     */
    @KtorDsl
    public class Builder : OpenApiDocDsl {
        override var openapiVersion: String = "3.1.1"
        override var info: OpenApiInfo = OpenApiInfo(title = "Untitled API", version = "1.0.0")

        override var components: Components? = null
        override var externalDocs: ExternalDocs? = null

        override val extensions: MutableMap<String, GenericElement> = linkedMapOf()

        private val _servers = mutableListOf<Server>()
        private val _tags = mutableListOf<String>()
        private val _securityRequirements = mutableListOf<Map<String, List<String>>>()

        /**
         * A list of servers configured for this API.
         */
        public val servers: List<Server> get() = _servers

        /**
         * A list of tags configured for this API.
         */
        public val tags: List<String> get() = _tags

        /**
         * A list of security requirements configured for this API.
         */
        public val securityRequirements: List<Map<String, List<String>>> get() = _securityRequirements

        override fun servers(configure: Servers.Builder.() -> Unit) {
            Servers.Builder().apply(configure).build().servers.forEach { _servers.add(it) }
        }

        override fun security(configure: Security.Builder.() -> Unit) {
            Security.Builder().apply(configure).build().requirements.forEach { _securityRequirements.add(it) }
        }

        override fun tag(tag: String) {
            _tags.add(tag)
        }

        /**
         * Adds a specification-extension to this OpenAPI document.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /**
         * Builds an instance of [OpenApiDoc] based on the current state of the [Builder].
         *
         * @return An instance of [OpenApiDoc].
         */
        public fun build(): OpenApiDoc {
            return OpenApiDoc(
                openapi = openapiVersion,
                info = info,
                servers = _servers.ifEmpty { null },
                paths = emptyMap(),
                webhooks = emptyMap(),
                components = components,
                security = _securityRequirements.ifEmpty { null },
                tags = _tags.distinct().map(::Tag).ifEmpty { null },
                externalDocs = externalDocs,
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

/**
 * Lists the required security schemes to execute this operation. The object can have multiple
 * security schemes declared in it which are all required (that is, there is a logical AND between
 * the schemes).
 */
public typealias SecurityRequirement = Map<String, List<String>>

/**
 * The object provides metadata about the API. The metadata MAY be used by the clients if needed,
 * and MAY be presented in editing or documentation generation tools for convenience.
 */
@Serializable(OpenApiInfo.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class OpenApiInfo(
    /** The title of the API. */
    public val title: String,
    /**
     * The version of the OpenAPI document (which is distinct from the OpenAPI Specification version
     * or the API implementation version).
     */
    public val version: String,
    /**
     * A short description of the API. [CommonMark syntax](https://spec.commonmark.org/) MAY be used
     * for rich text representation.
     */
    public val description: String? = null,
    /** A URL to the Terms of Service for the API. MUST be in the format of a URL. */
    public val termsOfService: String? = null,
    /** The contact information for the exposed API. */
    public val contact: Contact? = Contact(),
    /** The license information for the exposed API. */
    public val license: License? = null,
    /** Any additional external documentation for this OpenAPI document. */
    public override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<OpenApiInfo>(
            generatedSerializer(),
            { o, extensions -> o.copy(extensions = extensions) }
        )
    }

    /** Contact information for the exposed API. */
    @Serializable
    public data class Contact(
        /** The identifying name of the contact person/organization. */
        public val name: String? = null,
        /** The URL pointing to the contact information. MUST be in the format of a URL. */
        public val url: String? = null,
        /**
         * The email address of the contact person/organization. MUST be in the format of an email
         * address.
         */
        public val email: String? = null,
    )

    /** License information for the exposed API. */
    @Serializable
    public data class License(
        /** The license name used for the API. */
        public val name: String,
        /** A URL to the license used for the API. MUST be in the format of a URL. */
        public val url: String? = null,
        /** An SPDX license expression for the API. The identifier field is mutually exclusive of the url field. */
        public val identifier: String? = null,
    )
}

/**
 * Allows adding metadata to a single tag that is used by @Operation@. It is not mandatory to have
 * a @Tag@ per tag used there.
 */
@Serializable
public data class Tag(
    /** The name of the tag. */
    public val name: String,
    /**
     * A short description for the tag. [CommonMark syntax](https://spec.commonmark.org/) MAY be used
     * for rich text representation.
     */
    public val description: String? = null,
    /** Additional external documentation for this tag. */
    public val externalDocs: ExternalDocs? = null,
)

/**
 * Holds a set of reusable objects for different aspects of the OAS. All objects defined within the
 * component object will have no effect on the API unless they are explicitly referenced from
 * properties outside the component object.
 */
@Serializable(Components.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Components(
    public val schemas: Map<String, JsonSchema>? = null,
    public val responses: Map<String, ReferenceOr<Response>>? = null,
    public val parameters: Map<String, ReferenceOr<Parameter>>? = null,
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    public val requestBodies: Map<String, ReferenceOr<RequestBody>>? = null,
    public val headers: Map<String, ReferenceOr<Header>>? = null,
    public val securitySchemes: Map<String, ReferenceOr<SecurityScheme>>? = null,
    public val links: Map<String, ReferenceOr<Link>>? = null,
    public val callbacks: Map<String, ReferenceOr<Callback>>? = null,
    public val pathItems: Map<String, ReferenceOr<PathItem>>? = null,
    public override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Components>(
            generatedSerializer(),
            { mt, extensions -> mt.copy(extensions = extensions) }
        )
    }

    /**
     * Returns true if this object has no properties.
     */
    public fun isEmpty(): Boolean =
        schemas.isNullOrEmpty() &&
            responses.isNullOrEmpty() &&
            parameters.isNullOrEmpty() &&
            examples.isNullOrEmpty() &&
            requestBodies.isNullOrEmpty() &&
            headers.isNullOrEmpty() &&
            securitySchemes.isNullOrEmpty() &&
            links.isNullOrEmpty() &&
            callbacks.isNullOrEmpty() &&
            pathItems.isNullOrEmpty() &&
            extensions.isNullOrEmpty()

    /**
     * Returns true if this object has at least one property.
     */
    public fun isNotEmpty(): Boolean = !isEmpty()
}
