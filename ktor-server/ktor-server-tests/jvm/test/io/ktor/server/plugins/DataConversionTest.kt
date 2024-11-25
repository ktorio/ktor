/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.util.converters.*
import io.ktor.util.reflect.*
import java.math.*
import kotlin.test.*

class DataConversionTest {

    @Test
    fun testDefaultConversionBigNumbers() {
        val conversionService = DefaultConversionService
        val expected = "12345678901234567890"

        val v = conversionService.toValues(BigDecimal(expected))
        assertEquals(expected, v.single())
        assertEquals(BigDecimal(expected), conversionService.fromValues(v, typeInfo<BigDecimal>()))

        val v2 = conversionService.toValues(BigInteger(expected))
        assertEquals(expected, v2.single())
        assertEquals(BigInteger(expected), conversionService.fromValues(v2, typeInfo<BigInteger>()))
    }
}
