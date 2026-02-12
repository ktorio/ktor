/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

/**
 * Finds all [PathItem]s under the given [Route], and extracts object schema as references.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.mapToPathItemsAndSchema)
 */
public fun Sequence<Route>.mapToPathItemsAndSchema(): Pair<Map<String, PathItem>, Map<String, JsonSchema>> {
    val jsonSchema = mutableMapOf<String, JsonSchema>()
    val pathItems = mapToPathItems(
        PopulateMediaTypeDefaults + CollectSchemaReferences { schema ->
            val title = schema.title ?: return@CollectSchemaReferences null
            val unqualifiedTitle = title.substringAfterLast('.')
            val existingTitle = jsonSchema[unqualifiedTitle]?.title ?: title
            // if the shortened title is already in use, use the full title instead
            if (existingTitle != title) {
                jsonSchema[title] = schema
                title
            } else {
                jsonSchema[unqualifiedTitle] = schema
                unqualifiedTitle
            }
        }
    )
    return pathItems to jsonSchema
}

/**
 * Converts the sequence of [Route]s to a map of [String] to [PathItem]s.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.mapToPathItems)
 */
public fun Sequence<Route>.mapToPathItems(
    onOperation: OperationMapping = PopulateMediaTypeDefaults
): Map<String, PathItem> {
    return mapNotNull { node ->
        val isHidden = node.lineage()
            .any { OperationHiddenAttributeKey in it.attributes }
        if (isHidden) {
            return@mapNotNull null
        }
        node.asPathItem(onOperation)
    }.fold(mutableMapOf()) { map, (route, pathItem) ->
        map.also {
            if (route in map) {
                map[route] = map[route]!! + pathItem
            } else {
                map[route] = pathItem
            }
        }
    }
}

private fun Route.asPathItem(
    onOperation: OperationMapping
): Pair<String, PathItem>? {
    if (this !is RoutingNode || !this.hasHandler()) return null
    val path = path(format = OpenApiRoutePathFormat)
    val method = method() ?: return null
    val operation = operation()?.let(onOperation::map) ?: Operation()
    val pathItem = newPathItem(method, operation) ?: return null

    return path to pathItem
}

private fun Route.method(): HttpMethod? =
    lineage()
        .mapNotNull(Route::selector)
        .filterIsInstance<HttpMethodRouteSelector>()
        .firstOrNull()
        ?.method

private fun Route.operation(): Operation? {
    val schemaInference = application.attributes.getOrNull(JsonSchemaAttributeKey)
        ?: KotlinxSerializerJsonSchemaInference.Default
    val defaultContentTypes = application.attributes.getOrNull(DefaultContentTypesAttribute)
        ?: listOf(ContentType.Application.Json)

    // Merge operations from top to bottom, selectors to describe calls
    return lineage().toList().asReversed().fold(null) { acc: Operation?, node: Route ->
        val current = mergeNullable(
            node.operationFromSelector(),
            node.operationFromDescribeCalls(schemaInference, defaultContentTypes),
            Operation::plus,
        )
        mergeNullable(acc, current, Operation::plus)
    }
}

private fun Route.operationFromDescribeCalls(
    schemaInference: JsonSchemaInference,
    defaultContentTypes: List<ContentType>
): Operation? = attributes.getOrNull(OperationDescribeAttributeKey)
    ?.map { function -> Operation.build(schemaInference, defaultContentTypes, function) }
    ?.reduce(Operation::plus)

private fun Route.operationFromSelector(): Operation? {
    return when (val paramSelector = selector) {
        is ParameterRouteSelector,
        is OptionalParameterRouteSelector -> Operation.build {
            parameters {
                query(paramSelector.name) {
                    required = paramSelector !is OptionalParameterRouteSelector
                }
            }
        }

        is PathSegmentParameterRouteSelector,
        is PathSegmentOptionalParameterRouteSelector -> Operation.build {
            parameters {
                path(paramSelector.name) {
                    required = paramSelector !is PathSegmentOptionalParameterRouteSelector
                }
            }
        }

        is HttpHeaderRouteSelector -> Operation.build {
            parameters {
                header(paramSelector.name) {}
            }
        }

        else -> if (application.isAuthPluginInstalled) operationFromAuthSelector() else null
    }
}

