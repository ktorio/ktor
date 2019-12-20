/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.*
import kotlin.test.*

class DropLeadingTopDirsTest {
    @Test
    fun testEmptyPath() {
        assertEquals(0, dropLeadingTopDirs(""))
        assertEquals(0, dropLeadingTopDirs(" "))
        assertEquals(1, dropLeadingTopDirs("/"))
        assertEquals(1, dropLeadingTopDirs("\\"))
    }

    @Test
    fun testSingleDot() {
        assertEquals(1, dropLeadingTopDirs("."))
        assertEquals(2, dropLeadingTopDirs("./"))
        assertEquals(2, dropLeadingTopDirs(".\\"))
    }

    @Test
    fun testSingleTopDir() {
        assertEquals(2, dropLeadingTopDirs(".."))
        assertEquals(3, dropLeadingTopDirs("../a"))
        assertEquals(3, dropLeadingTopDirs("..\\a"))
    }

    @Test
    fun testDoubleTopDir() {
        assertEquals(5, dropLeadingTopDirs("../.."))
        assertEquals(5, dropLeadingTopDirs("..\\.."))
        assertEquals(6, dropLeadingTopDirs("../../"))
        assertEquals(6, dropLeadingTopDirs("..\\..\\"))
    }

    @Test
    fun testMultipleDots() {
        assertEquals(1, dropLeadingTopDirs("."))
        assertEquals(2, dropLeadingTopDirs(".."))
        assertEquals(0, dropLeadingTopDirs("..."))
        assertEquals(0, dropLeadingTopDirs("...."))
        assertEquals(0, dropLeadingTopDirs("....."))
    }

    @Test
    fun testLeadingPathSeparators() {
        assertEquals(1, dropLeadingTopDirs("/a/b/c"))
        assertEquals(1, dropLeadingTopDirs("/"))
        assertEquals(2, dropLeadingTopDirs("//"))
        assertEquals(3, dropLeadingTopDirs("///"))
        assertEquals(4, dropLeadingTopDirs(".///"))
        assertEquals(4, dropLeadingTopDirs(".///a"))
        assertEquals(0, dropLeadingTopDirs("z///a"))
    }

    @Test
    fun testPathElementStartingWithSingleDot() {
        assertEquals(0, dropLeadingTopDirs(".a"))
    }

    @Test
    fun testPathElementStartingWithTwoDots() {
        assertEquals(0, dropLeadingTopDirs("..a"))
    }
}
