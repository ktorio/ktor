/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.http.*
import io.ktor.openapi.*

private val StringReference: ReferenceOr<JsonSchema> = ReferenceOr.Value(JsonSchema(type = JsonType.STRING))
private const val SchemaComponentPrefix: String = "#/components/schemas/"

/**
 * Mapping function for [Operation].
 *
 * Used in post-processing of the OpenAPI model.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OperationMapping)
 */
public fun interface OperationMapping {
    public fun map(operation: Operation): Operation

    /**
     * Combine multiple [OperationMapping] instances into a single mapping.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.OperationMapping.plus)
     *
     * @param other mapping to apply after this one.
     * @return composed mapping that applies this mapping then [other].
     */
    public operator fun plus(other: OperationMapping): OperationMapping =
        JoinedOperationMapping(listOf(this, other))
}

internal class JoinedOperationMapping(private val operations: List<OperationMapping>) : OperationMapping {
    override fun map(operation: Operation): Operation {
        var current = operation
        for (processor in operations) {
            current = processor.map(current)
        }
        return current
    }

    override fun plus(other: OperationMapping): OperationMapping =
        JoinedOperationMapping(operations + other)
}

/**
 * Populate [Parameter.content] and response [Header.content] fields with default values.
 *
 * Defaults applied:
 * - Parameters: if `in` is missing, default to `query`.
 * - Parameters/Headers: if both `schema` and `content` are missing, set `schema` to `type/string`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.PopulateMediaTypeDefaults)
 */
public val PopulateMediaTypeDefaults: OperationMapping = OperationMapping { operation ->
    val hasMissingParamMediaInfo = operation.parameters.orEmpty()
        .filterIsInstance<ReferenceOr.Value<Parameter>>()
        .any { paramRef ->
            (paramRef.value.schema == null && paramRef.value.content == null) ||
                paramRef.value.`in` == null
        }

    val hasMissingHeaderMediaInfo = run {
        val responses = operation.responses ?: return@run false
        fun ReferenceOr<Response>.hasMissingInHeaders(): Boolean {
            val headers = this.valueOrNull()?.headers ?: return false
            return headers.values.filterIsInstance<ReferenceOr.Value<Header>>()
                .any { it.value.schema == null && it.value.content == null }
        }

        (responses.default?.hasMissingInHeaders() == true) ||
            (responses.responses?.values?.any { it.hasMissingInHeaders() } == true)
    }

    if (!hasMissingParamMediaInfo && !hasMissingHeaderMediaInfo) {
        return@OperationMapping operation
    }

    operation.copy(
        // Parameter defaults
        parameters = operation.parameters?.map { ref ->
            val param = ref.valueOrNull() ?: return@map ref
            ReferenceOr.Value(
                param.copy(
                    `in` = param.`in` ?: ParameterType.query,
                    schema = param.schema ?: StringReference.takeIf { param.content == null },
                )
            )
        },
        // Response header defaults
        responses = operation.responses?.let { responses ->
            responses.copy(
                default = responses.default?.mapValue { resp ->
                    resp.copy(
                        headers = resp.headers?.mapValues { (_, headerRef) ->
                            headerRef.mapValue { header ->
                                header.copy(
                                    schema = StringReference.takeIf { header.content == null },
                                )
                            }
                        }
                    )
                },
                responses = responses.responses?.mapValues { (_, responseRef) ->
                    responseRef.mapValue { resp ->
                        resp.copy(
                            headers = resp.headers?.mapValues { (_, headerRef) ->
                                headerRef.mapValue { header ->
                                    header.copy(
                                        schema = StringReference.takeIf { header.content == null },
                                    )
                                }
                            }
                        )
                    }
                }
            )
        }
    )
}

/**
 * Replace all JSON class schema values with component references.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.openapi.CollectSchemaReferences)
 */
public class CollectSchemaReferences(private val schemaToComponent: (JsonSchema) -> String?) : OperationMapping {
    private val titleToComponent = mutableMapOf<String, String>()

