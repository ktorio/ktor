/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class AttributesTest {
    object Foo
    object Bar

    @Test
    fun testAttributesTypeSafety() {
        val foo = AttributeKey<Foo>("example")
        val bar = AttributeKey<Bar>("example")
        with(Attributes()) {
            put(foo, Foo)
            put(bar, Bar)

            assertEquals(Foo, get(foo))
            assertEquals(Bar, get(bar))
        }
    }
}
