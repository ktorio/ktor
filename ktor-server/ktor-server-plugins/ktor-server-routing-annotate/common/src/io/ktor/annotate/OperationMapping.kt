/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.annotate

import io.ktor.http.*
import io.ktor.openapi.*

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
 * Populate [Parameter.content] fields with default values.
 */
public val PopulateMediaTypeDefaults: OperationMapping = OperationMapping { operation ->
    val hasMissingMediaInfo = operation.parameters.orEmpty()
        .filterIsInstance<ReferenceOr.Value<Parameter>>()
        .any { it.value.schema == null && it.value.content == null || it.value.`in` == null }
    if (!hasMissingMediaInfo) {
        return@OperationMapping operation
    }

    operation.copy(
        parameters = operation.parameters?.map { ref ->
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
                oneOf = schema.oneOf?.map { it.mapToReference(::collectSchema) },
                not = schema.not?.mapToReference(::collectSchema),
                properties = schema.properties?.mapValues { (_, value) -> value.mapToReference(::collectSchema) },
                items = schema.items?.mapToReference(::collectSchema),
            )
        )
    }
}
