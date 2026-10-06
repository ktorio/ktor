/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

interface Companion
interface Pet : Companion
open class Animal
class Cat : Animal(), Pet
interface Groomer<out P : Pet>

@OptIn(InternalAPI::class)
class TypesTest {

    @Test
    fun supertypes() {
        assertContentEquals(
            sequenceOf(
                typeInfo<Cat>(),
                typeInfo<Animal>(),
                typeInfo<Pet>(),
                typeInfo<Companion>()
            ),
            typeInfo<Cat>().hierarchy(),
        )
    }

    @Test
    fun `raw hierarchy preserves nullability`() {
        val hierarchy = TypeInfo(Cat::class, isNullable = true).hierarchy().toList()

        assertTrue(hierarchy.isNotEmpty())
        assertTrue(hierarchy.all { it.isNullable })
    }

    @Test
    fun `hierarchy preserves nullability independent of kotlin type`() {
        val typeInfo = TypeInfo(
            type = Cat::class,
            isNullable = true,
            kotlinType = typeOf<Cat>(),
        )

        assertTrue(typeInfo.hierarchy().all { it.isNullable })
    }

    @Test
    fun `raw type converts to nullable`() {
        assertEquals(
            TypeInfo(Cat::class, isNullable = true),
            TypeInfo(Cat::class, isNullable = false).toNullable(),
        )
    }

    @Test
    fun `explicitly nullable type is not converted again`() {
        val typeInfo = TypeInfo(
            type = Cat::class,
            isNullable = true,
            kotlinType = typeOf<Cat>(),
        )

        assertNull(typeInfo.toNullable())
    }

    @Test
    fun `parameterized supertypes`() {
        assertContentEquals(
            sequenceOf(
                typeInfo<ArrayList<Cat>>(),
                typeInfo<java.util.AbstractList<Cat>>(),
                typeInfo<java.util.AbstractCollection<Cat>>(),
                typeInfo<Collection<Cat>>(),
                typeInfo<Iterable<Cat>>(),
                typeInfo<List<Cat>>(),
            ),
            typeInfo<ArrayList<Cat>>().hierarchy().take(6),
        )
    }

    @Test
    fun `multi parameterized supertypes`() {
        assertContentEquals(
            sequenceOf(
                typeInfo<LinkedHashMap<String, Cat>>(),
                typeInfo<HashMap<String, Cat>>(),
                typeInfo<java.util.AbstractMap<String, Cat>>(),
                typeInfo<Map<String, Cat>>(),
            ),
            typeInfo<LinkedHashMap<String, Cat>>().hierarchy().take(4),
        )
    }

    @Test
    fun `parameterized arg supertypes`() {
        assertContentEquals(
            sequenceOf(
                typeInfo<List<Cat>>(),
                typeInfo<List<Animal>>(),
                typeInfo<List<Pet>>(),
                typeInfo<List<Companion>>()
            ),
            typeInfo<List<Cat>>().typeParametersHierarchy(),
        )
    }

    @Test
    fun `parameterized arg supertypes boundary`() {
        assertContentEquals(
            sequenceOf(
                typeInfo<Groomer<Cat>>(),
                typeInfo<Groomer<Pet>>(),
            ),
            typeInfo<Groomer<Cat>>().typeParametersHierarchy(),
        )
    }

    @Test
    fun `multi parameterized arg supertypes`() {
        assertContentEquals(
            sequenceOf(
                typeInfo<Pair<String, Cat>>(),
                typeInfo<Pair<String, Animal>>(),
                typeInfo<Pair<String, Pet>>(),
                typeInfo<Pair<String, Companion>>(),
                typeInfo<Pair<CharSequence, Cat>>(),
                typeInfo<Pair<CharSequence, Animal>>(),
            ),
            typeInfo<Pair<String, Cat>>().typeParametersHierarchy().take(6),
        )
    }
}
