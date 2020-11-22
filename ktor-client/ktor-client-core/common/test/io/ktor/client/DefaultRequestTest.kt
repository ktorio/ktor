/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*

class DefaultRequestTest {
    @Test
    fun parameters() {
        assertEquals(ParametersBuilder(), ParametersBuilder())
        assertEquals(URLBuilder(), URLBuilder())
    }

    private fun HttpRequestBuilder.defaultRequest() = apply {
        baseURL("https://ktor.io/docs")
    }

    @Test
    fun testUsingBaseURL() {
        val todoURL = HttpRequestBuilder().apply {
            url.takeFrom("/sub")
        }.defaultRequest().build().url
        assertEquals("https://ktor.io/docs/sub", todoURL.toString())
    }

    @Test
    fun overrideOther() {
        val overridden = HttpRequestBuilder().apply {
            url.takeFrom("https://kotlinlang.org/blog")
        }.defaultRequest().build().url
        assertEquals("https://kotlinlang.org/blog", overridden.toString())
    }

    @Test
    fun overriddenLocalhostOnly() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/")
        }.defaultRequest().build().url
        assertEquals("http://localhost/", localhost.toString())
    }

    @Test
    @Ignore // wait for KTOR-
    fun overriddenLocalhostWithPath() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/sub")
        }.defaultRequest().build().url
        assertEquals("http://localhost/sub", localhost.toString())
    }

    @Test
    fun useDefaultAsBaseURL() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/sub")
            baseURL(URLBuilder())
        }.build().url
        assertEquals("http://localhost/sub", localhost.toString())
    }
}
