/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class GenericElementTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonFormat = Json {
        encodeDefaults = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun `empty object deserialization`() {
        assertEquals(PathItem(), GenericElement.EmptyObject.deserialize(PathItem.serializer()))
    }

    @Test
    fun `petstore schema test`() {
        val jsonString = readPetStoreSchema()
        val petstoreJsonElement = JsonGenericElement(jsonFormat.parseToJsonElement(jsonString))

        // check traversal of the JSON wrapper
        val paths = petstoreJsonElement.entries().first { (key, _) -> key == "paths" }.second
        val pathItems = paths.deserialize(serializer<Map<String, PathItem>>())
        val getOperationIds = pathItems.values.mapNotNull { it.get?.operationId }
        assertEquals("findPets, find pet by id", getOperationIds.joinToString())

        // check traversal and serialization using the raw implementation
        val wrappedElement = GenericElement(pathItems)
        val firstPathItem = wrappedElement.entries().first()
            .second.deserialize(serializer<PathItem>())
        assertEquals(pathItems.values.first(), firstPathItem)
        assertEquals(
            jsonFormat.encodeToString(pathItems.values.first()),
            jsonFormat.encodeToString(firstPathItem),
        )
    }

    private fun readPetStoreSchema(): String =
        GenericElementTest::class.java.getResourceAsStream("/petstore.json")!!.reader().readText()
}
