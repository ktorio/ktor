/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.locations

import io.ktor.http.*
import io.ktor.locations.*
import kotlin.test.*

class LocationPatternTest {
    @Test
    fun smokeTests() {
        LocationPattern("")
        LocationPattern("/")
        LocationPattern("/a")
        LocationPattern("/a/")
        LocationPattern("/a/b")
        LocationPattern("/a/b/c")
        LocationPattern("/a/{b}/c")
        LocationPattern("/a/b/{c}")
        LocationPattern("/a/b/{c}/")
        LocationPattern("/a/b/{c...}/")
        LocationPattern("/a/b/{caa...}/")
        LocationPattern("{aaa}")
    }

    @Test
    fun testEmpty() {
        val pattern = LocationPattern("")

        assertEquals("/", pattern.format(Parameters.Empty))
        assertEquals(Parameters.Empty, pattern.parse("/"))
    }

    @Test
    fun testRoot() {
        val pattern = LocationPattern("/")

        assertEquals("/", pattern.format(Parameters.Empty))
        assertEquals(Parameters.Empty, pattern.parse("/"))

        test("/", pattern)
    }

    @Test
    fun testSingleConstant() {
        val pattern = LocationPattern("/path")

        test("/path", pattern)
    }

    @Test
    fun testSingleConstantWithTrailingSlash() {
        val pattern = LocationPattern("/path/")

        test("/path", pattern)
    }

    @Test
    fun testSingleVariable() {
        val pattern = LocationPattern("/{param}")

        test("/value", pattern).let {
            assertEquals("value", it["param"])
        }
    }

    @Test
    fun testTwoVariables() {
        val pattern = LocationPattern("/{param}/{second}")

        test("/value/another", pattern).let {
            assertEquals("value", it["param"])
            assertEquals("another", it["second"])
        }
    }

    @Test
    fun testEllipsis() {
        val pattern = LocationPattern("/{param}/{second...}")

        test("/value/another", pattern).let {
            assertEquals("value", it["param"])
            assertEquals("another", it["second"])
        }

        test("/value/another/more", pattern).let {
            assertEquals("value", it["param"])
            assertEquals("another", it["second"])
            assertEquals(listOf("another", "more"), it.getAll("second"))
        }
    }

    @Test
    fun testSameParameterTwice() {
        val pattern = LocationPattern("/{param}/{param}")

        test("/value/another", pattern).let {
            assertEquals("value", it["param"])
            assertEquals(listOf("value", "another"), it.getAll("param"))
        }
    }

    @Test
    fun testParameterWithPrefix() {
        val pattern = LocationPattern("/aa-{param}")

        test("/aa-value", pattern).let {
            assertEquals("value", it["param"])
        }
    }

    @Test
    fun testParameterWithSuffix() {
        val pattern = LocationPattern("/{param}-bb")

        test("/value-bb", pattern).let {
            assertEquals("value", it["param"])
        }
    }

    @Test
    fun testParameterWithPrefixAndSuffix() {
        val pattern = LocationPattern("/-{param}-")

        test("/-value-", pattern).let {
            assertEquals("value", it["param"])
        }
    }

    @Test
    fun testEllipsisWithPrefixAndSuffix() {
        val pattern = LocationPattern("/aa-{param...}-bb")

        test("/aa-value/more/last-bb", pattern).let {
            assertEquals(listOf("value", "more", "last"), it.getAll("param"))
        }
    }

    @Test
    fun testEllipsisWithNoValue() {
        val pattern = LocationPattern("/{param...}")

        test("/", pattern).let {
            assertEquals(listOf(""), it.getAll("param")) // TODO is that right?
        }
    }

    @Test
    fun testConstantInTheEnd() {
        val pattern = LocationPattern("/{param}/constant")

        test("/value/constant", pattern).let {
            assertEquals("value", it["param"])
        }
    }

    @Test
    fun testConstantInTheMiddle() {
        val pattern = LocationPattern("/{param}/constant/{second}")

        test("/value/constant/other", pattern).let {
            assertEquals("value", it["param"])
            assertEquals("other", it["second"])
        }
    }

    private fun test(actualPath: String, pattern: LocationPattern): Parameters {
        val parsed = pattern.parse(actualPath)
        val formatted = pattern.format(parsed)

        assertEquals(actualPath, formatted)
        assertEquals(pattern.pathParameterNames, parsed.names())

        return parsed
    }
}
