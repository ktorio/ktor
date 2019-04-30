/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import kotlin.test.*


class TypeInfoTest {

    @Test
    fun testClassInMethod() {
        class Foo
        typeInfo<Foo>()
    }

    @Test
    @Ignore
    fun testTypeInfoWithClassDefinedInMethodScopeWithComplexName() {
        class SomeClass
        typeInfo<SomeClass>()
    }

    @Test
    fun testEquals() {
        class Foo<Bar>
        assertEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Int>>())
    }
}