    override fun map(operation: Operation): Operation =
        operation.copy(
            requestBody = operation.requestBody?.mapValue { reqBody ->
                reqBody.copy(content = reqBody.content?.let(::mapSchemaReferences))
            },
            responses = operation.responses?.let { responses ->
                responses.copy(
                    default = responses.default?.mapValue { mapSchemaReferences(it) },
                    responses = responses.responses?.mapValues { (_, response) ->
                        response.mapValue { mapSchemaReferences(it) }
                    }
                )
            },
            parameters = operation.parameters?.map { parameter ->
                parameter.mapValue { param ->
                    param.copy(
                        schema = param.schema?.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences),
                        content = param.content?.let(::mapSchemaReferences)
                    )
                }
            },
            callbacks = operation.callbacks?.mapValues { (_, callbackRef) ->
                callbackRef.mapValue { callback ->
                    Callback(
                        callback.value.mapValues { (_, pathItem) ->
                            pathItem.copy(
                                parameters = pathItem.parameters?.map { parameter ->
                                    parameter.mapValue { param ->
                                        param.copy(
                                            schema = param.schema?.mapToSchemaReference(
                                                ::mapSchemaReferences,
                                                ::mapSchemaReferences
                                            ),
                                            content = param.content?.let(::mapSchemaReferences)
                                        )
                                    }
                                },
                                get = pathItem.get?.let(::map),
                                put = pathItem.put?.let(::map),
                                post = pathItem.post?.let(::map),
                                delete = pathItem.delete?.let(::map),
                                options = pathItem.options?.let(::map),
                                head = pathItem.head?.let(::map),
                                patch = pathItem.patch?.let(::map),
                                trace = pathItem.trace?.let(::map),
                            )
                        }
                    )
                }
            }
        )

    private fun mapSchemaReferences(content: Map<ContentType, MediaType>): Map<ContentType, MediaType> =
        content.mapValues { (_, mediaType) ->
            mediaType.copy(
                schema = mediaType.schema?.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences),
            )
        }

    /**
     * We use the "title" field for referencing types to schema definitions.
     *
     * This applies a "depth-first" transformation on the schema to extract all nested references first.
     */
    private fun mapSchemaReferences(schema: JsonSchema): ReferenceOr<JsonSchema> {
        val nestedSchema = schema.copy(
            allOf = schema.allOf?.map { it.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences) },
            anyOf = schema.anyOf?.map { it.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences) },
            oneOf = schema.oneOf?.map { it.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences) },
            not = schema.not?.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences),
            properties = schema.properties?.mapValues { (_, value) ->
                value.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences)
            },
            items = schema.items?.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences),
            additionalProperties = when (val ap = schema.additionalProperties) {
                is AdditionalProperties.PSchema -> AdditionalProperties.PSchema(
                    ap.value.mapToSchemaReference(::mapSchemaReferences, ::mapSchemaReferences)
                )

                else -> ap
            },
        )
        val title = nestedSchema.title
        val componentName = schemaToComponent(nestedSchema)
        if (title != null && componentName != null) {
            titleToComponent[title] = componentName
            titleToComponent[title.substringAfterLast('.')] = componentName
        }
        return componentName
            ?.let(::schemaRef)
            ?: ReferenceOr.value(nestedSchema)
    }

    private fun mapSchemaReferences(refRef: ReferenceOr.Reference): ReferenceOr<JsonSchema> {
        if (!refRef.ref.startsWith(SchemaComponentPrefix)) return refRef
        val schemaName = refRef.ref.removePrefix(SchemaComponentPrefix)
        val targetComponent = titleToComponent[schemaName]
            ?: titleToComponent[schemaName.substringAfterLast('.')]
            ?: schemaName.substringAfterLast('.')
        return ReferenceOr.schema(targetComponent, refRef.isDynamic)
    }

    private fun mapSchemaReferences(response: Response): Response = response.copy(
        content = response.content?.let(this::mapSchemaReferences),
        headers = response.headers?.mapValues { (_, headerRef) ->
            headerRef.mapValue { header ->
                header.copy(
                    schema = header.schema?.mapToSchemaReference(
                        ::mapSchemaReferences,
                        ::mapSchemaReferences
                    ),
                    content = header.content?.let(::mapSchemaReferences)
                )
            }
        }
    )

    private fun schemaRef(title: String): ReferenceOr<JsonSchema> =
        ReferenceOr.schema(title)
}

private fun ReferenceOr<JsonSchema>.mapToSchemaReference(
    mappingFunction: (JsonSchema) -> ReferenceOr<JsonSchema>,
    referenceMapping: (ReferenceOr.Reference) -> ReferenceOr<JsonSchema>
): ReferenceOr<JsonSchema> =
    when (this) {
        is ReferenceOr.Reference -> referenceMapping(this)
        is ReferenceOr.Value -> mappingFunction(value)
    }
