/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.openapi.Components
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.SecurityScheme
import io.ktor.server.routing.Route
import kotlin.collections.orEmpty
import kotlin.collections.plus

/**
 * Combines the current `OpenApiDoc` instance with a sequence of routes, resulting in a new `OpenApiDoc`
 * containing the updated paths and components based on the provided routes.
 *
 * @param routes A sequence of `Route` objects whose information is used to resolve path items and schemas.
 * @return A new `OpenApiDoc` instance with the combined paths and components from the original instance and the provided routes.
 */
public operator fun OpenApiDoc.plus(routes: Sequence<Route>): OpenApiDoc {
    val (pathItems, jsonSchema) = routes.mapToPathItemsAndSchema()
    return copy(
        paths = paths + pathItems.mapValues {
            ReferenceOr.Value(it.value)
        },
        components = components?.copy(
            schemas = jsonSchema.takeIf { it.isNotEmpty() },
        ) ?: Components(
            schemas = jsonSchema.takeIf { it.isNotEmpty() }
        ),
    )
}

/**
 * Overload for [OpenApiDoc.plus] that accepts a [Collection] of [Route]s.
 *
 * @param routes A sequence of `Route` objects whose information is used to resolve path items and schemas.
 * @return A new `OpenApiDoc` instance with the combined paths and components from the original instance and the provided routes.
 */
public operator fun OpenApiDoc.plus(routes: Collection<Route>): OpenApiDoc =
    plus(routes.asSequence())

/**
 * Combines the current [OpenApiDoc] instance with a single [Route].
 *
 * @param route An instance of `Route` that will be used to resolve path items and schemas.
 * @return A new `OpenApiDoc` instance with the combined paths and components from the original instance and the provided routes.
 */
public operator fun OpenApiDoc.plus(route: Route): OpenApiDoc = plus(sequenceOf(route))

/**
 * Combines the current [OpenApiDoc] instance with additional security schemes.
 *
 * This method updates the existing security schemes in the `components` section of the current
 * instance by merging them with the provided [securitySchemes]. If there are no existing security
 * schemes, the provided [securitySchemes] will be set as the new security schemes for the `components`.
 *
 * @param securitySchemes A map of additional security schemes to include, where the keys are
 * the scheme names and the values are references or definitions of [SecurityScheme] objects.
 * @return A new [OpenApiDoc] instance with the updated security schemes in the `components` section.
 */
public operator fun OpenApiDoc.plus(securitySchemes: Map<String, ReferenceOr<SecurityScheme>>): OpenApiDoc =
    if (securitySchemes.isEmpty()) {
        this
    } else {
        copy(
            components = components?.let { components ->
                components.copy(securitySchemes = components.securitySchemes.orEmpty() + securitySchemes)
            } ?: Components(securitySchemes = securitySchemes)
        )
    }
