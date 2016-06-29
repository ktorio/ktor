package org.jetbrains.ktor.tests.transform

import org.jetbrains.ktor.transform.*
import org.junit.*
import kotlin.test.*

class DFSTest {

    @Test
    fun testSymmetricRhombus() {
        val result = dfs<B>()
        assertEquals(listOf("I", "R", "L", "B"), result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric() {
        val result = dfs<B2>()
        assertEquals(listOf("I", "M", "M2", "B2"), result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric2() {
        val result = dfs<B3>()
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