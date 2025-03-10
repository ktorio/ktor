/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

import io.ktor.util.reflect.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

interface Companion
interface Pet : Companion
open class Animal
class Cat : Animal(), Pet

class TypesTest {

    @Test
    fun supertypes() {
        val supertypes = typeInfo<Cat>().hierarchy()
        assertContains(supertypes, typeInfo<Animal>())
        assertContains(supertypes, typeInfo<Pet>())
        assertContains(supertypes, typeInfo<Companion>())
        assertFalse("Should not contain Any") { typeInfo<Any>() in supertypes }
    }

    @Test
    fun parameterizedSupertypes() {
        val supertypes = typeInfo<ArrayList<Cat>>().hierarchy()
        assertContains(supertypes, typeInfo<List<Cat>>())
        assertContains(supertypes, typeInfo<Collection<Cat>>())
    }

    @Test
    fun multiParameterizedSupertypes() {
        val supertypes = typeInfo<LinkedHashMap<String, Cat>>().hierarchy()
        assertContains(supertypes, typeInfo<HashMap<String, Cat>>())
        assertContains(supertypes, typeInfo<Map<String, Cat>>())
    }
}
