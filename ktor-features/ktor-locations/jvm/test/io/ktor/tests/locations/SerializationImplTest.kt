/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.locations.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.test.*

class SerializationImplTest {
    private val engine = TestApplicationEngine().apply {
        start()
    }
    private val impl = SerializationImpl(EmptyModule, engine.application, LocationAttributeRouteService())

    @AfterTest
    fun stop() {
        engine.stop(1000, 1000)
    }

    @Test
    fun smokeTest() {
        val result = impl.getOrCreateInfo(Root1::class)
        assertEquals(Root1::class, result.classRef)
        assertEquals("/root/{p}", result.path)
        assertEquals("p", result.pathParameters.single().name)
        assertEquals("p2", result.queryParameters.single().name)

        assertNull(result.parent)
        assertNull(result.parentParameter)

        assertEquals("/root/9?p2=oo", impl.href(Root1(9, "oo")))

        io.ktor.http.URLBuilder().apply {
            impl.href(Root1(10, "pp"), this)

            assertEquals("/root/10", encodedPath)
            assertEquals("pp", parameters["p2"])
        }

        val instance = impl.instantiate(result, io.ktor.http.Parameters.build {
            append("p", "111")
            append("p2", "222")
        })

        assertTrue(instance is Root1)
        assertEquals(111, instance.p)
        assertEquals("222", instance.p2)
    }

    @Test
    fun childClass() {
        impl.getOrCreateInfo(Root2::class).let { result ->
            assertEquals(Root2::class, result.classRef)
            assertEquals("/root/{p}", result.path)
            assertEquals("p", result.pathParameters.single().name)
            assertEquals(0, result.queryParameters.size)

            assertNull(result.parent)
            assertNull(result.parentParameter)
        }

        impl.getOrCreateInfo(Root2.Child1::class).let { result ->
            assertEquals(Root2.Child1::class, result.classRef)
            assertEquals("{x}", result.path)
            assertEquals("x", result.pathParameters.single().name)
            assertTrue(result.pathParameters.single().isOptional)
            assertEquals(0, result.queryParameters.size)

            assertNotNull(result.parent)
            assertNotNull(result.parentParameter)
            assertEquals("root", result.parentParameter?.name)

            assertEquals(impl.getOrCreateInfo(Root2::class), result.parent)
        }

        assertEquals("/root/5/7", impl.href(Root2.Child1(7, Root2(5))))

        io.ktor.http.URLBuilder().apply {
            impl.href(Root2.Child1(8, Root2(4)), this)

            assertEquals("/root/4/8", encodedPath)
            assertTrue(parameters.isEmpty())
        }
    }
}

@Serializable
@Location("/root/{p}")
private class Root1(val p: Int, val p2: String)

@Serializable
@Location("/root/{p}")
private class Root2(val p: Int) {
    @Serializable
    @Location("{x}")
    class Child1(val x: Int = 1, val root: Root2)
}
