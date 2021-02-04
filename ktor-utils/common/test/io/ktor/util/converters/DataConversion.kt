/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.converters

import io.ktor.util.reflect.*
import kotlin.test.*

class DataConversionTest {

    @Test
    fun testDefaultConversionNull() {
        val id = DefaultConversionService.fromValues(listOf(), typeInfo<Int>())
        assertEquals(null, id)

        val converted = DefaultConversionService.toValues(null)
        assertEquals(emptyList(), converted)
    }

    @Test
    fun testDefaultConversionInt() {
        val id = DefaultConversionService.fromValues(listOf("1"), typeInfo<Int>())
        assertEquals(1, id)

        val converted = DefaultConversionService.toValues(1)
        assertEquals(listOf("1"), converted)
    }

    @Test
    fun testDefaultConversionList() {
        val fromValues = DefaultConversionService.fromValues(listOf("1", "2"), typeInfo<List<Int>>())
        assertEquals(listOf(1, 2), fromValues)

        val toValues = DefaultConversionService.toValues(listOf(1, 2))
        assertEquals(listOf("1", "2"), toValues)
    }

    data class EntityID(val typeId: Int, val entityId: Int)

    @Test
    fun testInstalledConversion() {
        val config = DataConversion.Configuration().apply {
            convert<EntityID> {
                decode { values, _ ->
                    val (typeId, entityId) = values.single().split('-').map { it.toInt() }
                    EntityID(typeId, entityId)
                }

                encode { value ->
                    when (value) {
                        null -> listOf()
                        is EntityID -> listOf("${value.typeId}-${value.entityId}")
                        else -> throw DataConversionException("Cannot convert $value as EntityID")
                    }
                }
            }
        }

        val dataConversion = DataConversion(config)
        val id = dataConversion.fromValues(listOf("42-999"), typeInfo<EntityID>())
        assertEquals(EntityID(42, 999), id)

        val converted = dataConversion.toValues(EntityID(42, 999))
        assertEquals(listOf("42-999"), converted)
    }
}
