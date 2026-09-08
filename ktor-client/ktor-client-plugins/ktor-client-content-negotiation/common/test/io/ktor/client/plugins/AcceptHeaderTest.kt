/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AcceptHeaderTest {
    // An explicit JSON exclusion in a combined Accept header must not be overridden by an implicit q=1.
    @Test
    fun honorCombinedHeaderContent() = runTest {
        val combinedAcceptHeader = "application/json;q=0, text/plain;q=0.5"
        val mockEngine = MockEngine { respondOk() }
        HttpClient(mockEngine) {
            this.install(ContentNegotiation) { json() }
        }.use { client ->
            client.get("https://example.com") {
                header(HttpHeaders.Accept, combinedAcceptHeader)
            }
        }
        val receivedAcceptHeader = mockEngine.requestHistory.single().headers.getAll(HttpHeaders.Accept)
        assertNotNull(receivedAcceptHeader)
        val jsonQualities = parseHeaderValue(receivedAcceptHeader.joinToString(", "))
            .filter { it.value == "application/json" }
            .map { it.quality }
        assertEquals(listOf(0.0), jsonQualities)
    }

    // Dropping the parsed profile parameter may add a duplicate JSON entry with implicit q=1 despite the explicit q=0.
    @Test
    fun honorHeaderContentWithParameters() = runTest {
        val acceptHeaderWithParameters = "application/json;profile=\"a,b\";q=0"
        val mockEngine = MockEngine { respondOk() }
        HttpClient(mockEngine) {
            this.install(ContentNegotiation) {
                json(contentType = ContentType.Application.Json.withParameter("profile", "a,b"))
            }
        }.use { client ->
            client.get("https://example.com") {
                header(HttpHeaders.Accept, acceptHeaderWithParameters)
            }
        }
        val receivedAcceptHeader = mockEngine.requestHistory.single().headers.getAll(HttpHeaders.Accept)
        assertNotNull(receivedAcceptHeader)
        val jsonQualities = parseHeaderValue(receivedAcceptHeader.joinToString(", "))
            .filter { it.value == "application/json" }
            .map { it.quality }
        assertEquals(listOf(0.0), jsonQualities)
    }
}
