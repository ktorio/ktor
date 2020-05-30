/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import kotlin.test.*

class JsonFeatureTest {
    @Test
    fun testDefaultContentTypes() {
        val config = JsonFeature.Config()

        assertEquals(1, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Json) }
    }

    @Test
    fun testAcceptCall() {
        val config = JsonFeature.Config()
        config.accept(ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Json) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
    }

    @Test
    fun testContentTypesListAssignment() {
        val config = JsonFeature.Config()
        config.acceptContentTypes = listOf(ContentType.Application.Pdf, ContentType.Application.Xml)

        assertEquals(2, config.acceptContentTypes.size)
        assertFalse { config.acceptContentTypes.contains(ContentType.Application.Json) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Xml) }
        assertTrue { config.acceptContentTypes.contains(ContentType.Application.Pdf) }
    }

    @Test
    fun testAddAcceptHeaderAlways() = testWithEngine(MockEngine) {
        acceptConfig(AddAcceptHeader.Always)

        test { client ->
            assertEquals("application/json", client.get())
            assertEquals("application/json", client.get {
                accept(ContentType.Application.Json)
            })
            assertEquals("application/xml,application/json", client.get {
                accept(ContentType.Application.Xml)
            })
        }
    }

    @Test
    fun testAddAcceptHeaderIfMissing() = testWithEngine(MockEngine) {
        acceptConfig(AddAcceptHeader.IfAcceptHeaderMissing)

        test { client ->
            assertEquals("application/json", client.get())
            assertEquals("application/json", client.get {
                accept(ContentType.Application.Json)
            })
            assertEquals("application/xml", client.get {
                accept(ContentType.Application.Xml)
            })
        }
    }

    @Test
    fun testAddAcceptHeaderNever() = testWithEngine(MockEngine) {
        acceptConfig(AddAcceptHeader.Never)

        test { client ->
            assertEquals("*/*", client.get())
            assertEquals("application/json", client.get {
                accept(ContentType.Application.Json)
            })
            assertEquals("application/xml", client.get {
                accept(ContentType.Application.Xml)
            })
        }
    }

    fun TestClientBuilder<MockEngineConfig>.acceptConfig(value: AddAcceptHeader) {
        config {
            engine {
                addHandler { request ->
                    respondOk(
                        request.headers.getAll(HttpHeaders.Accept)?.joinToString(",") ?: ""
                    )
                }
            }
            install(JsonFeature) {
                addAcceptHeader = value
            }
        }
    }
}
