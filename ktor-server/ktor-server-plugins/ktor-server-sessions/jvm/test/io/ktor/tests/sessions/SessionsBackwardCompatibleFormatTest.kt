/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.sessions

import io.ktor.server.sessions.*
import io.ktor.server.sessions.serialization.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*
import kotlin.test.*

class SessionsBackwardCompatibleFormatTest {

    enum class Enum { A, B }

    @Serializable
    data class Primitives(
        val int: Int,
        val string: String,
        val boolean: Boolean,
        val double: Double,
        val float: Float,
        val long: Long,
        val nullable: String?,
        val enum: Enum
    )

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun testEncoderPrimitive() {
        val format = SessionsBackwardCompatibleFormat(EmptySerializersModule())
        val primitives = Primitives(1, "2", true, 3.0, 4.0f, 5L, null, Enum.A)
        val encoded = format.encodeToString(Primitives.serializer(), primitives)
        assertEquals(
            "int=%23i1&string=%23s2&boolean=%23bot&double=%23f3.0&float=%23f4.0&long=%23l5&nullable=%23n&enum=%23sA",
            encoded
        )

        val decoded = format.decodeFromString(Primitives.serializer(), encoded)
        assertEquals(primitives, decoded)

        val reflectionSerializer = SessionSerializerReflection<Primitives>(typeOf<Primitives>())
        val decodedWithReflection = reflectionSerializer.deserialize(encoded)
        assertEquals(primitives, decodedWithReflection)
    }

    @Serializable
    data class WithCollections(
        val listS: List<String>,
        val listE: List<Enum>,
        val listI: List<Int>,
        val mapS: Map<String, String>,
        val mapI: Map<String, Int>
    )

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun testEncoderCollections() {
        val format = SessionsBackwardCompatibleFormat(EmptySerializersModule())
        val collections = WithCollections(
            listOf("1", "2"),
            listOf(Enum.A, Enum.B),
            listOf(1, 2),
            mapOf("key1" to "1", "key2" to "2"),
            mapOf("key1" to 1, "key2" to 2)
        )
        val encoded = format.encodeToString(WithCollections.serializer(), collections)
        assertEquals(
            "listS=%23cl%23s1%26%23s2" +
                "&listE=%23cl%23sA%26%23sB" +
                "&listI=%23cl%23i1%26%23i2" +
                "&mapS=%23m%23skey1%3D%23s1%26%23skey2%3D%23s2" +
                "&mapI=%23m%23skey1%3D%23i1%26%23skey2%3D%23i2",
            encoded
        )

        val decoded = format.decodeFromString(WithCollections.serializer(), encoded)
        assertEquals(collections, decoded)

        val reflectionSerializer = SessionSerializerReflection<WithCollections>(typeOf<WithCollections>())
        val decodedWithReflection = reflectionSerializer.deserialize(encoded)
        assertEquals(collections, decodedWithReflection)
    }

    @Serializable
    data class WithNestedClass(
        val primitives: Primitives,
    )

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun testNestedClass() {
        val format = SessionsBackwardCompatibleFormat(EmptySerializersModule())
        val nestedClass = WithNestedClass(
            Primitives(1, "2", true, 3.0, 4.0f, 5L, null, Enum.A)
        )
        val encoded = format.encodeToString(WithNestedClass.serializer(), nestedClass)
        assertEquals(
            "primitives=%23%23int%3D%2523i1" +
                "%26string%3D%2523s2" +
                "%26boolean%3D%2523bot" +
                "%26double%3D%2523f3.0" +
                "%26float%3D%2523f4.0" +
                "%26long%3D%2523l5" +
                "%26nullable%3D%2523n" +
                "%26enum%3D%2523sA",
            encoded
        )

        val decoded = format.decodeFromString(WithNestedClass.serializer(), encoded)
        assertEquals(nestedClass, decoded)

        val reflectionSerializer = SessionSerializerReflection<WithNestedClass>(typeOf<WithNestedClass>())
        val decodedWithReflection = reflectionSerializer.deserialize(encoded)
        assertEquals(nestedClass, decodedWithReflection)
    }
}
