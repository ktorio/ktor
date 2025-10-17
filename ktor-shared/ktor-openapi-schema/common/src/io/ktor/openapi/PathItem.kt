/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

/**
 * Describes the operations, parameters, and servers available for a single API path, as defined by the
 * OpenAPI Specification Path Item Object. This is a container that aggregates the HTTP operations
 * (GET, PUT, POST, DELETE, OPTIONS, HEAD, PATCH, TRACE) and common metadata that apply to the path.
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
    /** A definition of a GET operation on this path. */
    public val get: Operation? = null,
    /** A definition of a PUT operation on this path. */
    public val put: Operation? = null,
    /** A definition of a POST operation on this path. */
    public val post: Operation? = null,
    /** A definition of a DELETE operation on this path. */
    public val delete: Operation? = null,
    /** A definition of an OPTIONS operation on this path. */
    public val options: Operation? = null,
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
    /**
     * Any additional external documentation for this OpenAPI document.
     */
    override val extensions: ExtensionProperties = null,
) : Extensible {
    public companion object {
        internal object Serializer : ExtensibleMixinSerializer<PathItem>(
            generatedSerializer(),
            { pi, extensions -> pi.copy(extensions = extensions) }
        )
    }
}
