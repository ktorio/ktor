/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.test.*

class StringValuesBuilderTest {

    @Test
    fun testBuiltValuesDoesNotMutate() {
        val builder = StringValuesBuilderImpl()
        builder.append("key", "value1")
        val params = builder.build()

        builder.append("key", "value2")

        assertEquals(listOf("value1"), params.getAll("key"))
    }

    @Test
    fun testCanCallBuildMultipleTimes() {
        val builder = StringValuesBuilderImpl()

        builder.append("key", "value1")
        val params1 = builder.build()

        builder.append("key", "value2")
        val params2 = builder.build()

        assertEquals(listOf("value1"), params1.getAll("key"))
        assertEquals(listOf("value1", "value2"), params2.getAll("key"))
    }

    @Test
    fun testBuiltValuesDoesNotMutateCaseInsensitive() {
        val builder = StringValuesBuilderImpl(caseInsensitiveName = true)
        builder.append("key", "value1")
        val params = builder.build()

        builder.append("key", "value2")

        assertEquals(listOf("value1"), params.getAll("key"))
    }

    @Test
    fun testCanCallBuildMultipleTimesCaseInsensitive() {
        val builder = StringValuesBuilderImpl(caseInsensitiveName = true)

        builder.append("key", "value1")
        val params1 = builder.build()

        builder.append("key", "value2")
        val params2 = builder.build()

        assertEquals(listOf("value1"), params1.getAll("key"))
        assertEquals(listOf("value1", "value2"), params2.getAll("key"))
    }

    @Test
    fun testAppendIfAbsent() {
        val builder = StringValuesBuilderImpl(caseInsensitiveName = true)

        builder.append("key", "value1")

        builder.appendIfNameAbsent("key", "value2")
        assertEquals(listOf("value1"), builder.getAll("key"))

        builder.appendIfNameAndValueAbsent("key", "value1")
        builder.appendIfNameAndValueAbsent("key", "value3")
        assertEquals(listOf("value1", "value3"), builder.getAll("key"))
    }

    @Test
    fun replaceAll() {
        val original = StringValuesBuilderImpl(caseInsensitiveName = true).also {
            it.append("Key1", "value1")
            it.append("Key2", "value2")
            it.append("Key3", "value3")
        }

        val replace = StringValuesBuilderImpl(caseInsensitiveName = true).also {
            it.append("Key1", "replace1")
            it.append("Key2", "replace2")
        }

        original.replaceAll(replace)
        assertEquals(original["Key1"], "replace1")
        assertEquals(original["Key2"], "replace2")
        assertEquals(original["Key3"], "value3")
    }
}
