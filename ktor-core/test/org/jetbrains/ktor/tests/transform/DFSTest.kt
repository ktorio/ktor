package org.jetbrains.ktor.tests.transform

import org.jetbrains.ktor.transform.*
import org.junit.*

class DFSTest {

    @Test
    fun testSymmetricRhombus() {
        val result = dfs<B>()
        println(result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric() {
        val result = dfs<B2>()
        println(result.map { it.simpleName })
    }

    @Test
    fun testAsymmetric2() {
        val result = dfs<B3>()
        println(result.map { it.simpleName })
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