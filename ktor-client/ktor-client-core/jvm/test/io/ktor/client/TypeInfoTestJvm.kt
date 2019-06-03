/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client

import io.ktor.client.call.*
import kotlin.test.*

class TypeInfoTestJvm {

    @Test
    fun equalsTest() {
        class Foo<Bar>

        assertNotEquals(typeInfo<Foo<String>>(), typeInfo<Foo<Int>>())
        assertNotEquals(typeInfo<Foo<Int>>(), typeInfo<Foo<Char>>())
    }
}
