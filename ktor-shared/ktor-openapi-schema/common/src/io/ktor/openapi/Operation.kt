/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.http.*
import io.ktor.openapi.ReferenceOr.*
import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.jvm.JvmInline

/**
 * Describes a single API operation on a path item as defined by the OpenAPI Specification.
 * Use [Operation.Builder] or [Operation.build] to create instances in a type-safe DSL.
 *
 * Properties correspond to the OpenAPI Operation object.
 *
 * @property operationId Unique string used to identify the operation.
 * @property tags Optional tags for this operation.
 * @property summary A short summary of what the operation does.
 * @property description A verbose explanation of the operation behavior.
 * @property externalDocs Contains a description and URL to reference external documentation
 * @property parameters List of parameters that are applicable for this operation.
 * @property requestBody The request body applicable for this operation.
 * @property callbacks Map of possible callbacks related to this operation.
 * @property responses The list of possible responses as they are returned from executing this operation, keyed by status code.
 * @property deprecated Marks the operation as deprecated if true.
 * @property security A declaration of which security mechanisms can be used for this operation.
 * @property servers An alternative server array to service this operation.
 * @property extensions Specification-extensions for this operation (keys must start with `x-`).
 */
@Serializable(Operation.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Operation(
    public val operationId: String? = null,
    public val tags: List<String>? = null,
    public val summary: String? = null,
    public val description: String? = null,
    public val externalDocs: ExternalDocs? = null,
    public val parameters: List<ReferenceOr<Parameter>>? = null,
    public val requestBody: ReferenceOr<RequestBody>? = null,
    public val callbacks: Map<String, ReferenceOr<Callback>>? = null,
    public val responses: Responses? = null,
    public val deprecated: Boolean? = null,
    public val security: List<Map<String, List<String>>>? = null,
    public val servers: List<Server>? = null,
    override val extensions: ExtensionProperties = null
) : Extensible {
    public companion object {
        /**
         * Builds an [Operation] instance using the provided DSL [configure] block.
         */
        public fun build(
            schemaInference: JsonSchemaInference = KotlinxJsonSchemaInference,
            defaultContentTypes: List<ContentType> = listOf(ContentType.Application.Json),
            configure: Builder.() -> Unit
        ): Operation {
            require(defaultContentTypes.isNotEmpty()) { "Default content types must not be empty" }
            return Builder(schemaInference, defaultContentTypes).apply(configure).build()
        }

        internal object Serializer : ExtensibleMixinSerializer<Operation>(
            generatedSerializer(),
            { op, extensions -> op.copy(extensions = extensions) }
        )
    }

    /**
     * Builder for OpenAPI Operation object
     */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) : JsonSchemaInference by schemaInference {
        /** A short summary of what the operation does. */
        public var summary: String? = null

        /** A verbose explanation of the operation behavior. */
        public var description: String? = null

        /** Unique string used to identify the operation. */
        public var operationId: String? = null

        /** Marks the operation as deprecated when true. */
        public var deprecated: Boolean? = null

        /** A URL to additional external documentation for this operation. */
        public var externalDocs: ExternalDocs? = null

        // TODO remove these after KT-14663 is implemented
        private val _tags = mutableListOf<String>()
        private val _parameters = mutableListOf<Parameter>()
        private var _responses: Responses? = null
        private val _servers = mutableListOf<Server>()
        private val _securityRequirements = mutableListOf<Map<String, List<String>>>()
        private val _callbacks = mutableMapOf<String, ReferenceOr<Callback>>()

        /** Collected tags added to this operation. */
        public val tags: List<String> get() = _tags

        /** Collected parameter definitions for this operation. */
        public val parameters: List<Parameter> get() = _parameters

        /** Collected response definitions keyed by HTTP status code. */
        public val responses: Responses? get() = _responses

        /** Collected server definitions specific to this operation. */
        public val servers: List<Server> get() = _servers

        /** Declared security requirements for this operation. */
        public val securityRequirements: List<Map<String, List<String>>> get() = _securityRequirements

        /** Request body definition for this operation, if any. */
        public var requestBody: RequestBody? = null

        /** Specification-extensions for this operation (keys must start with `x-`). */
        public var extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /** Callback definitions for this operation, if any. */
        public val callbacks: Map<String, ReferenceOr<Callback>> get() = _callbacks

        /**
         * Adds a tag to this operation.
         *
         * @param tag The tag name to add.
         */
        public fun tag(tag: String) {
            _tags.add(tag)
        }

        /**
         * Adds operation parameters using the [Parameters] DSL.
         *
         * @param configure DSL to define one or more parameters.
         */
        public fun parameters(configure: Parameters.Builder.() -> Unit) {
            Parameters.Builder(schemaInference, defaultContentTypes).apply(configure).build().parameters.forEach {
                _parameters.add(it)
            }
        }

        /**
         * Defines a request body for this operation using the [RequestBody] DSL.
         *
         * @param configure DSL to configure the request body.
         */
        public fun requestBody(configure: RequestBody.Builder.() -> Unit) {
            requestBody = RequestBody.Builder(schemaInference, defaultContentTypes).apply(configure).build()
        }

        /**
         * Adds possible responses returned by this operation using the [Responses] DSL.
         *
         * @param configure DSL to define one or more responses.
         */
        public fun responses(configure: Responses.Builder.() -> Unit) {
            _responses = Responses.Builder(schemaInference, defaultContentTypes).apply(configure).build()
        }

        /**
         * Adds a callback definition for this operation using the [Callback] DSL.
         */
        public fun callback(key: String, value: Callback) {
            _callbacks[key] = Value(value)
        }

        /**
         * Adds server definitions specific to this operation using the [Servers] DSL.
         *
         * @param configure DSL to define one or more servers.
         */
        public fun servers(configure: Servers.Builder.() -> Unit) {
            Servers.Builder().apply(configure).build().servers.forEach { _servers.add(it) }
        }

        /**
         * Declares security requirements for this operation using the [Security] DSL.
         *
         * @param configure DSL to declare one or more security requirements.
         */
        public fun security(configure: Security.Builder.() -> Unit) {
            Security.Builder().apply(configure).build().requirements.forEach { _securityRequirements.add(it) }
        }

        /**
         * Adds a specification-extension to this operation.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        internal fun build(): Operation {
            return Operation(
                tags = if (_tags.isEmpty()) null else _tags,
                summary = summary,
                description = description,
                externalDocs = externalDocs,
                operationId = operationId,
                parameters = _parameters.map(::Value).ifEmpty { null },
                requestBody = requestBody?.let(::Value),
                responses = _responses,
                callbacks = _callbacks.ifEmpty { null },
                deprecated = deprecated,
                security = if (_securityRequirements.isEmpty()) null else _securityRequirements,
                servers = if (_servers.isEmpty()) null else _servers,
                extensions = extensions.ifEmpty { null }
            )
        }
    }
}

/**
 * A container for multiple operation [Parameter] definitions created via [Parameters.Builder].
 *
 * @property parameters The collected list of parameters.
 */
@Serializable
public data class Parameters(
    public val parameters: List<Parameter>
) {
    /**
     * DSL builder for assembling a list of [Parameter]s used by an operation.
     */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) {
        private val _parameters = mutableListOf<Parameter>()

        /** The collected list of parameters. */
        public val parameters: List<Parameter> get() = _parameters

        /**
         * Adds a generic [Parameter] via the provided [configure] block.
         *
         * This is used internally for marking ambiguous parameter types (i.e., `call.parameters`)
         *
         * @param configure DSL to configure the parameter.
         */
        @Deprecated("Use path, query, header, or cookie instead.")
        public fun parameter(name: String, configure: Parameter.Builder.() -> Unit) {
            _parameters.add(
                Parameter.Builder(schemaInference, defaultContentTypes).apply {
                    this.name = name
                    configure()
                }.build()
            )
        }

        /**
         * Adds a required path parameter with the given [name].
         *
         * @param name The parameter name.
         * @param configure Optional DSL to further configure the parameter.
         */
        public fun path(name: String, configure: Parameter.Builder.() -> Unit = {}) {
            Parameter.Builder(schemaInference, defaultContentTypes).apply {
                this.name = name
                this.`in` = ParameterType.path
                this.required = true
                configure()
            }.also { _parameters.add(it.build()) }
        }

        /**
         * Adds a query parameter with the given [name].
         *
         * @param name The parameter name.
         * @param configure Optional DSL to further configure the parameter.
         */
        public fun query(name: String, configure: Parameter.Builder.() -> Unit = {}) {
            Parameter.Builder(schemaInference, defaultContentTypes).apply {
                this.name = name
                this.`in` = ParameterType.query
                configure()
            }.also { _parameters.add(it.build()) }
        }

        /**
         * Adds a header parameter with the given [name].
         *
         * @param name The parameter name.
         * @param configure Optional DSL to further configure the parameter.
         */
        public fun header(name: String, configure: Parameter.Builder.() -> Unit = {}) {
            Parameter.Builder(schemaInference, defaultContentTypes).apply {
                this.name = name
                this.`in` = ParameterType.header
                configure()
            }.also { _parameters.add(it.build()) }
        }

        /**
         * Adds a cookie parameter with the given [name].
         *
         * @param name The parameter name.
         * @param configure Optional DSL to further configure the parameter.
         */
        public fun cookie(name: String, configure: Parameter.Builder.() -> Unit = {}) {
            Parameter.Builder(schemaInference, defaultContentTypes).apply {
                this.name = name
                this.`in` = ParameterType.cookie
                configure()
            }.also { _parameters.add(it.build()) }
        }

        /** Builds the [Parameters] container. */
        internal fun build(): Parameters {
            return Parameters(_parameters)
        }
    }
}

/**
 * Describes a single operation parameter (path, query, header, or cookie).
 *
 * @property name The name of the parameter.
 * @property `in` The location of the parameter. One of: "query", "header", "path", or "cookie".
 * @property description A brief description of the parameter.
 * @property required Determines whether this parameter is mandatory.
 * @property deprecated Marks the parameter as deprecated if true.
 * @property schema The schema defining the parameter type.
 * @property style Describes how the parameter value will be serialized.
 * @property explode Specifies whether arrays and objects generate separate parameters for each value.
 * @property allowReserved Determines if reserved characters `:/?#[]@!$&'()*+,;=` are allowed without percent-encoding.
 * @property allowEmptyValue Allows sending a parameter with an empty value (deprecated in OpenAPI 3.1).
 * @property example Example of the parameter's potential value.
 * @property examples Map of examples for the parameter.
 * @property extensions Specification-extensions for this parameter (keys must start with `x-`).
 */
@Serializable(Parameter.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Parameter(
    public val name: String,
    public val `in`: ParameterType? = null,
    public val description: String? = null,
    public val required: Boolean = false,
    public val deprecated: Boolean = false,
    public val schema: ReferenceOr<JsonSchema>? = null,
    public val content: Map<@Serializable(ContentTypeSerializer::class) ContentType, MediaType>? = null,
    public val style: String? = null,
    public val explode: Boolean? = null,
    public val allowReserved: Boolean? = null,
    public val allowEmptyValue: Boolean? = null,
    public val example: GenericElement? = null,
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Parameter>(
            generatedSerializer(),
            { pi, extensions -> pi.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Parameter] instance. */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) : JsonSchemaInference by schemaInference {
        /** The name of the parameter. */
        public var name: String? = null

        /** Location of the parameter: one of "query", "header", "path", or "cookie". */
        public var `in`: ParameterType? = null

        /** A brief description of the parameter. */
        public var description: String? = null

        /** Whether this parameter is mandatory. */
        public var required: Boolean = false

        /** Marks the parameter as deprecated when true. */
        public var deprecated: Boolean = false

        /** The schema defining the parameter type. */
        public var schema: JsonSchema? = null

        private val _content = mutableMapOf<ContentType, MediaType.Builder>()

        /** Map of media type to [MediaType] object. */
        public val content: Map<ContentType, MediaType> get() = _content.mapValues { it.value.build() }

        /** Describes how the parameter value will be serialized (e.g., "matrix", "label", "form", "simple", "spaceDelimited", "pipeDelimited", "deepObject"). */
        public var style: String? = null

        /** Specifies whether arrays and objects generate separate parameters for each value. */
        public var explode: Boolean? = null

        /** Determines if reserved characters are allowed without percent-encoding. Only applies to query parameters. */
        public var allowReserved: Boolean? = null

        /** Allows sending a parameter with an empty value. Deprecated in OpenAPI 3.1, only applies to query parameters. */
        public var allowEmptyValue: Boolean? = null

        /** Example of the parameter's potential value. */
        public var example: GenericElement? = null

        /** Map of examples for the parameter. */
        public var examples: MutableMap<String, ReferenceOr<ExampleObject>> = mutableMapOf()

        /** Specification-extensions for this parameter (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Provide a media type definition for the response body.
         *
         * This applies to all registered default content types, as defined in the ContentNegotiation plugin.
         */
        public fun content(configure: MediaType.Builder.() -> Unit) {
            for (contentType in defaultContentTypes) {
                _content.getOrPut(contentType) {
                    MediaType.Builder(schemaInference)
                }.apply(configure)
            }
        }

        /**
         * Adds a media type definition for the response body using a ContentType receiver.
         *
         * @param configure DSL to configure the [MediaType].
         */
        public operator fun ContentType.invoke(configure: MediaType.Builder.() -> Unit = {}) {
            _content.getOrPut(this) { MediaType.Builder(schemaInference) }.apply(configure)
        }

        /**
         * Adds a custom vendor-specific extension.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /**
         * Adds an example for this parameter.
         *
         * @param name The example identifier.
         * @param example The example object.
         */
        public fun example(name: String, example: ExampleObject) {
            examples[name] = Value(example)
        }

        /** Validates required fields and constructs the [Parameter]. */
        internal fun build(): Parameter {
            requireNotNull(name) { "Parameter name is required" }

            return Parameter(
                name = name!!,
                `in` = `in`,
                description = description,
                required = required,
                deprecated = deprecated,
                schema = schema?.let(::Value),
                content = _content.mapValues { it.value.build() }.ifEmpty { null },
                style = style,
                explode = explode,
                allowReserved = allowReserved,
                allowEmptyValue = allowEmptyValue,
                example = example,
                examples = examples.ifEmpty { null },
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

@Suppress("EnumEntryName")
@Serializable
public enum class ParameterType {
    query,
    header,
    path,
    cookie,
}

public typealias ResponsesByStatusCode = Map<Int, ReferenceOr<Response>>?

/**
 * A container for named response objects keyed by HTTP status code or "default".
 *
 * @property responses Map of status code (or "default") to a [Response].
 */
@Serializable(Responses.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Responses(
    public val default: ReferenceOr<Response>? = null,
    public val responses: Map<Int, ReferenceOr<Response>>? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer :
            DoublePropertyMixinSerializer<Responses, ResponsesByStatusCode, ExtensionProperties>(
                generatedSerializer(),
                Responses::responses,
                serializer<Map<Int, ReferenceOr<Response>>?>(),
                { it.toIntOrNull() != null },
                Responses::extensions,
                serializer<ExtensionProperties>(),
                { it.startsWith("x-") },
                { r, responses, extensions -> r.copy(responses = responses, extensions = extensions) }
            )
    }

    /** Builder for collecting operation [Response]s keyed by status code. */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) {
        private val _responses = mutableMapOf<Int, Response.Builder>()
        private val _extensions = mutableMapOf<String, GenericElement>()

        public var default: Response.Builder? = null

        /** Map of HTTP status code (or "default") to a response definition. */
        public val responses: Map<Int, Response> get() = _responses.mapValues { it.value.build() }

        /** Map of extension properties provided */
        public val extensions: Map<String, GenericElement> get() = _extensions

        /**
         * Adds a response for the given HTTP [statusCode].
         *
         * @param statusCode The HTTP status code as an Int.
         * @param configure DSL to configure the [Response].
         */
        public fun response(statusCode: Int, configure: Response.Builder.() -> Unit) {
            _responses.getOrPut(statusCode) {
                Response.Builder(schemaInference, defaultContentTypes)
            }.apply(configure)
        }

        /**
         * Shorthand to add a response using this [HttpStatusCode] as the key.
         *
         * @param configure Optional DSL to configure the [Response].
         */
        public operator fun HttpStatusCode.invoke(configure: Response.Builder.() -> Unit = {}) {
            response(value, configure)
        }

        /**
         * Adds the catch-all "default" response.
         *
         * @param configure DSL to configure the default [Response].
         */
        public fun default(configure: Response.Builder.() -> Unit) {
            if (default == null) {
                default = Response.Builder(schemaInference, defaultContentTypes).apply(configure)
            } else {
                default!!.apply(configure)
            }
        }

        /** Builds the [Responses] map. */
        internal fun build(): Responses {
            return Responses(
                default?.let { Value(it.build()) },
                _responses.mapValues { (_, v) -> Value(v.build()) }.ifEmpty { null },
                _extensions.ifEmpty { null }
            )
        }
    }
}

/**
 * Describes a response returned by an operation including description, headers, content, and links.
 *
 * @property description Human-readable description of the response.
 * @property headers Optional map of response header parameters.
 * @property content Map of media type to [MediaType] object, describing the response content.
 * @property links Map of link names to [Link] objects for dynamic links that can be followed from the response.
 * @property extensions Specification-extensions for this response (keys must start with `x-`).
 */
@Serializable(Response.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Response(
    public val description: String,
    public val headers: Map<String, ReferenceOr<Header>>? = null,
    public val content: Map<@Serializable(ContentTypeSerializer::class) ContentType, MediaType>? = null,
    public val links: Map<String, ReferenceOr<Link>>? = null,
    override val extensions: ExtensionProperties = null
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Response>(
            generatedSerializer(),
            { r, extensions -> r.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Response] definition. */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) : JsonSchemaInference by schemaInference {
        /** Human-readable description of the response. */
        public var description: String = ""

        /** Specification-extensions for this response (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        private val _headers = mutableMapOf<String, Header>()
        private val _content = mutableMapOf<ContentType, MediaType.Builder>()
        private val _links = mutableMapOf<String, Link>()

        /**
         * Headers for this response.
         */
        public val headers: Map<String, Header> get() = _headers

        /**
         * Content types and schemas for this response.
         */
        public val content: Map<ContentType, MediaType> get() = _content.mapValues { it.value.build() }

        /**
         * Links that can be followed from this response.
         */
        public val links: Map<String, Link> get() = _links

        /**
         * Defines response header parameters.
         *
         * @param configure DSL to define the response headers.
         */
        public fun headers(configure: Headers.Builder.() -> Unit) {
            Headers.Builder(schemaInference).apply(configure).build().headers.forEach { (name, header) ->
                _headers[name] = header
            }
        }

        /**
         * Provide a media type definition for the response body.
         *
         * This applies to all registered default content types, as defined in the ContentNegotiation plugin.
         */
        public fun content(configure: MediaType.Builder.() -> Unit) {
            for (contentType in defaultContentTypes) {
                _content.getOrPut(contentType) {
                    MediaType.Builder(schemaInference)
                }.apply(configure)
            }
        }

        /**
         * Adds a media type definition for the response body using a ContentType receiver.
         *
         * @param configure DSL to configure the [MediaType].
         */
        public operator fun ContentType.invoke(configure: MediaType.Builder.() -> Unit = {}) {
            _content.getOrPut(this) { MediaType.Builder(schemaInference) }.apply(configure)
        }

        /**
         * Convenience property to add default content with a schema.
         *
         * When reading, this will return the first registered default content type's schema.
         */
        public var schema: JsonSchema?
            get() = defaultContentTypes.firstOrNull()?.let { _content[it]?.schema }
            set(value) = content { schema = value }

        /**
         * Adds a link that can be followed from this response.
         *
         * @param name The link identifier.
         * @param configure DSL to configure the [Link].
         */
        public fun link(name: String, configure: Link.Builder.() -> Unit) {
            _links[name] = Link.Builder().apply(configure).build()
        }

        /**
         * Adds a specification-extension to this response.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /** Builds the [Response] object. */
        internal fun build(): Response {
            return Response(
                description = description,
                headers = _headers.mapValues { (_, value) -> Value(value) }.ifEmpty { null },
                content = _content.mapValues { it.value.build() }.ifEmpty { null },
                links = _links.mapValues { (_, value) -> Value(value) }.ifEmpty { null },
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

/**
 * Represents a possible design-time link for a response. The presence of a link does not guarantee
 * the caller's ability to successfully invoke it, but it does provide a known relationship and
 * traversal mechanism between responses and other operations.
 *
 * @property operationRef A relative or absolute URI reference to an OAS operation.
 * @property operationId The name of an existing, resolvable OAS operation, as defined with a unique operationId.
 * @property parameters A map of parameters to pass to the operation as specified with operationId or identified via operationRef.
 * @property requestBody A literal value or expression to use as a request body when calling the target operation.
 * @property description A description of the link.
 * @property server A server object to be used by the target operation.
 * @property extensions Specification-extensions for this link (keys must start with `x-`).
 */
@Serializable(Link.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Link(
    public val operationRef: String? = null,
    public val operationId: String? = null,
    public val parameters: ExtensionProperties = null,
    public val requestBody: GenericElement? = null,
    public val description: String? = null,
    public val server: Server? = null,
    override val extensions: ExtensionProperties = null
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Link>(
            generatedSerializer(),
            { link, extensions -> link.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Link] instance. */
    @KtorDsl
    public class Builder {
        /** A relative or absolute URI reference to an OAS operation. */
        public var operationRef: String? = null

        /** The name of an existing, resolvable OAS operation. */
        public var operationId: String? = null

        /** A map of parameters to pass to the operation. */
        public val parameters: MutableMap<String, GenericElement> = mutableMapOf()

        /** A literal value or expression to use as a request body. */
        public var requestBody: GenericElement? = null

        /** A description of the link. */
        public var description: String? = null

        /** A server object to be used by the target operation. */
        public var server: Server? = null

        /** Specification-extensions for this link (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Adds a parameter to pass to the linked operation.
         *
         * @param name The parameter name.
         * @param value The parameter value or expression.
         */
        public inline fun <reified T : Any> parameter(name: String, value: T) {
            parameters[name] = GenericElement(value)
        }

        /**
         * Adds a specification-extension to this link.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /** Builds the [Link] object. */
        internal fun build(): Link {
            require(operationRef != null || operationId != null) {
                "Either operationRef or operationId must be specified"
            }

            return Link(
                operationRef = operationRef,
                operationId = operationId,
                parameters = parameters.ifEmpty { null },
                requestBody = requestBody,
                description = description,
                server = server,
                extensions = extensions.ifEmpty { null }
            )
        }
    }
}

/**
 * A map of possible out-of band callbacks related to the parent operation. Each value in the map is
 * a [PathItem] Object that describes a set of requests that may be initiated by the API provider
 * and the expected responses. The key value used to identify the path item object is an expression,
 * evaluated at runtime, that identifies a URL to use for the callback operation.
 */
@Serializable @JvmInline
public value class Callback(public val value: Map<String, PathItem>)

/**
 * Describes the request body for an operation including content types, schemas, and whether it is required.
 *
 * @property description Optional description for the request body.
 * @property content Map of media type to [MediaType] object, describing the request body content.
 * @property required Whether the request body is required for this operation.
 * @property extensions Specification-extensions for this request body (keys must start with `x-`).
 */
@Serializable(RequestBody.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class RequestBody(
    public val description: String? = null,
    public val content: Map<@Serializable(ContentTypeSerializer::class) ContentType, MediaType>? = null,
    public val required: Boolean = false,
    override val extensions: ExtensionProperties = null
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<RequestBody>(
            generatedSerializer(),
            { rb, extensions -> rb.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [RequestBody] description. */
    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) : JsonSchemaInference by schemaInference {
        /** Optional description of the request body. */
        public var description: String? = null

        /** Whether the request body is required. */
        public var required: Boolean = false

        private val _content = mutableMapOf<ContentType, MediaType.Builder>()

        /** Map of media type to [MediaType] object. */
        public val content: Map<ContentType, MediaType> get() = _content.mapValues { it.value.build() }

        /** Specification-extensions for this request body (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Provide a media type definition for the response body.
         *
         * This applies to all registered default content types, as defined in the ContentNegotiation plugin.
         */
        public fun content(configure: MediaType.Builder.() -> Unit) {
            for (contentType in defaultContentTypes) {
                _content.getOrPut(contentType) {
                    MediaType.Builder(schemaInference)
                }.apply(configure)
            }
        }

        /**
         * Adds a media type definition for the request body using a ContentType receiver.
         *
         * @param configure DSL to configure the [MediaType].
         */
        public operator fun ContentType.invoke(configure: MediaType.Builder.() -> Unit = {}) {
            _content.getOrPut(this) { MediaType.Builder(schemaInference) }.apply(configure)
        }

        /**
         * Convenience property to add default content with the given schema.
         *
         * When reading, this will return the first registered default content type's schema.
         */
        public var schema: JsonSchema?
            get() = defaultContentTypes.firstOrNull()?.let { _content[it]?.schema }
            set(value) = content { schema = value }

        /**
         * Adds a specification-extension to this request body.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /** Builds the [RequestBody] object. */
        internal fun build(): RequestBody {
            return RequestBody(
                description = description,
                content = _content.ifEmpty { null }?.mapValues { it.value.build() },
                required = required,
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

/**
 * Represents a list of security requirements (schemes with optional scopes) for an operation.
 *
 * @property requirements The list of security requirement objects (scheme name to scopes).
 */
@Serializable
public data class Security(
    public val requirements: List<Map<String, List<String>>>
) {
    /** Builder for collecting security requirements for an operation. */
    @KtorDsl
    public class Builder {
        private val _requirements = mutableListOf<Map<String, List<String>>>()

        /** The collected list of security requirement objects. */
        public val requirements: List<Map<String, List<String>>> get() = _requirements

        /**
         * Adds a security requirement for the given [scheme] and [scopes].
         * Each call to this method creates an OR relationship (alternative).
         *
         * @param scheme The name of the security scheme.
         * @param scopes Optional list of scopes required for the scheme.
         */
        public fun requirement(scheme: String, scopes: List<String> = emptyList()) {
            _requirements.add(mapOf(scheme to scopes))
        }

        /**
         * Adds a security requirement with multiple schemes that must all be satisfied (AND relationship).
         * Use this when multiple authentication schemes must be used simultaneously.
         * Each call to this method creates an OR relationship (alternative).
         *
         * @param schemes A map of scheme names to their required scopes.
         */
        public fun requirement(schemes: Map<String, List<String>>) {
            _requirements.add(schemes)
        }

        /**
         * Marks security as optional by adding an empty requirement object.
         * This allows requests without any authentication to succeed.
         */
        public fun optional() {
            _requirements.add(emptyMap())
        }

        /** Adds an HTTP Basic authentication requirement. */
        public fun basic() {
            requirement("basicAuth")
        }

        /**
         * Adds an API key requirement using the given parameter [name].
         *
         * @param name The API key parameter name.
         */
        public fun apiKey(name: String) {
            requirement(name)
        }

        /**
         * Adds an OAuth 2 requirement with optional [scopes].
         *
         * @param scopes Optional OAuth scopes to require.
         */
        public fun oauth2(vararg scopes: String) {
            requirement("oauth2", scopes.toList())
        }

        /**
         * Adds an OpenID Connect requirement with optional [scopes].
         *
         * @param scopes Optional OpenID Connect scopes to require.
         */
        public fun openIdConnect(vararg scopes: String) {
            requirement("openIdConnect", scopes.toList())
        }

        /** Builds the [Security] object. */
        internal fun build(): Security {
            return Security(_requirements)
        }
    }
}

/**
 * A container for multiple [Server] definitions used by an operation.
 *
 * @property servers The list of server definitions.
 */
@Serializable
public data class Servers(
    public val servers: List<Server>
) {
    /** Builder for collecting [Server] entries. */
    @KtorDsl
    public class Builder {
        private val _servers = mutableListOf<Server>()

        /** Servers that can be used to service this operation. */
        public val servers: List<Server> get() = _servers

        /**
         * Adds a [Server] with the given [url].
         *
         * @param url The server URL.
         * @param configure Optional DSL to configure the server.
         */
        public fun server(url: String, configure: Server.Builder.() -> Unit = {}) {
            _servers.add(
                Server.Builder().apply {
                    this.url = url
                    configure()
                }.build()
            )
        }

        /** Builds the [Servers] list. */
        internal fun build(): Servers {
            return Servers(_servers)
        }
    }
}

/**
 * Describes a server that hosts the API.
 *
 * @property url The URL of the target host.
 * @property description Optional description of the server.
 * @property extensions Specification-extensions for this server (keys must start with `x-`).
 */
@Serializable(Server.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Server(
    public val url: String,
    public val description: String? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Server>(
            generatedSerializer(),
            { s, extensions -> s.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Server] definition. */
    public class Builder {
        /** The URL of the target host. */
        public var url: String? = null

        /** Optional description of the server. */
        public var description: String? = null

        /** Specification-extensions for this server (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Adds a specification-extension to this server.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        internal fun build(): Server {
            requireNotNull(url) { "Server URL is required" }

            return Server(
                url = url!!,
                description = description,
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

/**
 * Container for named headers attached to a response.
 *
 * @property headers Map of header name to its definition.
 */
@Serializable
public data class Headers(
    public val headers: Map<String, Header>
) {
    @KtorDsl
    public class Builder(private val schemaInference: JsonSchemaInference) {
        private val _headers = mutableMapOf<String, Header>()

        /** Map of header name to its header definition. */
        public val headers: Map<String, Header> get() = _headers

        /**
         * Adds a header definition.
         *
         * @param name Header name.
         * @param configure DSL to configure the header.
         */
        public fun header(name: String, configure: Header.Builder.() -> Unit) {
            val header = Header.Builder(schemaInference).apply(configure).build()
            _headers[name] = header
        }

        internal fun build(): Headers {
            return Headers(_headers)
        }
    }
}

/**
 * Header fields have the same meaning as for 'Param'. Style is always treated as [Style.simple], as
 * it is the only value allowed for headers.
 */
@Serializable(Header.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Header(
    /** A short description of the header. */
    public val description: String? = null,
    public val required: Boolean = false,
    public val deprecated: Boolean = false,
    public val schema: ReferenceOr<JsonSchema>? = null,
    public val content: Map<@Serializable(ContentTypeSerializer::class) ContentType, MediaType>? = null,
    public val style: String? = null,
    public val explode: Boolean? = null,
    public val example: GenericElement? = null,
    public val examples: Map<String, ReferenceOr<ExampleObject>>? = null,
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<Header>(
            generatedSerializer(),
            { h, extensions -> h.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Header] instance. */
    @KtorDsl
    public class Builder(private val schemaInference: JsonSchemaInference) : JsonSchemaInference by schemaInference {
        /** A brief description of the header. */
        public var description: String? = null

        /** Whether this header is mandatory. */
        public var required: Boolean = false

        /** Marks the header as deprecated when true. */
        public var deprecated: Boolean = false

        /** The schema defining the header type. */
        public var schema: JsonSchema? = null

        private val _content = mutableMapOf<ContentType, MediaType>()

        /** Map of media type to [MediaType] object. */
        public val content: Map<ContentType, MediaType> get() = _content

        /** Describes how the header value will be serialized (e.g., "matrix", "label", "form", "simple", "spaceDelimited", "pipeDelimited", "deepObject"). */
        public var style: String? = null

        /** Specifies whether arrays and objects generate separate headers for each value. */
        public var explode: Boolean? = null

        /** Example of the parameter's potential value. */
        public var example: GenericElement? = null

        /** Map of examples for the parameter. */
        public var examples: MutableMap<String, ReferenceOr<ExampleObject>> = mutableMapOf()

        /** Specification-extensions for this parameter (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * Adds a media type definition for the request body.
         *
         * @receiver the media type to assign
         * @param configure DSL to configure the [MediaType].
         */
        public operator fun ContentType.invoke(configure: MediaType.Builder.() -> Unit = {}) {
            _content[this] = MediaType.Builder(schemaInference).apply(configure).build()
        }

        /**
         * Adds a custom vendor-specific extension.
         *
         * @param name The extension name; must start with `x-`.
         * @param value The extension value.
         */
        public inline fun <reified T : Any> extension(name: String, value: T) {
            require(name.startsWith("x-")) { "Extension name must start with 'x-'" }
            extensions[name] = GenericElement(value)
        }

        /**
         * Adds an example for this parameter.
         *
         * @param name The example identifier.
         * @param example The example object.
         */
        public fun example(name: String, example: ExampleObject) {
            examples[name] = Value(example)
        }

        /** Constructs the [Header]. */
        internal fun build(): Header {
            return Header(
                description = description,
                required = required,
                deprecated = deprecated,
                schema = schema?.let(::Value),
                content = _content.ifEmpty { null },
                style = style,
                explode = explode,
                example = example,
                examples = examples.ifEmpty { null },
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}
