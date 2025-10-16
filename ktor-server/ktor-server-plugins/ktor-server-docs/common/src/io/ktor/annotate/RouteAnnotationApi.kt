/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.annotate

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Attribute key for storing [Operation] in a [Route].
 */
public val EndpointAnnotationAttributeKey: AttributeKey<Operation> =
    AttributeKey<Operation>("operation-docs")

/**
 * Annotate a [Route] with an OpenAPI [Operation].
 */
public fun Route.annotate(configure: Operation.Builder.() -> Unit): Route {
    attributes[EndpointAnnotationAttributeKey] = Operation.build(configure)
    return this
}

/**
 * Finds all [PathItem]s in the [RoutingNode] and its descendants.
 */
public fun RoutingNode.findPathItems(): Map<String, PathItem> =
    descendants()
        .mapNotNull(RoutingNode::asPathItem)
        .fold(mutableMapOf()) { map, (route, pathItem) ->
            map.also {
                if (route in map) {
                    map[route] = map[route]!! + pathItem
                } else {
                    map[route] = pathItem
                }
            }
        }

internal fun RoutingNode.asPathItem(): Pair<String, PathItem>? {
    if (!hasHandler()) return null
    val path = path()
    val method = method() ?: return null
    val operation = operation() ?: Operation()
    val pathItem = newPathItem(method, operation) ?: return null

    return path to pathItem
}

internal fun RoutingNode.path(): String =
    lineage().toList()
        .asReversed()
        .mapNotNull { it.selector.segmentOrNull() }
        .fold("") { acc, seg ->
            when {
                acc.isEmpty() -> seg
                seg.isEmpty() -> acc
                acc.endsWith('/') || seg.startsWith('/') -> "$acc$seg"
                else -> "$acc/$seg"
            }
        }
        .let {
            if (it.isEmpty()) {
                "/"
            } else if (it.startsWith("/")) {
                it
            } else {
                "/$it"
            }
        }

internal fun RouteSelector.segmentOrNull(): String? =
    when (this) {
        is RoutePathComponent -> pathString()
        is CompositeRouteSelector -> subSelectors()
            .filterIsInstance<RoutePathComponent>()
            .singleOrNull()?.pathString()
        else -> null
    }

internal fun RoutingNode.method(): HttpMethod? =
    lineage()
        .map(RoutingNode::selector)
        .filterIsInstance<HttpMethodRouteSelector>()
        .firstOrNull()
        ?.method

internal fun RoutingNode.operation(): Operation? =
    lineage().fold(null) { acc, node ->
        val current = mergeNullable(
            node.operationAttribute(),
            node.operationFromSelector(),
            Operation::plus
        )
        mergeNullable(acc, current, Operation::plus)
    }

internal fun RoutingNode.operationAttribute(): Operation? =
    attributes.getOrNull(EndpointAnnotationAttributeKey)

internal fun RoutingNode.operationFromSelector(): Operation? {
    return when (val paramSelector = selector) {
        is ParameterRouteSelector,
        is OptionalParameterRouteSelector -> Operation.Companion.build {
            parameters {
                query(paramSelector.name) {
                    required = paramSelector is OptionalParameterRouteSelector
                }
            }
        }
        is PathSegmentParameterRouteSelector,
        is PathSegmentOptionalParameterRouteSelector -> Operation.Companion.build {
            parameters {
                path(paramSelector.name) {
                    required = paramSelector is PathSegmentOptionalParameterRouteSelector
                }
            }
        }
        is HttpHeaderRouteSelector -> Operation.Companion.build {
            parameters {
                header(paramSelector.name) {}
            }
        }

        else -> null
    }
}

internal fun newPathItem(method: HttpMethod, operation: Operation): PathItem? =
    when (method) {
        HttpMethod.Get -> PathItem(get = operation)
        HttpMethod.Post -> PathItem(post = operation)
        HttpMethod.Put -> PathItem(put = operation)
        HttpMethod.Delete -> PathItem(delete = operation)
        HttpMethod.Head -> PathItem(head = operation)
        HttpMethod.Options -> PathItem(options = operation)
        HttpMethod.Patch -> PathItem(patch = operation)
        HttpMethod.Trace -> PathItem(trace = operation)
        else -> null
    }

internal operator fun Operation.plus(other: Operation): Operation =
    Operation(
        tags = mergeNullable(tags, other.tags) { a, b -> a + b },
        summary = summary ?: other.summary,
        description = description ?: other.description,
        externalDocs = externalDocs ?: other.externalDocs,
        operationId = operationId ?: other.operationId,
        parameters = mergeNullable(parameters, other.parameters) { a, b -> a + b },
        requestBody = requestBody ?: other.requestBody,
        responses = mergeNullable(responses, other.responses) { a, b -> a + b },
        deprecated = deprecated ?: other.deprecated,
        security = mergeNullable(security, other.security) { a, b -> a + b },
        servers = mergeNullable(servers, other.servers) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

internal operator fun PathItem.plus(other: PathItem): PathItem =
    PathItem(
        summary = summary ?: other.summary,
        get = get ?: other.get,
        put = put ?: other.put,
        post = post ?: other.post,
        delete = delete ?: other.delete,
        options = options ?: other.options,
        head = head ?: other.head,
        patch = patch ?: other.patch,
        trace = trace ?: other.trace,
        servers = mergeNullable(servers, other.servers) { a, b -> a + b },
        parameters = mergeNullable(parameters, other.parameters) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

internal operator fun Responses.plus(other: Responses) =
    Responses(
        default = default ?: other.default,
        responses = mergeNullable(responses, other.responses) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

internal fun <T> mergeNullable(a: T?, b: T?, merge: (T, T) -> T): T? =
    when {
        a == null -> b
        b == null -> a
        else -> merge(a, b)
    }
