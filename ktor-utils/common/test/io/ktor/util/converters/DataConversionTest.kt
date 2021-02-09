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

    data class EntityID1(val typeId: Int, val entityId: Int)
    data class EntityID2(val typeId: String, val entityId: String)

    @Test
    fun testInstalledConversion() {
        val config = DataConversion.Configuration().apply {
            convert<EntityID1> {
                decode { values ->
                    val (typeId, entityId) = values.single().split('-').map { it.toInt() }
                    EntityID1(typeId, entityId)
                }

                encode { value -> listOf("${value.typeId}-${value.entityId}") }
            }
            convert<EntityID2> {
                decode { values ->
                    val (typeId, entityId) = values.single().split('-')
                    EntityID2(typeId, entityId)
                }

                encode { value -> listOf("${value.typeId}-${value.entityId}") }
            }
        }

        val dataConversion = DataConversion(config)

        val id1 = dataConversion.fromValues(listOf("42-999"), typeInfo<EntityID1>())
        assertEquals(EntityID1(42, 999), id1)

        val id2 = dataConversion.fromValues(listOf("42-999"), typeInfo<EntityID2>())
        assertEquals(EntityID2("42", "999"), id2)

        val converted1 = dataConversion.toValues(EntityID1(42, 999))
        assertEquals(listOf("42-999"), converted1)

        val converted2 = dataConversion.toValues(EntityID2("42", "999"))
        assertEquals(listOf("42-999"), converted2)
    }
}
