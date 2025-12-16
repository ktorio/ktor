/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.annotate

import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.ReferenceOr.Companion.value
import io.ktor.server.auth.AuthenticateProvidersKey
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.http.content.DefaultContentTypesAttribute
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.collections.plus

/**
 * Attribute key for including OpenAPI metadata on a [Route].
 */
public val EndpointAnnotationAttributeKey: AttributeKey<List<RouteAnnotationFunction>> =
    AttributeKey("OperationDocs")

/**
 * Attribute key for JSON schema inference override.
 */
public val JsonSchemaAttributeKey: AttributeKey<JsonSchemaInference> =
    AttributeKey("JsonSchemaInference")

/**
 * Function that configures an OpenAPI [Operation].
 */
public typealias RouteAnnotationFunction = Operation.Builder.() -> Unit

/**
 * Annotate a [Route] with an OpenAPI [Operation].
 */
public fun Route.annotate(configure: RouteAnnotationFunction): Route {
    attributes[EndpointAnnotationAttributeKey] =
        when (val previous = attributes.getOrNull(EndpointAnnotationAttributeKey)) {
            null -> listOf(configure)
            else -> previous + configure
        }
    return this
}

/**
 * Generates an OpenAPI specification for the given [route].
 *
 * @param info The OpenAPI info object.
 * @param route The route to generate the specification for.
 */
public fun generateOpenApiSpec(
    info: OpenApiInfo,
    route: RoutingNode
): OpenApiSpecification {
    val jsonSchema = mutableMapOf<String, JsonSchema>()
    val pathItems = route.findPathItems(
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

    return OpenApiSpecification(
        info = info,
        paths = pathItems,
        components = Components(
            schemas = jsonSchema,
            securitySchemes = route.application.findSecuritySchemesOrRefs(useCache = true)
        ).takeIf(Components::isNotEmpty)
    )
}

/**
 * Finds all [PathItem]s under the given [RoutingNode].
 */
public fun RoutingNode.findPathItems(
    onOperation: OperationMapping = PopulateMediaTypeDefaults
): Map<String, PathItem> {
    return descendants()
        .mapNotNull { it.asPathItem(onOperation) }
        .fold(mutableMapOf()) { map, (route, pathItem) ->
            map.also {
                if (route in map) {
                    map[route] = map[route]!! + pathItem
                } else {
                    map[route] = pathItem
                }
            }
        }
}

private fun RoutingNode.asPathItem(
    onOperation: OperationMapping
): Pair<String, PathItem>? {
    if (!hasHandler()) return null
    val path = path(format = OpenApiRoutePathFormat)
    val method = method() ?: return null
    val operation = operation()?.let(onOperation::map) ?: Operation()
    val pathItem = newPathItem(method, operation) ?: return null

    return path to pathItem
}

private fun RoutingNode.method(): HttpMethod? =
    lineage()
        .map(RoutingNode::selector)
        .filterIsInstance<HttpMethodRouteSelector>()
        .firstOrNull()
        ?.method

private fun RoutingNode.operation(): Operation? {
    val schemaInference = application.attributes.getOrNull(JsonSchemaAttributeKey)
        ?: KotlinxJsonSchemaInference
    val defaultContentTypes = application.attributes.getOrNull(DefaultContentTypesAttribute)
        ?: listOf(ContentType.Application.Json)

    return lineage().fold(null) { acc, node ->
        val current = mergeNullable(
            node.operationFromAnnotateCalls(schemaInference, defaultContentTypes),
            node.operationFromSelector(),
            Operation::plus
        )
        mergeNullable(acc, current, Operation::plus)
    }
}

private fun RoutingNode.operationFromAnnotateCalls(
    schemaInference: JsonSchemaInference,
    defaultContentTypes: List<ContentType>
): Operation? = attributes.getOrNull(EndpointAnnotationAttributeKey)
    ?.map { function -> Operation.build(schemaInference, defaultContentTypes, function) }
    ?.reduce(Operation::plus)

private fun RoutingNode.operationFromSelector(): Operation? {
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

private fun RoutingNode.operationFromAuthSelector(): Operation? {
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

private fun Map<String, SecurityScheme>?.scopesFor(name: String): List<String> {
    val scheme = this?.get(name) ?: return emptyList()
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
        content = mergeNullable(content, other.content) { a, b -> b + a },
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
