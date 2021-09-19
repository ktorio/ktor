/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor

import io.ktor.client.features.cbor.serializer.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.serialization.cbor.*
import kotlin.test.*

@ExperimentalSerializationApi
class CollectionsSerializationTest {
    private val testSerializer = KotlinxSerializer()
    private val cbor = Cbor { }

    @Test
    fun testMapsElements() {
        val entries = listOf(
            mapOf(
                "a" to "1",
                "b" to "2"
            ),
            mapOf(
                "a" to "1",
                "b" to null
            ),
            mapOf(
                "a" to "1",
                null to "2"
            )
        )
        entries.forEach { entry ->
            assertEquals(entry, cbor.decodeFromByteArray(testSerializer.testWrite(entry)))
        }

        // this is not yet supported
        assertFails {
            testSerializer.testWrite(
                mapOf(
                    "a" to "1",
                    "b" to 2
                )
            )
        }
    }

    private fun CborSerializer.testWrite(data: Any): ByteArray =
        (write(data, ContentType.Application.Cbor) as? ByteArrayContent)?.bytes()
            ?: error("Failed to get serialized $data")
}
