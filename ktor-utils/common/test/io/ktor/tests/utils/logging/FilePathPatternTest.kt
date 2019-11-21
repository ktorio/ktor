/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import kotlin.test.*

class FilePathPatternTest {
    @Test
    fun smokeTest() {
        val pattern = FilePathPattern("test-%d{yyyyMMdd}-%i")
        assertEquals(
            listOf(
                FilePathPattern.Component.ConstantPart("test-"),
                FilePathPattern.Component.Date(StringPatternDateFormat("yyyyMMdd")),
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Number
            ), pattern.parts
        )

        val date = GMTDate(0, 0, 0, 1, Month.FEBRUARY, 2019)
        val formatted = pattern.format(date, 77)
        assertEquals("test-20190201-77", formatted)
    }

    @Test
    fun dateWithoutParameters() {
        val pattern = FilePathPattern("%d-%i")
        assertEquals(
            listOf(
                FilePathPattern.Component.Date(StringPatternDateFormat("yyyy-MM-dd")),
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Number
            ), pattern.parts
        )
    }

    @Test
    fun dateWithoutParameters2() {
        val pattern = FilePathPattern("%i-%d")
        assertEquals(
            listOf(
                FilePathPattern.Component.Number,
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Date(StringPatternDateFormat("yyyy-MM-dd"))
            ), pattern.parts
        )
    }

    @Test
    fun dateWithParameters() {
        val pattern = FilePathPattern("%i-%d{yyyy}")
        assertEquals(
            listOf(
                FilePathPattern.Component.Number,
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Date(StringPatternDateFormat("yyyy"))
            ), pattern.parts
        )
    }

    @Test
    fun dateMultipleDates() {
        val pattern = FilePathPattern("%d{yyyy}-%i-%d{MM-dd}")
        assertEquals(
            listOf(
                FilePathPattern.Component.Date(StringPatternDateFormat("yyyy")),
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Number,
                FilePathPattern.Component.ConstantPart("-"),
                FilePathPattern.Component.Date(StringPatternDateFormat("MM-dd"))
            ), pattern.parts
        )

        val date = GMTDate(0, 0, 0, 1, Month.FEBRUARY, 2019)
        val formatted = pattern.format(date, 77)
        assertEquals("2019-77-02-01", formatted)
    }

    @Test
    fun dateMultipleNumbers() {
        val pattern = FilePathPattern("%i%i")
        assertEquals(
            listOf(
                FilePathPattern.Component.Number,
                FilePathPattern.Component.Number
            ), pattern.parts
        )

        val date = GMTDate(0, 0, 0, 1, Month.FEBRUARY, 2019)
        val formatted = pattern.format(date, 77)
        assertEquals("7777", formatted)
    }

    @Test
    fun testSeparators() {
        val pattern = FilePathPattern("dir/pattern-%i/file-%i")
        assertEquals(
            listOf(
                FilePathPattern.Component.ConstantPart("dir"),
                FilePathPattern.Component.Separator,
                FilePathPattern.Component.ConstantPart("pattern-"),
                FilePathPattern.Component.Number,
                FilePathPattern.Component.Separator,
                FilePathPattern.Component.ConstantPart("file-"),
                FilePathPattern.Component.Number
            ), pattern.parts
        )

        val date = GMTDate(0, 0, 0, 1, Month.FEBRUARY, 2019)
        val formatted = pattern.format(date, 77)
        assertEquals("dir/pattern-77/file-77", formatted)
    }

    @Test
    fun testRedundantSeparators() {
        val pattern = FilePathPattern("/////a-%i")
        assertEquals(
            listOf(
                FilePathPattern.Component.Separator,
                FilePathPattern.Component.ConstantPart("a-"),
                FilePathPattern.Component.Number
            ), pattern.parts
        )

        val date = GMTDate(0, 0, 0, 1, Month.FEBRUARY, 2019)
        val formatted = pattern.format(date, 77)
        assertEquals("/a-77", formatted)
    }
}
