/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.server.plugins.dataconversion.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import java.math.*
import kotlin.test.*

class DataConversionTest {

    @Test
    fun testBigNumbers() = withTestApplication {
        val expected = "12345678901234567890"
        val v = server.conversionService.toValues(BigDecimal(expected))
        assertEquals(expected, v.single())
        assertEquals(BigDecimal(expected), server.conversionService.fromValues(v, typeInfo<BigDecimal>()))

        val v2 = server.conversionService.toValues(BigInteger(expected))
        assertEquals(expected, v2.single())
        assertEquals(BigInteger(expected), server.conversionService.fromValues(v2, typeInfo<BigInteger>()))
    }
}
