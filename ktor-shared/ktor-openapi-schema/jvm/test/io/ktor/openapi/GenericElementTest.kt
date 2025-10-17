/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GenericElementTest {

    @Test
    fun `empty object deserialization`() {
        assertEquals(PathItem(), GenericElement.EmptyObject.deserialize(PathItem.serializer()))
    }

    @Test
    fun petStoreSchemaTest() {
        val jsonString = GenericElementTest::class.java.getResourceAsStream("/petstore.json")!!.reader().readText()
        val petstoreJsonElement = JsonGenericElement(Json.parseToJsonElement(jsonString))
        val paths = petstoreJsonElement.entries().first { (key, _) -> key == "paths" }.second
        val pathItems = paths.deserialize(serializer<Map<String, PathItem>>())
        val getOperationIds = pathItems.values.mapNotNull { it.get?.operationId }
        assertEquals("findPets, find pet by id", getOperationIds.joinToString())
    }
}
