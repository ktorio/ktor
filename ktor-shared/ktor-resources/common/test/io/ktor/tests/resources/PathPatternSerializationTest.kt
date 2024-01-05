/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.resources.*
import io.ktor.resources.serialization.*
import kotlin.test.*

class PathPatternSerializationTest {

    private val locationsFormat = ResourcesFormat()

    @Resource("some/path/")
    class SimplePath

    @Test
    fun testSimplePath() {
        val pathPattern = locationsFormat.encodeToPathPattern(SimplePath.serializer())
        assertEquals("some/path/", pathPattern)
    }

    @Resource("parent/{path}")
    class NestedClass {
        @Resource("{child}/path")
        data class ChildClass(val parent: NestedClass)
    }

    @Test
    fun testNestedPath() {
        val pathPattern = locationsFormat.encodeToPathPattern(NestedClass.ChildClass.serializer())
        assertEquals("parent/{path}/{child}/path", pathPattern)
    }

    @Resource("parent/{path}/")
    class NestedClassWithSlash {
        @Resource("{child}/path")
        data class ChildClassWithSlash(val parent: NestedClass)

        @Resource("/{child}/path")
        data class ChildClassWithoutSlash(val parent: NestedClass)
    }

    @Resource("parent/{path}")
    class NestedClassWithoutSlash {
        @Resource("/{child}/path")
        data class ChildClassWithSlash(val parent: NestedClass)
    }

    @Test
    fun testNestedPathDoesNotDoubleSlash() {
        val pathPattern1 = locationsFormat.encodeToPathPattern(
            NestedClassWithSlash.ChildClassWithSlash.serializer()
        )
        assertEquals("parent/{path}/{child}/path", pathPattern1)

        val pathPattern3 = locationsFormat.encodeToPathPattern(
            NestedClassWithSlash.ChildClassWithoutSlash.serializer()
        )
        assertEquals("parent/{path}/{child}/path", pathPattern3)

        val pathPattern2 = locationsFormat.encodeToPathPattern(
            NestedClassWithoutSlash.ChildClassWithSlash.serializer()
        )
        assertEquals("parent/{path}/{child}/path", pathPattern2)
    }

    @Resource("/{child}/path")
    data class Container(
        val child: MultipleParents
    )

    @Resource("/{child}/path")
    data class MultipleParents(val parent1: NestedClass, val value: String, val parent2: NestedClassWithSlash)

    @Test
    fun testMultipleNestedParentsShouldThrow() {
        assertFailsWith<ResourceSerializationException> {
            locationsFormat.encodeToPathPattern(Container.serializer())
        }.let {
            assertEquals(
                "There are multiple parents for resource " +
                    "io.ktor.tests.resources.PathPatternSerializationTest.MultipleParents",
                it.message
            )
        }
    }
}
