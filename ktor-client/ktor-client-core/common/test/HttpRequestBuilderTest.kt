/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlin.test.*

class HttpRequestBuilderTest {

    val testKey1 = AttributeKey<String>("key_1")
    val testKey2 = AttributeKey<Int>("key_2")

    @Test
    fun testTakeFromBuilderKeepsAttributes() {
        val request = HttpRequestBuilder().apply {
            attributes.put(testKey1, "value")
            attributes.put(testKey2, 123)
        }
        val newRequest = HttpRequestBuilder().takeFrom(request)
        assertEquals("value", newRequest.attributes[testKey1])
        assertEquals(123, newRequest.attributes[testKey2])
    }

    @Test
    fun testTakeFromRequestKeepsAttributes() {
        val request = object : HttpRequest {
            override val call: HttpClientCall
                get() = throw NotImplementedError()
            override val method: HttpMethod = HttpMethod.Get
            override val url: Url = URLBuilder("localhost").build()
            override val attributes: Attributes = Attributes().apply {
                put(testKey1, "value")
                put(testKey2, 123)
            }
            override val content: OutgoingContent = EmptyContent
            override val headers: Headers = Headers.Empty
        }
        val newRequest = HttpRequestBuilder().takeFrom(request)
        assertEquals("value", newRequest.attributes[testKey1])
        assertEquals(123, newRequest.attributes[testKey2])
    }

    @Test
    fun testTakeFromDataKeepsAttributes() {
        val request = HttpRequestBuilder().apply {
            attributes.put(testKey1, "value")
            attributes.put(testKey2, 123)
        }.build()
        val newRequest = HttpRequestBuilder().takeFrom(request)
        assertEquals("value", newRequest.attributes[testKey1])
        assertEquals(123, newRequest.attributes[testKey2])
    }
}
