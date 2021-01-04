/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class ParametersBuilderTest {

    private val original = "abc=def&efg=hij;klm=nop/qrs=tu v"
    private val encodedKey = original.encodeURLParameter(spaceToPlus = true)
    private val encodedValue = original.encodeURLParameterValue()

    private fun assertEncodedQuery(option: UrlEncodingOption?, expectedKey: String, expectedValue: String) {
        // given that a builder is constructed with the UrlEncodingOption
        val builder =
            if (option == null) ParametersBuilder()
            else ParametersBuilder(urlEncodingOption = option)

        // when the original string is added to the parameters as a key and value
        builder.append(original, original)
        val encoded = builder.build().formUrlEncode()

        // then encoded string should be the expected key=value
        assertEquals("$expectedKey=$expectedValue", encoded)
    }

    @Test
    fun defaultConstructorTest() {
        assertEncodedQuery(null, encodedKey, encodedValue)
    }

    @Test
    fun defaultEncodingTest() {
        assertEncodedQuery(UrlEncodingOption.DEFAULT, encodedKey, encodedValue)
    }

    @Test
    fun keyOnlyEncodingTest() {
        assertEncodedQuery(UrlEncodingOption.KEY_ONLY, encodedKey, original)
    }

    @Test
    fun valueOnlyEncodingTest() {
        assertEncodedQuery(UrlEncodingOption.VALUE_ONLY, original, encodedValue)
    }

    @Test
    fun noEncodingTest() {
        assertEncodedQuery(UrlEncodingOption.NO_ENCODING, original, original)
    }
}
