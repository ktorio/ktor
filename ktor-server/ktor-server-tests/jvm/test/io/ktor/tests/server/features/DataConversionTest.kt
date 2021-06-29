/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.features

import io.ktor.server.application.*
import io.ktor.server.features.*
import io.ktor.server.testing.*
import io.ktor.util.reflect.*
import java.math.*
import kotlin.test.*

class DataConversionTest {
    @Test
    fun testDefaultConversion() = withTestApplication {
        val id = application.conversionService.fromValues(listOf("1"), typeInfo<Int>())
        assertEquals(1, id)
    }

    private val expectedList = listOf(1, 2)

    @Test
    fun testDefaultConversionList() = withTestApplication {
        val type = typeInfo<List<Int>>()
        val id = application.conversionService.fromValues(listOf("1", "2"), type)
        assertEquals(expectedList, id)
    }

    @Test
    fun testBigNumbers() = withTestApplication {
        val expected = "12345678901234567890"
        val v = application.conversionService.toValues(BigDecimal(expected))
        assertEquals(expected, v.single())
        assertEquals(BigDecimal(expected), application.conversionService.fromValues(v, typeInfo<BigDecimal>()))

        val v2 = application.conversionService.toValues(BigInteger(expected))
        assertEquals(expected, v2.single())
        assertEquals(BigInteger(expected), application.conversionService.fromValues(v2, typeInfo<BigInteger>()))
    }

    data class EntityID(val typeId: Int, val entityId: Int)

    @Test
    fun testInstalledConversion() = withTestApplication {
        application.install(DataConversion) {
            convert<EntityID> {
                decode { values ->
                    val (typeId, entityId) = values.single().split('-').map { it.toInt() }
                    EntityID(typeId, entityId)
                }

                encode { value -> listOf("${value.typeId}-${value.entityId}") }
            }
        }

        val id = application.conversionService.fromValues(listOf("42-999"), typeInfo<EntityID>())
        assertEquals(EntityID(42, 999), id)
    }
}
