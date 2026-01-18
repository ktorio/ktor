/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.routing.openapi

import io.ktor.openapi.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Attribute key for including OpenAPI metadata on a [Route].
 */
public val OperationDescribeAttributeKey: AttributeKey<List<RouteOperationFunction>> =
    AttributeKey("OperationDescribe")

/**
 * Attribute key for hiding the given [Route] from OpenAPI documentation.
 */
public val OperationHiddenAttributeKey: AttributeKey<Unit> =
    AttributeKey("OperationHidden")

/**
 * Attribute key for [io.ktor.server.application.Application] JSON schema inference override.
 */
public val JsonSchemaAttributeKey: AttributeKey<JsonSchemaInference> =
    AttributeKey("JsonSchemaInference")

/**
 * Function that configures an OpenAPI [Operation].
 */
public typealias RouteOperationFunction = Operation.Builder.() -> Unit

/**
 * Annotate a [Route] with an OpenAPI [Operation].
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
 */
@ExperimentalKtorApi
public fun Route.hide(): Route {
    attributes[OperationHiddenAttributeKey] = Unit
    return this
}
