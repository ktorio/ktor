/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.annotate

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.collections.plus

/**
 * Attribute key for storing [Operation] in a [Route].
 */
public val EndpointAnnotationAttributeKey: AttributeKey<Operation> =
    AttributeKey<Operation>("operation-docs")

/**
 * Annotate a [Route] with an OpenAPI [Operation].
 */
public fun Route.annotate(configure: Operation.Builder.() -> Unit): Route {
    attributes[EndpointAnnotationAttributeKey] =
        when (val previous = attributes.getOrNull(EndpointAnnotationAttributeKey)) {
            null -> Operation.build(configure)
            else -> previous + Operation.build(configure)
        }
    return this
}

/**
 * Finds all [PathItem]s under the given [RoutingNode].
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

private fun RoutingNode.asPathItem(): Pair<String, PathItem>? {
    if (!hasHandler()) return null
    val path = path(format = OpenApiRoutePathFormat)
    val method = method() ?: return null
    val operation = operation()?.normalize() ?: Operation()
    val pathItem = newPathItem(method, operation) ?: return null

    return path to pathItem
}

private fun RoutingNode.method(): HttpMethod? =
    lineage()
        .map(RoutingNode::selector)
        .filterIsInstance<HttpMethodRouteSelector>()
        .firstOrNull()
        ?.method

private fun RoutingNode.operation(): Operation? =
    lineage().fold(null) { acc, node ->
        val current = mergeNullable(
            node.operationAttribute(),
            node.operationFromSelector(),
            Operation::plus
        )
        mergeNullable(acc, current, Operation::plus)
    }

private fun RoutingNode.operationAttribute(): Operation? =
    attributes.getOrNull(EndpointAnnotationAttributeKey)

private fun RoutingNode.operationFromSelector(): Operation? {
    return when (val paramSelector = selector) {
        is ParameterRouteSelector,
        is OptionalParameterRouteSelector -> Operation.build {
            parameters {
                query(paramSelector.name) {
                    required = paramSelector is OptionalParameterRouteSelector
                }
            }
        }
        is PathSegmentParameterRouteSelector,
        is PathSegmentOptionalParameterRouteSelector -> Operation.build {
            parameters {
                path(paramSelector.name) {
                    required = paramSelector is PathSegmentOptionalParameterRouteSelector
                }
            }
        }
        is HttpHeaderRouteSelector -> Operation.build {
            parameters {
                header(paramSelector.name) {}
            }
        }

        else -> null
    }
}

private fun newPathItem(method: HttpMethod, operation: Operation): PathItem? =
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

private operator fun Operation.plus(other: Operation): Operation =
    Operation(
        tags = mergeNullable(tags, other.tags) { a, b -> a + b },
        summary = summary ?: other.summary,
        description = description ?: other.description,
        externalDocs = externalDocs ?: other.externalDocs,
        operationId = operationId ?: other.operationId,
        parameters = mergeParameters(parameters, other.parameters),
        requestBody = requestBody ?: other.requestBody,
        responses = mergeNullable(responses, other.responses) { a, b -> a + b },
        deprecated = deprecated ?: other.deprecated,
        security = mergeNullable(security, other.security) { a, b -> a + b },
        servers = mergeNullable(servers, other.servers) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> b + a }
    )

private operator fun PathItem.plus(other: PathItem): PathItem =
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
        parameters = mergeParameters(parameters, other.parameters),
        extensions = mergeNullable(extensions, other.extensions) { a, b -> b + a }
    )

private fun mergeParameters(parameters: List<ReferenceOr<Parameter>>?, otherParameters: List<ReferenceOr<Parameter>>?) =
    mergeNullable(parameters, otherParameters) { a, b ->
        (a + b).mergeReferencesOr {
            mergeElementsBy({ name }) { a, b -> a + b }
        }
    }

private operator fun Responses.plus(other: Responses): Responses =
    Responses(
        default = default ?: other.default,
        responses = mergeNullable(responses, other.responses) { a, b ->
            val byStatusCode = (a.entries + b.entries).groupBy({ it.key }) { it.value }
            byStatusCode.map { (statusCode, responseList) ->
                // Merge responses with the same status code
                // Automatically merges response values / references
                // and takes the first one when they cannot be combined
                statusCode to responseList.mergeReferencesOr {
                    listOf(reduce { responseA, responseB -> responseA + responseB })
                }.first()
            }.toMap()
        },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> b + a }
    )

private operator fun Response.plus(other: Response): Response =
    Response(
        description = description.ifEmpty { null } ?: other.description,
        headers = mergeNullable(headers, other.headers) { a, b -> b + a },
        content = mergeNullable(content, other.content) { a, b -> b + a },
        links = mergeNullable(links, other.links) { a, b -> b + a },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> b + a }
    )

private operator fun Parameter.plus(other: Parameter): Parameter =
    Parameter(
        name = name,
        `in` = `in` ?: other.`in`,
        description = description ?: other.description,
        required = required || other.required,
        deprecated = deprecated || other.deprecated,
        schema = schema ?: other.schema,
        style = style ?: other.style,
        explode = explode ?: other.explode,
        allowReserved = allowReserved ?: other.allowReserved,
        allowEmptyValue = allowEmptyValue ?: other.allowEmptyValue,
        example = example ?: other.example,
        examples = mergeNullable(examples, other.examples) { a, b -> b + a },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> b + a }
    )

/**
 * Merges two nullable values using the provided merge function.
 * If both values are non-null, applies the merge function.
 * If only one value is non-null, returns that value.
 * If both are null, returns null.
 */
private fun <T> mergeNullable(a: T?, b: T?, merge: (T, T) -> T): T? =
    if (a != null && b != null) merge(a, b) else a ?: b

private fun <E> List<ReferenceOr<E>>.mergeReferencesOr(
    mergeNonReferences: List<E>.() -> List<E> = { this }
): List<ReferenceOr<E>> {
    val (references, nonReferences) = partition { it is ReferenceOr.Reference }
    @Suppress("UNCHECKED_CAST")
    val elements = (nonReferences as List<ReferenceOr.Value<E>>).map { it.value }
    return references.distinct() + elements.mergeNonReferences().map(::value)
}

private fun <E, K> Iterable<E>.mergeElementsBy(
    keySelector: E.() -> K,
    mergeElements: (E, E) -> E
): List<E> =
    groupBy(keySelector).map { (_, elements) ->
        elements.reduce(mergeElements)
    }

private fun <K, V> Iterable<Map.Entry<K, V>>.toMap() =
    associate { it.key to it.value }

private fun Operation.normalize(): Operation {
    val hasMissingMediaInfo = parameters.orEmpty()
        .filterIsInstance<ReferenceOr.Value<Parameter>>()
        .any { it.value.schema == null && it.value.content == null || it.value.`in` == null }
    if (!hasMissingMediaInfo) {
        return this
    }
    return copy(
        parameters = parameters?.map { ref ->
            val param = ref.valueOrNull() ?: return@map ref
            ReferenceOr.Value(
                param.copy(
                    `in` = param.`in` ?: ParameterType.query,
                    content = param.content ?: MediaType.Text.takeIf { param.schema == null },
                )
            )
        }
    )
}
