/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.reflect

import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(InternalAPI::class)
class TypeInfoTest {

    @Test
    fun `typeInfo preserves nullability`() {
        assertFalse(typeInfo<String>().isNullable)
        assertTrue(typeInfo<String?>().isNullable)
    }

    @Test
    fun `marked nullable type requires nullable flag`() {
        assertFailsWith<IllegalArgumentException> {
            TypeInfo(
                type = String::class,
                isNullable = false,
                kotlinType = typeOf<String?>(),
            )
        }
    }

    @Test
    fun `raw types with different nullability are not equal`() {
        val nonNullable = TypeInfo(String::class, isNullable = false)
        val nullable = TypeInfo(String::class, isNullable = true)

        assertNotEquals(nonNullable, nullable)
        assertEquals(2, setOf(nonNullable, nullable).size)
    }

    @Test
    fun `types with kotlin type and different nullability are not equal`() {
        val nonNullable = TypeInfo(String::class, isNullable = false, kotlinType = typeOf<String>())
        val nullable = TypeInfo(String::class, isNullable = true, kotlinType = typeOf<String>())

        assertNotEquals(nonNullable, nullable)
        assertEquals(2, setOf(nonNullable, nullable).size)
    }

    @OptIn(InternalAPI::class, ExperimentalSerializationApi::class)
    @Test
    fun `serializer preserves explicit nullability`() {
        val rawSerializer = TypeInfo(String::class, isNullable = true).serializer()
        val parameterizedSerializer = TypeInfo(
            type = List::class,
            isNullable = true,
            kotlinType = typeOf<List<String>>(),
        ).serializer()

        assertTrue(rawSerializer.descriptor.isNullable)
        assertTrue(parameterizedSerializer.descriptor.isNullable)
    }
}
