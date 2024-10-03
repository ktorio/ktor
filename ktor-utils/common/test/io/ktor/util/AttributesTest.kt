/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class AttributesTest {
    data object Foo
    data object Bar

    private val attributes = Attributes()

    @Test
    fun testAttributesTypeSafety() {
        val foo = AttributeKey<Foo>("example")
        val bar = AttributeKey<Bar>("example")
        val fooList = AttributeKey<List<Foo>>("example")
        val barList = AttributeKey<List<Bar>>("example")

        with(attributes) {
            put(foo, Foo)
            put(bar, Bar)
            put(fooList, listOf(Foo))
            put(barList, listOf(Bar))

            assertEquals(Foo, get(foo))
            assertEquals(Bar, get(bar))
            assertEquals(listOf(Foo), get(fooList))
            assertEquals(listOf(Bar), get(barList))
        }
    }

    @Test
    fun testAttributeKeyCreatedInsideGenericFunction() {
        fun <T> listKey() = AttributeKey<List<T>>("example")

        val key = listKey<Int>()
        attributes.put(key, emptyList())
        assertEquals(emptyList(), attributes[key])
    }
}
