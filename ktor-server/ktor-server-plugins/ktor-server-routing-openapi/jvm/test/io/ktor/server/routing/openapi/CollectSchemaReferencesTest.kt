/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing.openapi

import io.ktor.http.*
import io.ktor.openapi.*
import kotlin.test.*

class CollectSchemaReferencesTest {

    @Test
    fun `schema references on recursive properties`() {
        val operation = Operation(
            parameters = listOf(
                ReferenceOr.Value(
                    Parameter(
                        name = "param",
                        `in` = ParameterType.query,
                        schema = ReferenceOr.Value(
                            JsonSchema(
                                type = JsonType.OBJECT,
                                title = "com.acme.openapi.Value",
                                properties = mapOf(
                                    "name" to ReferenceOr.Value(JsonSchema(type = JsonType.STRING)),
                                    "children" to ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.ARRAY,
                                            items = ReferenceOr.Reference(
                                                "#/components/schemas/com.acme.openapi.Value"
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            requestBody = ReferenceOr.Value(
                RequestBody(
                    content = mapOf(
                        ContentType.Application.Json to MediaType(
                            schema = ReferenceOr.Value(
                                JsonSchema(
                                    type = JsonType.OBJECT,
                                    title = "com.acme.openapi.Value",
                                    properties = mapOf(
                                        "name" to ReferenceOr.Value(JsonSchema(type = JsonType.STRING)),
                                        "children" to ReferenceOr.Value(
                                            JsonSchema(
                                                type = JsonType.ARRAY,
                                                items = ReferenceOr.Reference(
                                                    "#/components/schemas/com.acme.openapi.Value"
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            ),
            responses = Responses(
                default = ReferenceOr.Value(
                    Response(
                        description = "Default response",
                        headers = mapOf(
                            "X-Header" to ReferenceOr.Value(
                                Header(
                                    schema = ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.STRING,
                                            title = "com.acme.openapi.HeaderType"
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),
                responses = mapOf(
                    200 to ReferenceOr.Value(
                        Response(
                            description = "Success",
                            content = mapOf(
                                ContentType.Application.Json to MediaType(
                                    schema = ReferenceOr.Value(
                                        JsonSchema(
                                            type = JsonType.OBJECT,
                                            title = "com.acme.openapi.Value"
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val collector = CollectSchemaReferences { schema ->
            schema.title?.substringAfterLast('.')
        }

        val mapped = collector.map(operation)

        // Verify parameter schema is referenced
        val paramSchema = mapped.parameters?.get(0)?.valueOrNull()?.schema
        assertNotNull(paramSchema)
        assertTrue(paramSchema is ReferenceOr.Reference)
        assertEquals("#/components/schemas/Value", paramSchema.ref)

        // Verify requestBody schema is referenced
        val reqBodyContent = mapped.requestBody?.valueOrNull()?.content?.get(ContentType.Application.Json)
        assertNotNull(reqBodyContent)
        val reqBodySchemaRef = reqBodyContent.schema
        assertNotNull(reqBodySchemaRef)
        assertTrue(reqBodySchemaRef is ReferenceOr.Reference)
        assertEquals("#/components/schemas/Value", reqBodySchemaRef.ref)

        // Verify responses default header schema
        val defaultResp = mapped.responses?.default?.valueOrNull()
        assertNotNull(defaultResp)
        val headerSchema = defaultResp.headers?.get("X-Header")?.valueOrNull()?.schema
        assertNotNull(headerSchema)
        assertTrue(headerSchema is ReferenceOr.Reference)
        assertEquals("#/components/schemas/HeaderType", headerSchema.ref)
    }

    @Test
    fun `recursive reference substitution`() {
        val schemaWithRef = JsonSchema(
            type = JsonType.OBJECT,
            title = "com.acme.openapi.Value",
            properties = mapOf(
                "children" to ReferenceOr.Value(
                    JsonSchema(
                        type = JsonType.ARRAY,
                        items = ReferenceOr.Reference(
                            "#/components/schemas/com.acme.openapi.Value"
                        )
                    )
                )
            )
        )

        val collector = CollectSchemaReferences { schema ->
            schema.title?.substringAfterLast('.')
        }

        val containerSchema = JsonSchema(
            type = JsonType.OBJECT,
            title = "com.acme.openapi.Container",
            properties = mapOf(
                "value" to ReferenceOr.Value(schemaWithRef)
            )
        )

        val opContainer = Operation(
            requestBody = ReferenceOr.Value(
                RequestBody(
                    content = mapOf(
                        ContentType.Application.Json to MediaType(
                            schema = ReferenceOr.Value(containerSchema)
                        )
                    )
                )
            )
        )

        val mappedContainer = collector.map(opContainer)
        val containerRef = mappedContainer.requestBody?.valueOrNull()?.content?.get(
            ContentType.Application.Json
        )?.schema
        assertNotNull(containerRef)
        assertTrue(containerRef is ReferenceOr.Reference)
        assertEquals("#/components/schemas/Container", containerRef.ref)
    }
}
