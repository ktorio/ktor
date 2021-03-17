/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.plugins.*
import io.ktor.client.plugins.DefaultRequest.Plugin.install
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.*

class DefaultRequestTest {
    private fun HttpRequestBuilder.defaultRequest(block: DefaultRequest.Builder.() -> Unit) = apply {
        install(block)
    }

    @Test
    fun testUsingBaseURLMatchingCurrentPipelineBehaviour() {
        val todoURL = HttpRequestBuilder().apply {
            url.takeFrom(URLBuilder("/sub").build())
        }.defaultRequest {
            baseURL("https://ktor.io/docs")
        }.build().url
        assertEquals("https://ktor.io/docs/sub", todoURL.toString())
    }

    @Test
    fun overrideOther() {
        val overridden = HttpRequestBuilder().apply {
            url.takeFrom("https://kotlinlang.org/blog")
        }.defaultRequest {
            baseURL("https://ktor.io/docs")
        }.build().url
        assertEquals("https://kotlinlang.org/blog", overridden.toString())
    }

    @Test
    fun overriddenLocalhostOnly() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/")
        }.defaultRequest {
            baseURL("https://ktor.io/docs")
        }.build().url
        assertEquals("http://localhost/", localhost.toString())
    }

    @Test
    fun overriddenLocalhostWithPath() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/sub")
        }.defaultRequest {
            baseURL("https://ktor.io/docs")
        }.build().url
        assertEquals("http://localhost/sub", localhost.toString())
    }

    @Test
    fun useDefaultAsBaseURL() {
        val localhost = HttpRequestBuilder().apply {
            url.takeFrom("http://localhost/sub")
        }.defaultRequest {
            baseURL(URLBuilder())
        }.build().url
        assertEquals("http://localhost/sub", localhost.toString())
    }

    @Test
    fun malformedBaseURL() {
        assertFailsWith<IllegalArgumentException> {
            HttpRequestBuilder().apply {
                url.takeFrom("http://localhost/sub")
            }.defaultRequest {
                baseURL("https://ktor.io?a")
            }
        }
    }
}