@OptIn(InternalAPI::class)
private fun Route.operationFromAuthSelector(): Operation? {
    val paramSelector = selector as? AuthenticationRouteSelector
        ?: return null
    val globalSchemes = application.findSecuritySchemes(useCache = true)
    val registration = attributes.getOrNull(AuthenticateProvidersKey)
    val strategy = registration?.strategy ?: AuthenticationStrategy.FirstSuccessful

    return Operation.build {
        security {
            fun firstSuccessful() {
                // At least one of the schemes must succeed (OR relationship)
                for (providerName in paramSelector.names) {
                    val schemeName = providerName ?: AuthenticationRouteSelector.DEFAULT_NAME
                    requirement(schemeName, scopes = globalSchemes.scopesFor(schemeName))
                }
            }

            when (strategy) {
                AuthenticationStrategy.Optional -> {
                    firstSuccessful()
                    optional()
                }

                AuthenticationStrategy.FirstSuccessful -> {
                    firstSuccessful()
                }

                AuthenticationStrategy.Required -> {
                    // All schemes must be satisfied (AND relationship)
                    val schemes = buildMap {
                        for (providerName in paramSelector.names) {
                            val schemeName = providerName ?: AuthenticationRouteSelector.DEFAULT_NAME
                            set(schemeName, globalSchemes.scopesFor(schemeName))
                        }
                    }
                    requirement(schemes)
                }
            }
        }
    }
}

private fun Map<String, ReferenceOr<SecurityScheme>>?.scopesFor(name: String): List<String> {
    val scheme = this?.get(name)?.valueOrNull() ?: return emptyList()
    return if (scheme is OAuth2SecurityScheme) {
        val (implicit, password, clientCredentials, authorizationCode) = scheme.flows
            ?: return emptyList()
        when {
            authorizationCode != null -> authorizationCode.scopes?.keys
            clientCredentials != null -> clientCredentials.scopes?.keys
            implicit != null -> implicit.scopes?.keys
            password != null -> password.scopes?.keys
            else -> null
        }?.toList()
    } else {
        null
    } ?: emptyList()
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
        tags = mergeNullable(tags, other.tags) { a, b -> (a + b).distinct() },
        summary = other.summary?.takeIf { it.isNotEmpty() } ?: summary?.takeIf { it.isNotEmpty() } ?: "",
        description = other.description ?: description,
        externalDocs = other.externalDocs ?: externalDocs,
        operationId = other.operationId ?: operationId,
        parameters = mergeParameters(parameters, other.parameters),
        requestBody = other.requestBody ?: requestBody,
        responses = mergeNullable(responses, other.responses) { a, b -> a + b },
        deprecated = other.deprecated ?: deprecated,
        security = mergeNullable(security, other.security) { a, b -> a + b },
        servers = mergeNullable(servers, other.servers) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

private operator fun PathItem.plus(other: PathItem): PathItem =
    PathItem(
        summary = other.summary ?: summary,
        get = other.get ?: get,
        put = other.put ?: put,
        post = other.post ?: post,
        delete = other.delete ?: delete,
        options = other.options ?: options,
        head = other.head ?: head,
        patch = other.patch ?: patch,
        trace = other.trace ?: trace,
        servers = mergeNullable(servers, other.servers) { a, b -> a + b },
        parameters = mergeParameters(parameters, other.parameters),
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

private fun mergeParameters(parameters: List<ReferenceOr<Parameter>>?, otherParameters: List<ReferenceOr<Parameter>>?) =
    mergeNullable(parameters, otherParameters) { a, b ->
        (a + b).mergeReferencesOr {
            mergeElementsBy({ name }) { a, b -> a + b }
        }
    }

private operator fun Responses.plus(other: Responses): Responses =
    Responses(
        default = other.default ?: default,
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
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

private operator fun Response.plus(other: Response): Response =
    Response(
        description = other.description.ifEmpty { null } ?: description,
        headers = mergeNullable(headers, other.headers) { a, b -> a + b },
        content = mergeNullable(content, other.content) { a, b -> a + b },
        links = mergeNullable(links, other.links) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
    )

private operator fun Parameter.plus(other: Parameter): Parameter =
    Parameter(
        name = name,
        `in` = `in` ?: other.`in`,
        description = other.description ?: description,
        required = required || other.required,
        deprecated = deprecated || other.deprecated,
        schema = other.schema ?: schema,
        content = mergeNullable(content, other.content) { a, b -> a + b },
        style = other.style ?: style,
        explode = other.explode ?: explode,
        allowReserved = other.allowReserved ?: allowReserved,
        allowEmptyValue = other.allowEmptyValue ?: allowEmptyValue,
        example = other.example ?: example,
        examples = mergeNullable(examples, other.examples) { a, b -> a + b },
        extensions = mergeNullable(extensions, other.extensions) { a, b -> a + b }
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
