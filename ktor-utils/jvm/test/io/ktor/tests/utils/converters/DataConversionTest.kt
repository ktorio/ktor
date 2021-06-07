/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.converters

import io.ktor.util.converters.*
import io.ktor.util.reflect.*
import java.math.*
import kotlin.test.*

class DataConversionTest {

    private enum class Enum { A, B }

    @Test
    fun testBigNumbers() {
        val expected = "12345678901234567890"
        val decimalValues = DefaultConversionService.toValues(BigDecimal(expected))
        assertEquals(expected, decimalValues.single())

        val decimalFromValues = DefaultConversionService.fromValues(decimalValues, typeInfo<BigDecimal>())
        assertEquals(BigDecimal(expected), decimalFromValues)

        val integerValues = DefaultConversionService.toValues(BigInteger(expected))
        assertEquals(expected, integerValues.single())

        val integerFromValues = DefaultConversionService.fromValues(integerValues, typeInfo<BigInteger>())
        assertEquals(BigInteger(expected), integerFromValues)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    @Test
    fun testDefaultConversionList() {
        val fromValues = DefaultConversionService.fromValues(listOf("1", "2"), typeInfo<List<Integer>>())
        assertEquals(listOf(1, 2), fromValues)

        val toValues = DefaultConversionService.toValues(listOf(1, 2))
        assertEquals(listOf("1", "2"), toValues)
    }

    @Test
    fun testDefaultConversionEnumList() {
        val fromValues = DefaultConversionService.fromValues(listOf("A", "B"), typeInfo<List<Enum>>())
        assertEquals(listOf(Enum.A, Enum.B), fromValues)

        val toValues = DefaultConversionService.toValues(listOf(Enum.A, Enum.B))
        assertEquals(listOf("A", "B"), toValues)
    }

    @Test
    fun testDefaultConversionEnum() {
        val converted = DefaultConversionService.fromValues(listOf("B"), typeInfo<Enum>())
        assertEquals(Enum.B, converted)

        val toValues = DefaultConversionService.toValues(converted)
        assertEquals(listOf("B"), toValues)
    }
}
