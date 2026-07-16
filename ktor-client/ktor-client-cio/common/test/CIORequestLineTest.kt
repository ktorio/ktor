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

internal class CIORequestLineTest {
    @Test
    fun `use encoded path in origin-form request target`() = runTest {
        assertEquals(
            "GET /library/annual%E2%80%93review/cover-image.webp?size=large&download= HTTP/1.1",
            requestLine("https://assets.example.org/library/annual–review/cover-image.webp?size=large&download=")
        )
    }

    @Test
    fun `use encoded path in absolute-form request target`() = runTest {
        assertEquals(
            "GET https://assets.example.org/library/annual%E2%80%93review/cover-image.webp HTTP/1.1",
            requestLine("https://assets.example.org/library/annual–review/cover-image.webp", overProxy = true)
        )
    }

    @OptIn(InternalAPI::class)
    private suspend fun requestLine(url: String, overProxy: Boolean = false): String {
        val output = ByteChannel(autoFlush = true)
        val request = HttpRequestData(
            url = Url(url),
            method = HttpMethod.Get,
            headers = Headers.Empty,
            body = EmptyContent,
            executionContext = Job(),
            attributes = Attributes()
        )

        writeHeaders(request, output, overProxy)
        return output.readLineStrict()!!
    }
}
