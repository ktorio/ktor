package io.ktor.client.tests.features.cookies

import io.ktor.client.call.*
import kotlin.test.*


class TypeInfoTest {

    @Test
    fun classInMethodTest() {
        class Foo
        typeInfo<Foo>()
    }

    @Test
    @Ignore
    fun `type info with class defined in method scope with complex name`() {
        class SomeClass
        typeInfo<SomeClass>()
    }

    @Test
    fun equalsTest() {
        class Foo<Bar>
        assertEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Int>>())
    }
}