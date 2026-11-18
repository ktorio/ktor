/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.test.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

internal class CIOProxyAuthorizationTest {
    @Test
    fun `do not send Proxy-Authorization through the HTTPS tunnel`() = runTest {
        val headers = writtenHeaders("https://example.org/", overProxy = true)
        assertTrue(headers.none { it.startsWith("${HttpHeaders.ProxyAuthorization}:", ignoreCase = true) })
        assertContains(headers, "Accept: text/plain")
    }

    @Test
    fun `send Proxy-Authorization to an HTTP proxy`() = runTest {
        val headers = writtenHeaders("http://example.org/", overProxy = true)
        assertContains(headers, "${HttpHeaders.ProxyAuthorization}: Basic secret")
    }

    @OptIn(InternalAPI::class)
    private suspend fun writtenHeaders(url: String, overProxy: Boolean): List<String> {
        val output = ByteChannel(autoFlush = true)
        val request = HttpRequestData(
            url = Url(url),
            method = HttpMethod.Get,
            headers = headersOf(
                HttpHeaders.ProxyAuthorization to listOf("Basic secret"),
                HttpHeaders.Accept to listOf("text/plain"),
            ),
            body = EmptyContent,
            executionContext = Job(),
            attributes = Attributes()
        )

        writeHeaders(request, output, overProxy)
        val lines = mutableListOf<String>()
        while (true) {
            val line = output.readLineStrict()
            if (line.isNullOrEmpty()) return lines
            lines += line
        }
    }
}
