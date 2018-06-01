package io.ktor.tests.utils

import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class ReflectionSupertypesTest {

    @Test
    fun testSymmetricRhombus() {
        val result = B::class.java.findAllSupertypes()
        assertEquals(listOf("I", "R", "L", "B"), result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric() {
        val result = B2::class.java.findAllSupertypes()
        assertEquals(listOf("I", "M", "M2", "B2"), result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric2() {
        val result = B3::class.java.findAllSupertypes()
        assertEquals(listOf("I", "M", "M2", "B3"), result.map { it.simpleName })
    }

    interface I
    interface L : I
    interface R : I
    interface B : L, R

    interface M : I
    interface M2 : M
    interface B2 : M2, I
    interface B3 : I, M2
}