/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.EncodeDefault.Mode.ALWAYS
import kotlinx.serialization.json.*

/** This is the root document object for the API specification. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class OpenApiSpecification(
    @EncodeDefault(ALWAYS) public val openapi: String = "3.1.1",
    /** Provides metadata about the API. The metadata can be used by the clients if needed. */
    public val info: OpenApiInfo,
    /** An array of Server Objects, which provide connectivity information to a target server. */
    public val servers: List<Server>? = null,
    /** The available paths and operations for the API. */
    public val paths: Map<String, PathItem> = emptyMap(),
    /**
     * The incoming webhooks that MAY be received as part of this API and that the API consumer MAY
     * choose to implement. Closely related to the callbacks feature, this section describes requests
     * initiated other than by an API call, for example by an out of band registration. The key name
     * is a unique string to refer to each webhook, while the (optionally referenced) Path Item Object
     * describes a request that may be initiated by the API provider and the expected responses.
     */
    public val webhooks: Map<String, ReferenceOr<PathItem>>? = null,
    /** An element to hold various schemas for the specification. */
    public val components: Components? = null,
    /**
     * A declaration of which security mechanisms can be used across the API. The list of values
     * includes alternative security requirement objects that can be used. Only one of the security
     * requirement objects need to be satisfied to authorize a request. Individual operations can
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
    public val tags: Set<Tag>? = null,
    /** Additional external documentation. */
    public val externalDocs: ExternalDocs? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    public val extensions: Map<String, JsonElement>? = null,
)

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
@Serializable
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
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
    public val extensions: Map<String, JsonElement> = emptyMap(),
) {
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
        /**
         * Any additional external documentation for this OpenAPI document. The key is the name of the
         * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
         * [JsonPrimitive], [JsonArray] or [JsonObject].
         */
        public val extensions: Map<String, JsonElement> = emptyMap(),
    )

    /** License information for the exposed API. */
    @Serializable
    public data class License(
        /** The license name used for the API. */
        public val name: String,
        /** A URL to the license used for the API. MUST be in the format of a URL. */
        public val url: String? = null,
        private val identifier: String? = null,
        /**
         * Any additional external documentation for this OpenAPI document. The key is the name of the
         * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
         * [JsonPrimitive], [JsonArray] or [JsonObject].
         */
        public val extensions: Map<String, JsonElement> = emptyMap(),
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
 * components object will have no effect on the API unless they are explicitly referenced from
 * properties outside the components object.
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
    public val links: Map<String, Link>? = null,
    public val callbacks: Map<String, Callback>? = null,
    public val pathItems: Map<String, ReferenceOr<PathItem>>? = null,
    /**
     * Any additional external documentation for this OpenAPI document. The key is the name of the
     * extension (beginning with x-), and the value is the data. The value can be a [JsonNull],
     * [JsonPrimitive], [JsonArray] or [JsonObject].
     */
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
            links.isNullOrEmpty() &&
            callbacks.isNullOrEmpty() &&
            pathItems.isNullOrEmpty()

    /**
     * Returns true if this object has at least one property.
     */
    public fun isNotEmpty(): Boolean = !isEmpty()
}
