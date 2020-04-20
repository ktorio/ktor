/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import java.util.*
import kotlin.collections.*
import kotlin.reflect.jvm.*
import kotlin.test.*

@OptIn(ExperimentalStdlibApi::class)
class DefaultConversionServiceTest {
    @Test
    fun smokeTest() {
        // the content depends on the particular platform
        // so we only check that it doesnt fail
        DefaultConversionService.supportedTypes()

        assertEquals(listOf("test"), DefaultConversionService.toValues("test"))
        assertEquals("test", DefaultConversionService.fromValues<String>(listOf("test")))
    }

    @Test
    fun testEmptyAndNulls() {
        assertEquals(listOf(), DefaultConversionService.toValues(null))
        assertEquals(listOf(), DefaultConversionService.toValues(listOf<Int>()))

        assertEquals(null, DefaultConversionService.fromValues<String>(emptyList()))
        assertEquals(emptyList(), DefaultConversionService.fromValues<List<String>>(emptyList()))
        assertEquals(emptyList(), DefaultConversionService.fromValues<List<Int>>(emptyList()))
    }

    @Test
    fun testSinglePrimitives() {
        assertEquals(listOf("3330"), DefaultConversionService.toValues(3330))
        assertEquals(333, DefaultConversionService.fromValues<Int>(listOf("333")))

        assertEquals(listOf("5550"), DefaultConversionService.toValues(5550L))
        assertEquals(555L, DefaultConversionService.fromValues<Long>(listOf("555")))

        assertEquals(listOf("true"), DefaultConversionService.toValues(true))
        assertEquals(listOf("false"), DefaultConversionService.toValues(false))
        assertEquals(true, DefaultConversionService.fromValues<Boolean>(listOf("true")))
        assertEquals(false, DefaultConversionService.fromValues<Boolean>(listOf("false")))
    }

    @Test
    fun testHomoTypedListsSerialization() {
        assertEquals(listOf("sss"), DefaultConversionService.toValues(listOf("sss")))
        assertEquals(listOf("sss", "s2"), DefaultConversionService.toValues(listOf("sss", "s2")))
        assertEquals(listOf("1"), DefaultConversionService.toValues(listOf(1)))
        assertEquals(listOf("2", "1"), DefaultConversionService.toValues(listOf(2, 1)))
        assertEquals(listOf("4"), DefaultConversionService.toValues(listOf(4L)))
        assertEquals(listOf("true", "false"), DefaultConversionService.toValues(listOf(true, false)))
    }

    @Test
    fun testHomoTypedListsDeserialization() {
        assertEquals(arrayListOf("sssD"), DefaultConversionService.fromValues<ArrayList<String>>(listOf("sssD")))
        assertEquals(
            LinkedList(listOf("sssD")),
            DefaultConversionService.fromValues<LinkedList<String>>(listOf("sssD"))
        )
        assertEquals(arrayListOf("sssD"), DefaultConversionService.fromValues<List<String>>(listOf("sssD")))
        assertEquals(listOf("a1", "b2"), DefaultConversionService.fromValues<List<String>>(listOf("a1", "b2")))

        assertEquals(listOf(2), DefaultConversionService.fromValues<List<Int>>(listOf("2")))
        assertEquals(listOf(3, 4), DefaultConversionService.fromValues<List<Int>>(listOf("3", "4")))

        assertEquals(listOf(9L), DefaultConversionService.fromValues<List<Long>>(listOf("9")))
        assertEquals(listOf(10L, 11L), DefaultConversionService.fromValues<List<Long>>(listOf("10", "11")))

        assertEquals(listOf(false, true), DefaultConversionService.fromValues<List<Boolean>>(listOf("false", "true")))
    }

    @Test
    fun testEnums() {
        assertEquals(listOf("A"), DefaultConversionService.toValues(E.A))
        assertEquals(listOf("B"), DefaultConversionService.toValues(E.B))

        assertEquals(E.A, DefaultConversionService.fromValues<E>(listOf("A")))
        assertEquals(E.B, DefaultConversionService.fromValues<E>(listOf("B")))
        assertEquals(listOf(E.B, E.C), DefaultConversionService.fromValues<List<E>>(listOf("B", "C")))

        assertFailsWith<DataConversionException> {
            DefaultConversionService.fromValues<E>(listOf("x"))
        }
    }

    @Test
    @Suppress("DEPRECATION_ERROR")
    fun testFromValuesJavaType() {
        assertEquals(E.A, DefaultConversionService.fromValues(listOf("A"), E::class.java))

        assertEquals(
            listOf("aaa"), DefaultConversionService.fromValues(
                listOf("aaa"),
                Token::f.javaField!!.genericType
            )
        )
    }

    enum class E {
        A, B, C
    }

    class Token {
        val f: List<String> = emptyList()
    }
}
