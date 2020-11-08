/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import java.util.*
import kotlin.test.*

internal class DefaultConversionServiceTest {
    enum class A {
        B, C
    }

    private val values = listOf(1, 2f, 3.0, 4L, true, "Hello", 5.toBigInteger(), A.B, UUID.randomUUID())

    @Test
    fun fromValues() {
        values.forEach {
            val convertedValue = DefaultConversionService.toValues(it)
            val value = DefaultConversionService.fromValues(convertedValue, it.javaClass)
            assertEquals(it, value)
        }
    }

    @Test
    fun list() {
        val expected = listOf("1")
        val toActual = DefaultConversionService.toValues(listOf(1))
        assertEquals(expected, toActual)
        val fromActual = DefaultConversionService.fromValues(expected, Int::class.java)
        assertEquals(1, fromActual)
    }
}
