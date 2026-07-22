/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.openapi.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Attribute key for including OpenAPI metadata on a [Route].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OperationDescribeAttributeKey)
 */
public val OperationDescribeAttributeKey: AttributeKey<List<RouteOperationFunction>> =
    AttributeKey("OperationDescribe")

/**
 * Attribute key for hiding the given [Route] from OpenAPI documentation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OperationHiddenAttributeKey)
 */
public val OperationHiddenAttributeKey: AttributeKey<Unit> =
    AttributeKey("OperationHidden")

/**
 * Attribute key for the JSON schema inference override.
 *
 * The value may be set either on [io.ktor.server.application.Application.attributes] to provide
 * an application-wide default, or on any [Route]'s attributes to override the inference for a
 * subtree of routes. When resolving the inference for a given route, the nearest ancestor that
 * carries the attribute wins, falling back to the application-wide value and finally to
 * [KotlinxSerializerJsonSchemaInference.Default].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.JsonSchemaAttributeKey)
 */
public val JsonSchemaAttributeKey: AttributeKey<JsonSchemaInference> =
    AttributeKey("JsonSchemaInference")

/**
 * Function that configures an OpenAPI [Operation].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.RouteOperationFunction)
 */
public typealias RouteOperationFunction = Operation.Builder.() -> Unit

/**
 * Annotate a [Route] with an OpenAPI [Operation].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.describe)
 *
 * @param configure configures the operation metadata
 * @return the current route for chaining expressions
 */
@ExperimentalKtorApi
public fun Route.describe(configure: RouteOperationFunction): Route {
    attributes.remove(OperationHiddenAttributeKey)
    attributes[OperationDescribeAttributeKey] =
        when (val previous = attributes.getOrNull(OperationDescribeAttributeKey)) {
            null -> listOf(configure)
            else -> previous + configure
        }
    return this
}

/**
 * Hide a [Route] from OpenAPI documentation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.hide)
 *
 * @return the current route for chaining expressions
 */
@ExperimentalKtorApi
public fun Route.hide(): Route {
    attributes[OperationHiddenAttributeKey] = Unit
    return this
}
