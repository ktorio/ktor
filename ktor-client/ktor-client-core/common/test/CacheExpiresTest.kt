/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
package io.ktor.client.features.cache.tests

import io.ktor.client.call.*
import io.ktor.client.features.cache.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*
import kotlin.test.*

class CacheExpiresTest {
    @Test
    fun testValidExpirationDate() {
        val dateText = "Tue, 27 Oct 2020 15:21:07 GMT"
        val parsed = dateText.fromHttpToGmtDate()

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires()
        assertEquals(parsed, result)
    }

    @Test
    fun testInvalidExpirationDate() {
        val dateText = "A1231242323532452345"
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateZero() {
        val dateText = "0"
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateEmpty() {
        val dateText = ""
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires { expected }
        assertEquals(expected, result)
    }

    @Test
    fun testInvalidExpirationDateBlank() {
        val dateText = " "
        val expected = GMTDate.START

        val response = response {
            append(HttpHeaders.Expires, dateText)
        }

        val result = response.cacheExpires { expected }
        assertEquals(expected, result)
    }

    private fun response(builder: HeadersBuilder.() -> Unit): HttpResponse {
        return Response(buildHeaders(builder))
    }

    private class Response(override val headers: Headers) : HttpResponse() {
        override val call: HttpClientCall get() = error("Shouldn't be used")
        override val status: HttpStatusCode
            get() = error("Shouldn't be used")
        override val version: HttpProtocolVersion
            get() = error("Shouldn't be used")
        override val requestTime: GMTDate
            get() = error("Shouldn't be used")
        override val responseTime: GMTDate
            get() = error("Shouldn't be used")
        override val content: ByteReadChannel
            get() = error("Shouldn't be used")
        override val coroutineContext: CoroutineContext
            get() = error("Shouldn't be used")
    }
}
