/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ParametersBuilderTest {

    private val original = "abc=def&efg=hij;klm=nop/qrs=tuv"
    private val encoded = original.encodeURLParameter(false)

    private fun assertEncodedQuery(option: UrlEncodingOption?, expectedKey: String, expectedValue: String) {
        val builder = option?.let { ParametersBuilder(urlEncodingOption = it) } ?: ParametersBuilder()
        builder.append(original, original)
        val encoded = builder.build().formUrlEncode()

        assertEquals("$expectedKey=$expectedValue", encoded)
    }

    @Test
    fun encodeKeyValueDefaultConstructorTest() {
        assertEncodedQuery(null, encoded, encoded)
    }

    @Test
    fun encodeKeyValueTest() {
        assertEncodedQuery(UrlEncodingOption.DEFAULT, encoded, encoded)
    }

    @Test
    fun encodeKeyOnlyTest() {
        assertEncodedQuery(UrlEncodingOption.KEY_ONLY, encoded, original)
    }

    @Test
    fun encodeValueOnlyTest() {
        assertEncodedQuery(UrlEncodingOption.VALUE_ONLY, original, encoded)
    }

    @Test
    fun noEncodingTest() {
        assertEncodedQuery(UrlEncodingOption.NO_ENCODING, original, original)
    }
}
