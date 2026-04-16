/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/**
 * Describes the operations, parameters, and servers available for a single API path, as defined by the
 * OpenAPI Specification Path Item Object. This is a container that aggregates the HTTP operations
 * (GET, PUT, POST, DELETE, OPTIONS, HEAD, PATCH, TRACE) and common metadata that apply to the path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem)
 */
@Serializable(PathItem.Companion.Serializer::class)
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
public data class PathItem(
    /** An optional, string summary, intended to apply to all operations in this path. */
    public val summary: String? = null,
    /**
     * An optional, string description, intended to apply to all operations in this path. CommonMark
     * syntax MAY be used for rich text representation.
     */
    public val description: String? = null,
    /** A definition of a PUT operation on this path. */
    public val put: Operation? = null,
    /** A definition of a POST operation on this path. */
    public val post: Operation? = null,
    /** A definition of a DELETE operation on this path. */
    public val delete: Operation? = null,
    /** A definition of an OPTIONS operation on this path. */
    public val options: Operation? = null,
    /** A definition of a GET operation on this path. */
    public val get: Operation? = null,
    /** A definition of a HEAD operation on this path. */
    public val head: Operation? = null,
    /** A definition of a PATCH operation on this path. */
    public val patch: Operation? = null,
    /** A definition of a TRACE operation on this path. */
    public val trace: Operation? = null,
    /** An alternative server array to service all operations in this path. */
    public val servers: List<Server>? = null,
    /**
     * A list of parameters that are applicable for all the operations described under this path.
     * These parameters can be overridden at the operation level, but cannot be removed there. The
     * list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of
     * a name and location. The list can use the Reference Object to link to parameters that are
     * defined at the OpenAPI Object's components/parameters.
     */
    public val parameters: List<ReferenceOr<Parameter>>? = null,
    /** Specification extensions for this Path Item (keys MUST start with "x-"). */
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<PathItem>(
            generatedSerializer(),
            { pi, extensions -> pi.copy(extensions = extensions) }
        )
    }

    @KtorDsl
    public class Builder(
        private val schemaInference: JsonSchemaInference,
        private val defaultContentTypes: List<ContentType>,
    ) {
        /**
         * A short summary of the path item
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.summary)
         */
        public var summary: String? = null
        public var description: String? = null
        private var put: Operation? = null
        private var post: Operation? = null
        private var delete: Operation? = null
        private var options: Operation? = null
        private var get: Operation? = null
        private var head: Operation? = null
        private var patch: Operation? = null
        private var trace: Operation? = null

        private val _parameters = mutableListOf<Parameter>()
        private val _servers = mutableListOf<Server>()
        public var extensions: MutableMap<String, GenericElement> = mutableMapOf()

        /**
         * A list of parameters that are applicable for all the operations described under this path.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.parameters)
         */
        public val parameters: List<Parameter> get() = _parameters

        /**
         * A list of servers configured for this path.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.servers)
         */
        public val servers: List<Server> get() = _servers

        /**
         * Adds parameters using the [Parameters] DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.parameters)
         *
         * @param configure DSL to define one or more parameters.
         */
        public fun parameters(configure: Parameters.Builder.() -> Unit) {
            Parameters.Builder(schemaInference, defaultContentTypes).apply(configure).build().parameters.forEach {
                _parameters.add(it)
            }
        }

        /**
         * Adds server definitions specific to this operation using the [Servers] DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.servers)
         *
         * @param configure DSL to define one or more servers.
         */
        public fun servers(configure: Servers.Builder.() -> Unit) {
            Servers.Builder().apply(configure).build().servers.forEach { _servers.add(it) }
        }

        /**
         * Sets the DELETE operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.delete)
         */
        public fun delete(configure: Operation.Builder.() -> Unit) {
            delete = buildOperation(configure)
        }

        /**
         * Sets the OPTIONS operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.options)
         */
        public fun options(configure: Operation.Builder.() -> Unit) {
            options = buildOperation(configure)
        }

        /**
         * Sets the GET operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.get)
         */
        public fun get(configure: Operation.Builder.() -> Unit) {
            get = buildOperation(configure)
        }

        /**
         * Sets the HEAD operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.head)
         */
        public fun head(configure: Operation.Builder.() -> Unit) {
            head = buildOperation(configure)
        }

        /**
         * Sets the PATCH operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.patch)
         */
        public fun patch(configure: Operation.Builder.() -> Unit) {
            patch = buildOperation(configure)
        }

        /**
         * Sets the TRACE operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.trace)
         */
        public fun trace(configure: Operation.Builder.() -> Unit) {
            trace = buildOperation(configure)
        }

        /**
         * Sets the PUT operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.put)
         */
        public fun put(configure: Operation.Builder.() -> Unit) {
            put = buildOperation(configure)
        }

        /**
         * Sets the POST operation for this path item with the Operation DSL.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.openapi.PathItem.Builder.post)
         */
        public fun post(configure: Operation.Builder.() -> Unit) {
            post = buildOperation(configure)
        }

        private fun buildOperation(configure: Operation.Builder.() -> Unit): Operation =
            Operation.Builder(schemaInference, defaultContentTypes).apply(configure).build()

        public fun build(): PathItem = PathItem(
            summary = summary,
            description = description,
            put = put,
            post = post,
            delete = delete,
            options = options,
            get = get,
            head = head,
            patch = patch,
            trace = trace,
            servers = _servers.ifEmpty { null },
            parameters = _parameters.map(ReferenceOr.Companion::value).ifEmpty { null },
            extensions = extensions.ifEmpty { null },
        )
    }
}
