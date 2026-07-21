/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cache.storage.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CacheHeaderFilteringTest {

    @Test
    fun filterForCacheStorageStripsHopByHopAndProxyAuthButKeepsContentLength() {
        val headers = headersOf(
            HttpHeaders.ETag to listOf("\"v1\""),
            HttpHeaders.ContentLength to listOf("4"),
            HttpHeaders.Connection to listOf("close, Foo"),
            HttpHeaders.TransferEncoding to listOf("chunked"),
            "Keep-Alive" to listOf("timeout=5"),
            "Proxy-Connection" to listOf("keep-alive"),
            HttpHeaders.TE to listOf("trailers"),
            HttpHeaders.Upgrade to listOf("websocket"),
            HttpHeaders.ContentRange to listOf("bytes 0-0/100"),
            HttpHeaders.ProxyAuthenticate to listOf("Basic"),
            "Foo" to listOf("bar"),
            "X-Custom" to listOf("value"),
        )

        val filtered = headers.filterForCacheStorage()

        assertEquals("\"v1\"", filtered[HttpHeaders.ETag])
        assertEquals("4", filtered[HttpHeaders.ContentLength])
        assertEquals("value", filtered["X-Custom"])
        assertNull(filtered[HttpHeaders.Connection])
        assertNull(filtered[HttpHeaders.TransferEncoding])
        assertNull(filtered["Keep-Alive"])
        assertNull(filtered["Proxy-Connection"])
        assertNull(filtered[HttpHeaders.TE])
        assertNull(filtered[HttpHeaders.Upgrade])
        assertNull(filtered[HttpHeaders.ContentRange])
        assertNull(filtered[HttpHeaders.ProxyAuthenticate])
        assertNull(filtered["Foo"])
    }

    @Test
    fun mergeExcludesContentLengthFrom304ButPreservesStoredContentLength() {
        val stored = headersOf(
            HttpHeaders.ETag to listOf("\"v1\""),
            HttpHeaders.ContentLength to listOf("4"),
            HttpHeaders.CacheControl to listOf("max-age=60"),
        )
        val notModified = headersOf(
            HttpHeaders.ETag to listOf("\"v1\""),
            HttpHeaders.CacheControl to listOf("max-age=120"),
            HttpHeaders.ContentLength to listOf("0"),
            HttpHeaders.Connection to listOf("close"),
        )

        val merged = stored.merge(notModified)

        assertEquals("max-age=120", merged[HttpHeaders.CacheControl])
        assertEquals("4", merged[HttpHeaders.ContentLength])
        assertNull(merged[HttpHeaders.Connection])
    }

    @Test
    fun mergeExcludesConnectionListedFieldNames() {
        val stored = headersOf("X-Custom" to listOf("original"))
        val notModified = headersOf(
            HttpHeaders.Connection to listOf("Foo"),
            "Foo" to listOf("bar"),
            "X-Custom" to listOf("updated"),
        )

        val merged = stored.merge(notModified)

        assertEquals("updated", merged["X-Custom"])
        assertNull(merged[HttpHeaders.Connection])
        assertNull(merged["Foo"])
    }
}
