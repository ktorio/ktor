/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.http.*
import io.ktor.openapi.*

private val StringReference: ReferenceOr<JsonSchema> = ReferenceOr.Value(JsonSchema(type = JsonType.STRING))

/**
 * Mapping function for [Operation].
 *
 * Used in post-processing of the OpenAPI model.
 */
public fun interface OperationMapping {
    public fun map(operation: Operation): Operation

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
 */
public class CollectSchemaReferences(private val schemaToComponent: (JsonSchema) -> String?) : OperationMapping {
    override fun map(operation: Operation): Operation =
        operation.copy(
            requestBody = operation.requestBody?.mapValue {
                it.copy(content = it.content?.let(::collectSchemaReferences))
            },
            responses = operation.responses?.let { responses ->
                responses.copy(
                    responses = responses.responses?.mapValues { (_, response) ->
                        response.mapValue {
                            it.copy(content = it.content?.let(::collectSchemaReferences))
                        }
                    }
                )
            },
            parameters = operation.parameters?.map { parameter ->
                parameter.mapValue {
                    it.copy(
                        schema = it.schema?.mapToReference(::collectSchema),
                        content = it.content?.let(::collectSchemaReferences)
                    )
                }
            },
        )

    private fun collectSchemaReferences(content: Map<ContentType, MediaType>): Map<ContentType, MediaType> =
        content.mapValues { (_, mediaType) ->
            mediaType.copy(
                schema = mediaType.schema?.mapToReference(::collectSchema),
            )
        }

    /**
     * We use the "title" field for referencing types to schema definitions.
     */
    private fun collectSchema(schema: JsonSchema): ReferenceOr<JsonSchema> {
        return schemaToComponent(schema)?.let { ref ->
            ReferenceOr.schema(ref)
        } ?: ReferenceOr.value(
            schema.copy(
                allOf = schema.allOf?.map { it.mapToReference(::collectSchema) },
                anyOf = schema.anyOf?.map { it.mapToReference(::collectSchema) },
                oneOf = schema.oneOf?.map { it.mapToReference(::collectSchema) },
                not = schema.not?.mapToReference(::collectSchema),
                properties = schema.properties?.mapValues { (_, value) -> value.mapToReference(::collectSchema) },
                items = schema.items?.mapToReference(::collectSchema),
            )
        )
    }
}
