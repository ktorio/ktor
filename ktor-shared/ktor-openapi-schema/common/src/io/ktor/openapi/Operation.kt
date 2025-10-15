/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.http.*
import io.ktor.utils.io.KtorDsl
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/**
 * Describes a single API operation on a path item as defined by the OpenAPI Specification.
 * Use [Operation.Builder] or [Operation.build] to create instances in a type-safe DSL.
 *
 * Properties correspond to the OpenAPI Operation object.
 *
 * @property tags Optional tags for this operation.
 * @property summary A short summary of what the operation does.
 * @property description A verbose explanation of the operation behavior.
 * @property externalDocs A URL to additional external documentation for this operation.
 * @property operationId Unique string used to identify the operation.
 * @property parameters List of parameters that are applicable for this operation.
 * @property requestBody The request body applicable for this operation.
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
    public val tags: List<String>? = null,
    public val summary: String? = null,
    public val description: String? = null,
    public val externalDocs: String? = null,
    public val operationId: String? = null,
    public val parameters: List<Parameter>? = null,
    public val requestBody: RequestBody? = null,
    public val responses: Map<String, Response>? = null,
    public val deprecated: Boolean? = null,
    public val security: List<Map<String, List<String>>>? = null,
    public val servers: List<Server>? = null,
    public val extensions: Map<String, GenericElement>? = null
) {
    public companion object {
        /**
         * Builds an [Operation] instance using the provided DSL [configure] block.
         */
        public fun build(configure: Builder.() -> Unit): Operation =
            Builder().apply(configure).build()

        internal object Serializer : SerializerWithExtensions<Operation>(
            generatedSerializer(),
            { op, extensions -> op.copy(extensions = extensions) }
        )
    }

    /**
     * Builder for OpenAPI Operation object
     */
    @KtorDsl
    public class Builder {
        /** A short summary of what the operation does. */
        public var summary: String? = null

        /** A verbose explanation of the operation behavior. */
        public var description: String? = null

        /** Unique string used to identify the operation. */
        public var operationId: String? = null

        /** Marks the operation as deprecated when true. */
        public var deprecated: Boolean? = null

        /** A URL to additional external documentation for this operation. */
        public var externalDocs: String? = null

        // TODO remove these after KT-14663 is implemented
        private val _tags = mutableListOf<String>()
        private val _parameters = mutableListOf<Parameter>()
        private val _responses = mutableMapOf<String, Response>()
        private val _servers = mutableListOf<Server>()
        private val _securityRequirements = mutableListOf<Map<String, List<String>>>()

        /** Collected tags added to this operation. */
        public val tags: List<String> get() = _tags

        /** Collected parameter definitions for this operation. */
        public val parameters: List<Parameter> get() = _parameters

        /** Collected response definitions keyed by HTTP status code. */
        public val responses: Map<String, Response> get() = _responses

        /** Collected server definitions specific to this operation. */
        public val servers: List<Server> get() = _servers

        /** Declared security requirements for this operation. */
        public val securityRequirements: List<Map<String, List<String>>> get() = _securityRequirements

        /** Request body definition for this operation, if any. */
        public var requestBody: RequestBody? = null

        /** Specification-extensions for this operation (keys must start with `x-`). */
        public var extensions: MutableMap<String, GenericElement>? = null

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
            Parameters.Builder().apply(configure).build().parameters.forEach { _parameters.add(it) }
        }

        /**
         * Defines a request body for this operation using the [RequestBody] DSL.
         *
         * @param configure DSL to configure the request body.
         */
        public fun requestBody(configure: RequestBody.Builder.() -> Unit) {
            requestBody = RequestBody.Builder().apply(configure).build()
        }

        /**
         * Adds possible responses returned by this operation using the [Responses] DSL.
         *
         * @param configure DSL to define one or more responses.
         */
        public fun responses(configure: Responses.Builder.() -> Unit) {
            Responses.Builder().apply(configure).build().responses.forEach { (code, response) ->
                _responses[code] = response
            }
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
            if (extensions == null) {
                extensions = mutableMapOf()
            }
            extensions!![name] = GenericElement(value)
        }

        internal fun build(): Operation {
            return Operation(
                tags = if (_tags.isEmpty()) null else _tags,
                summary = summary,
                description = description,
                externalDocs = externalDocs,
                operationId = operationId,
                parameters = if (_parameters.isEmpty()) null else _parameters,
                requestBody = requestBody,
                responses = _responses,
                deprecated = deprecated,
                security = if (_securityRequirements.isEmpty()) null else _securityRequirements,
                servers = if (_servers.isEmpty()) null else _servers,
                extensions = extensions
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
    public class Builder {
        private val _parameters = mutableListOf<Parameter>()

        /** The collected list of parameters. */
        public val parameters: List<Parameter> get() = _parameters

        /**
         * Adds a generic [Parameter] via the provided [configure] block.
         *
         * @param configure DSL to configure the parameter.
         */
        public fun parameter(configure: Parameter.Builder.() -> Unit) {
            _parameters.add(Parameter.Builder().apply(configure).build())
        }

        /**
         * Adds a required path parameter with the given [name].
         *
         * @param name The parameter name.
         * @param configure Optional DSL to further configure the parameter.
         */
        public fun path(name: String, configure: Parameter.Builder.() -> Unit = {}) {
            Parameter.Builder().apply {
                this.name = name
                this.`in` = "path"
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
            Parameter.Builder().apply {
                this.name = name
                this.`in` = "query"
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
            Parameter.Builder().apply {
                this.name = name
                this.`in` = "header"
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
            Parameter.Builder().apply {
                this.name = name
                this.`in` = "cookie"
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
 * @property extensions Specification-extensions for this parameter (keys must start with `x-`).
 */
@Serializable(Parameter.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Parameter(
    public val name: String,
    public val `in`: String,
    public val description: String? = null,
    public val required: Boolean = false,
    public val deprecated: Boolean = false,
    public val schema: Schema? = null,
    public val extensions: Map<String, GenericElement>? = null,
) {
    public companion object {
        internal object Serializer : SerializerWithExtensions<Parameter>(
            generatedSerializer(),
            { pi, extensions -> pi.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Parameter] instance. */
    @KtorDsl
    public class Builder {
        /** The name of the parameter. */
        public var name: String? = null

        /** Location of the parameter: one of "query", "header", "path", or "cookie". */
        public var `in`: String? = null

        /** A brief description of the parameter. */
        public var description: String? = null

        /** Whether this parameter is mandatory. */
        public var required: Boolean = false

        /** Marks the parameter as deprecated when true. */
        public var deprecated: Boolean = false

        /** The schema defining the parameter type. */
        public var schema: Schema? = null

        /** Specification-extensions for this parameter (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

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

        /** Validates required fields and constructs the [Parameter]. */
        internal fun build(): Parameter {
            requireNotNull(name) { "Parameter name is required" }
            requireNotNull(`in`) { "Parameter location ('in') is required" }

            return Parameter(
                name = name!!,
                `in` = `in`!!,
                description = description,
                required = required,
                deprecated = deprecated,
                schema = schema,
                extensions = extensions,
            )
        }
    }
}

/**
 * A container for named response objects keyed by HTTP status code or "default".
 *
 * @property responses Map of status code (or "default") to a [Response].
 */
@Serializable
public data class Responses(
    public val responses: Map<String, Response>
) {
    /** Builder for collecting operation [Response]s keyed by status code. */
    @KtorDsl
    public class Builder {
        private val _responses = mutableMapOf<String, Response>()

        /** Map of HTTP status code (or "default") to a response definition. */
        public val responses: Map<String, Response> get() = _responses

        /**
         * Adds a response for the given HTTP [statusCode].
         *
         * @param statusCode The HTTP status code as a string (or "default").
         * @param configure DSL to configure the [Response].
         */
        public fun response(statusCode: String, configure: Response.Builder.() -> Unit) {
            _responses[statusCode] = Response.Builder().apply(configure).build()
        }

        /**
         * Shorthand to add a response using this [HttpStatusCode] as the key.
         *
         * @param configure Optional DSL to configure the [Response].
         */
        public operator fun HttpStatusCode.invoke(configure: Response.Builder.() -> Unit = {}) {
            response(value.toString(), configure)
        }

        /**
         * Adds the catch-all "default" response.
         *
         * @param configure DSL to configure the default [Response].
         */
        public fun default(configure: Response.Builder.() -> Unit) {
            response("default", configure)
        }

        /** Builds the [Responses] map. */
        internal fun build(): Responses {
            return Responses(_responses)
        }
    }
}

/**
 * Describes a response returned by an operation including description, headers, and payload schema.
 *
 * @property description Human-readable description of the response.
 * @property headers Optional map of response header parameters.
 * @property contentType Optional content type of the response payload.
 * @property schema Optional schema of the response payload.
 * @property extensions Specification-extensions for this response (keys must start with `x-`).
 */
@Serializable(Response.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class Response(
    public val description: String,
    public val headers: Map<String, Parameter>? = null,
    @Serializable(ContentTypeSerializer::class)
    public var contentType: ContentType? = null,
    public var schema: Schema? = null,
    public val extensions: Map<String, GenericElement>? = null
) {
    public companion object {
        internal object Serializer : SerializerWithExtensions<Response>(
            generatedSerializer(),
            { r, extensions -> r.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [Response] definition. */
    @KtorDsl
    public class Builder {
        /** Human-readable description of the response. */
        public var description: String = ""

        /** Content type of the response payload, if any. */
        public var contentType: ContentType? = null

        /** Schema of the response payload, if any. */
        public var schema: Schema? = null

        /** Specification-extensions for this response (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        private val _headers = mutableMapOf<String, Parameter>()

        /**
         * Headers for this response.
         */
        public val headers: Map<String, Parameter> get() = _headers

        /**
         * Defines response header parameters.
         *
         * @param configure DSL to define the response headers.
         */
        public fun headers(configure: Headers.Builder.() -> Unit) {
            Headers.Builder().apply(configure).build().headers.forEach { (name, header) ->
                _headers[name] = header
            }
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
                contentType = contentType,
                schema = schema,
                headers = _headers.ifEmpty { null },
                extensions = extensions.ifEmpty { null },
            )
        }
    }
}

/**
 * Describes the request body for an operation including content type, schema, and whether it is required.
 *
 * @property description Optional description for the request body.
 * @property contentType Optional content type accepted for the request body.
 * @property schema Optional schema that defines the structure of the request body.
 * @property required Whether the request body is required for this operation.
 * @property extensions Specification-extensions for this request body (keys must start with `x-`).
 */
@Serializable(RequestBody.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class RequestBody(
    public val description: String?,
    @Serializable(ContentTypeSerializer::class)
    public val contentType: ContentType?,
    public val schema: Schema?,
    public val required: Boolean,
    public val extensions: Map<String, GenericElement>?
) {
    public companion object {
        internal object Serializer : SerializerWithExtensions<RequestBody>(
            generatedSerializer(),
            { rb, extensions -> rb.copy(extensions = extensions) }
        )
    }

    /** Builder for constructing a [RequestBody] description. */
    @KtorDsl
    public class Builder {
        /** Optional description of the request body. */
        public var description: String? = null

        /** Whether the request body is required. */
        public var required: Boolean = false

        /** Content type accepted for the request body, if any. */
        public var contentType: ContentType? = null

        /** Schema that defines the structure of the request body, if any. */
        public var schema: Schema? = null

        /** Specification-extensions for this request body (keys must start with `x-`). */
        public val extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /** Builds the [RequestBody] object. */
        internal fun build(): RequestBody {
            return RequestBody(
                description = description,
                contentType = contentType,
                schema = schema,
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
         *
         * @param scheme The name of the security scheme.
         * @param scopes Optional list of scopes required for the scheme.
         */
        public fun requirement(scheme: String, scopes: List<String> = emptyList()) {
            _requirements.add(mapOf(scheme to scopes))
        }

        /** Adds a HTTP Basic authentication requirement. */
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
    public val description: String?,
    public val extensions: Map<String, GenericElement>?
) {
    public companion object {
        internal object Serializer : SerializerWithExtensions<Server>(
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
                extensions = extensions,
            )
        }
    }
}

/**
 * Container for named header parameters attached to a response.
 *
 * @property headers Map of header name to its parameter definition.
 */
@Serializable
public data class Headers(
    public val headers: Map<String, Parameter>
) {
    @KtorDsl
    public class Builder {
        private val _headers = mutableMapOf<String, Parameter>()

        /** Map of header name to its parameter definition. */
        public val headers: Map<String, Parameter> get() = _headers

        /**
         * Adds a header parameter definition.
         *
         * @param name Header name.
         * @param configure DSL to configure the header parameter.
         */
        public fun header(name: String, configure: Parameter.Builder.() -> Unit) {
            _headers[name] = Parameter.Builder().apply(configure).build()
        }

        internal fun build(): Headers {
            return Headers(_headers)
        }
    }
}
