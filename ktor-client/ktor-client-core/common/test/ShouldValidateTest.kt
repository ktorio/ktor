/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cache.tests

import io.ktor.client.plugins.cache.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.test.*

class ShouldValidateTest {

    @Test
    fun testNoCacheInRequestReturnsShouldValidate() {
        val result = shouldValidate(
            GMTDate(),
            Headers.Empty,
            HttpRequestBuilder().apply { headers.append(HttpHeaders.CacheControl, "no-cache") }
        )
        assertEquals(ValidateStatus.ShouldValidate, result)
    }

    @Test
    fun testNoCacheInResponseReturnsShouldValidate() {
        val result = shouldValidate(
            GMTDate(),
            headersOf(HttpHeaders.CacheControl, "no-cache"),
            HttpRequestBuilder()
        )
        assertEquals(ValidateStatus.ShouldValidate, result)
    }

    @Test
    fun testMaxAge0InRequestReturnsShouldValidate() {
        val result = shouldValidate(
            GMTDate(),
            headersOf(HttpHeaders.CacheControl, "max-age=0"),
            HttpRequestBuilder()
        )
        assertEquals(ValidateStatus.ShouldValidate, result)
    }

    @Test
    fun testExpiresInPastAndMustRevalidateReturnsShouldValidate() {
        val result = shouldValidate(
            GMTDate(getTimeMillis() - 1),
            headersOf(HttpHeaders.CacheControl, "must-revalidate"),
            HttpRequestBuilder()
        )
        assertEquals(ValidateStatus.ShouldValidate, result)
    }

    @Test
    fun testExpiresInPastAndMaxStaleInFutureReturnsShouldWarn() {
        val result = shouldValidate(
            GMTDate(getTimeMillis() - 1),
            Headers.Empty,
            HttpRequestBuilder().apply { headers.append(HttpHeaders.CacheControl, "max-stale=10") }
        )
        assertEquals(ValidateStatus.ShouldWarn, result)
    }

    @Test
    fun testExpiresInPastReturnsShouldValidate() {
        val result = shouldValidate(
            GMTDate(getTimeMillis() - 1),
            Headers.Empty,
            HttpRequestBuilder()
        )
        assertEquals(ValidateStatus.ShouldValidate, result)
    }
}
